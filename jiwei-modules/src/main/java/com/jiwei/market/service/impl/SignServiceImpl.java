package com.jiwei.market.service.impl;

import com.jiwei.market.service.SignService;
import com.jiwei.user.service.GrowthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 签到服务实现类
 * Key: sign:{userId}:{yearMonth}
 * 使用 Redis BitMap 记录签到情况
 */
@Service
@Slf4j
public class SignServiceImpl implements SignService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private GrowthService growthService;

    /** 每次签到获得的成长值 */
    private static final int SIGN_GROWTH = 5;

    @Override
    public void sign(Long userId) {
        //1、获取当前日期
        LocalDate today = LocalDate.now();
        int dayOfMonth = today.getDayOfMonth();
        String key = buildSignKey(userId, today);

        //2、每日签到去重：已签到则拒绝（防止前端绕过按钮无限签到）
        if (Boolean.TRUE.equals(redisTemplate.opsForValue().getBit(key, dayOfMonth - 1))) {
            throw new com.jiwei.common.exception.BaseException("今日已签到，明天再来吧");
        }

        //3、签到（SETBIT）
        redisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        log.info("用户签到成功，userId: {}, date: {}", userId, today);

        //4、发放成长值
        growthService.addGrowth(userId, SIGN_GROWTH);
    }

    @Override
    public boolean isSignedToday(Long userId) {
        LocalDate today = LocalDate.now();
        String key = buildSignKey(userId, today);
        Boolean signed = redisTemplate.opsForValue().getBit(key, today.getDayOfMonth() - 1);
        return Boolean.TRUE.equals(signed);
    }

    @Override
    public long getMonthSignCount(Long userId) {
        LocalDate today = LocalDate.now();
        String key = buildSignKey(userId, today);

        //BITCOUNT 统计本月签到天数
        Long count = redisTemplate.execute(
                connection -> connection.bitCount(key.getBytes()),
                true
        );
        return count != null ? count : 0;
    }

    @Override
    public int getContinuousSignDays(Long userId) {
        LocalDate today = LocalDate.now();
        String key = buildSignKey(userId, today);
        int dayOfMonth = today.getDayOfMonth();
        int continuousDays = 0;

        //1、反向遍历bit位，统计连续1的个数
        for (int i = dayOfMonth - 1; i >= 0; i--) {
            Boolean signed = redisTemplate.opsForValue().getBit(key, i);
            if (Boolean.TRUE.equals(signed)) {
                continuousDays++;
            } else {
                break;
            }
        }

        return continuousDays;
    }

    /**
     * 构建签到Key
     */
    private String buildSignKey(Long userId, LocalDate date) {
        String yearMonth = String.format("%d%02d", date.getYear(), date.getMonthValue());
        return "sign:" + userId + ":" + yearMonth;
    }
}
