package com.jiwei.social.like;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 热点笔记检测器：按窗口统计每篇笔记的点赞操作速率，
 * 超过阈值即进入本地聚合模式，连续冷却则退出。
 */
@Component
@Slf4j
public class HotKeyDetector {

    private final LocalHotLikeBuffer buffer;
    private final int thresholdPerWindow;
    private final int cooldownCycles;

    private final ConcurrentHashMap<Long, AtomicLong> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> coldCycles = new ConcurrentHashMap<>();

    public HotKeyDetector(LocalHotLikeBuffer buffer,
                          @Value("${like.hot.threshold-per-window:30}") int thresholdPerWindow,
                          @Value("${like.hot.cooldown-cycles:5}") int cooldownCycles) {
        this.buffer = buffer;
        this.thresholdPerWindow = thresholdPerWindow;
        this.cooldownCycles = cooldownCycles;
    }

    /** 每一次点赞/取消操作都打点。 */
    public void hit(long noteId) {
        counters.computeIfAbsent(noteId, k -> new AtomicLong()).incrementAndGet();
    }

    /** 每个评估窗口（window-seconds，默认 2s）评估一次各笔记的操作速率。 */
    @Scheduled(fixedDelayString = "${like.hot.window-seconds:2}000",
               initialDelayString = "${like.hot.window-seconds:2}000")
    public void evaluate() {
        for (Map.Entry<Long, AtomicLong> entry : counters.entrySet()) {
            Long noteId = entry.getKey();
            int ops = (int) entry.getValue().getAndSet(0);
            if (ops >= thresholdPerWindow) {
                if (!buffer.isHot(noteId)) {
                    buffer.markHot(noteId);
                    log.info("热点判定: note={} 窗口操作数={} >= 阈值={} -> 切换本地聚合写入",
                            noteId, ops, thresholdPerWindow);
                }
                coldCycles.remove(noteId);
            } else if (buffer.isHot(noteId)) {
                int cycles = coldCycles.merge(noteId, 1, Integer::sum);
                if (cycles >= cooldownCycles) {
                    buffer.markCold(noteId);
                    coldCycles.remove(noteId);
                    log.info("热点冷却: note={} 连续 {} 个周期低于阈值，退出热点模式", noteId, cycles);
                }
            }
        }
    }
}
