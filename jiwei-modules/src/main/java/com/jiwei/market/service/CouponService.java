package com.jiwei.market.service;

import com.jiwei.common.result.PageResult;
import com.jiwei.pojo.entity.CouponTemplate;

import java.util.List;

/**
 * 优惠券服务接口
 */
public interface CouponService {

    /**
     * 创建优惠券模板
     */
    void createTemplate(CouponTemplate template);

    /**
     * 分页查询模板
     */
    PageResult pageTemplate(int page, int pageSize);

    /**
     * 批量发放优惠券给用户
     */
    void grant(Long templateId, List<Long> userIds);

    /**
     * 领取优惠券
     */
    void receive(Long templateId);

    /**
     * 我的优惠券列表
     */
    PageResult list(Integer page, Integer pageSize);

    /**
     * C端可领取优惠券模板列表（上架中且未领满）
     */
    PageResult availableTemplates(int page, int pageSize);

    /**
     * 释放优惠券（订单取消/拒单/超时后恢复为未使用）
     */
    void release(Long userCouponId);

    /**
     * 启用/禁用优惠券模板
     */
    void updateTemplateStatus(Long id, Integer status);
}
