package com.jiwei.user.service;

/**
 * 成长值服务：统一的成长值累加入口（签到 / 下单 / 发布笔记等行为回调）。
 */
public interface GrowthService {

    /**
     * 为用户累加成长值，并根据最新成长值重新计算等级（等级只升不降）。
     *
     * @param userId 用户ID
     * @param delta  本次行为获得的成长值（必须为正数）
     */
    void addGrowth(Long userId, int delta);
}
