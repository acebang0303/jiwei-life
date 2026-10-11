package com.jiwei.market.service;

/**
 * 签到服务接口
 * 基于 Redis BitMap 实现
 */
public interface SignService {

    /**
     * 每日签到
     */
    void sign(Long userId);

    /**
     * 查询今日是否已签到
     */
    boolean isSignedToday(Long userId);

    /**
     * 查询本月签到天数
     */
    long getMonthSignCount(Long userId);

    /**
     * 查询连续签到天数
     */
    int getContinuousSignDays(Long userId);
}
