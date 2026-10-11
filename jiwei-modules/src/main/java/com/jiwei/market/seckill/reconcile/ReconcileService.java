package com.jiwei.market.seckill.reconcile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiwei.market.mapper.SeckillActivityMapper;
import com.jiwei.market.seckill.cache.RedisExecutor;
import com.jiwei.market.seckill.config.SeckillProperties;
import com.jiwei.market.service.SeckillService;
import com.jiwei.pojo.entity.SeckillActivity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 秒杀对账：Redis 预扣库存 vs DB 库存，漂移以 DB 为唯一账本收敛；滞留订单兜底关单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconcileService {

    private final SeckillActivityMapper activityMapper;
    private final RedisExecutor redisExecutor;
    private final SeckillService seckillService;
    private final SeckillProperties properties;

    private final AtomicReference<Map<String, Object>> latestReport = new AtomicReference<>(Map.of());

    @Scheduled(fixedDelayString = "${seckill.reconcile.interval-ms:60000}", initialDelay = 15000)
    public void runPeriodically() {
        if (!properties.getReconcile().isEnabled()) {
            return;
        }
        try {
            Map<String, Object> report = runOnce(properties.getReconcile().isAutoFix());
            latestReport.set(report);
        } catch (Exception e) {
            log.error("对账任务失败", e);
        }
    }

    public Map<String, Object> runOnce(boolean autoFix) {
        List<String> details = new ArrayList<>();
        inventoryReconcile(autoFix, details);   // 第一道：Redis 库存 vs DB
        staleOrderReconcile(details);           // 第二道：滞留订单兜底关单
        Map<String, Object> report = new HashMap<>();
        report.put("autoFix", autoFix);
        report.put("fixedCount", details.size());
        report.put("details", details);
        latestReport.set(report);
        return report;
    }

    public Map<String, Object> latest() {
        return latestReport.get();
    }

    /** 第一道：Redis 预扣库存 vs DB 库存，漂移以 DB 为准收敛 */
    private void inventoryReconcile(boolean autoFix, List<String> details) {
        List<SeckillActivity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<SeckillActivity>().eq(SeckillActivity::getStatus, 1));
        LocalDateTime now = LocalDateTime.now();
        for (SeckillActivity a : activities) {
            if (now.isBefore(a.getStartTime()) || now.isAfter(a.getEndTime())) {
                continue;
            }
            String key = "seckill:stock:" + a.getId() + ":" + a.getDishId();
            String redisStock = redisExecutor.get(key, t -> "BREAKER_OPEN");
            if ("BREAKER_OPEN".equals(redisStock)) {
                continue; // 熔断期跳过
            }
            int dbStock = a.getStock();
            if (redisStock == null) {
                details.add("stock-missing: activity#" + a.getId() + " db=" + dbStock);
                if (autoFix) {
                    redisExecutor.setIfAbsent(key, String.valueOf(dbStock),
                            Duration.between(now, a.getEndTime()));
                }
            } else if (Integer.parseInt(redisStock) != dbStock) {
                details.add("stock-drift: activity#" + a.getId() + " redis=" + redisStock + " db=" + dbStock);
                // 仅当 Redis 多于 DB 时以 DB 收敛（库存异常释放）；Redis 少于 DB 是
                // Lua 预扣后 DB 尚未异步落库的正常窗口，不能覆盖，否则购买后库存回弹
                if (autoFix && Integer.parseInt(redisStock) > dbStock) {
                    redisExecutor.del(key);
                    redisExecutor.setIfAbsent(key, String.valueOf(dbStock),
                            Duration.between(now, a.getEndTime()));
                }
            }
        }
    }

    /** 第二道：滞留待支付秒杀订单兜底关单（延迟消息丢失/消费失败的兜底） */
    private void staleOrderReconcile(List<String> details) {
        // 扫描 is_seckill=1 且 待支付 且 超过支付截止时间的 orders，
        // 复用状态机 CAS 关单 + 回补库存（seckillService 提供，后续按需接入）
        // TODO: 需要跨库扫描 trade.orders，当前由 OrderTask 的延迟取消覆盖，暂不重复实现
    }
}
