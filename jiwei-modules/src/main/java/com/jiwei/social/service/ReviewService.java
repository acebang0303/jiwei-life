package com.jiwei.social.service;

import com.jiwei.common.result.PageResult;
import com.jiwei.pojo.entity.Review;

import java.util.List;

/**
 * 评价服务接口
 */
public interface ReviewService {

    /**
     * 发表评价
     * 提交后触发AI内容审核(AuditAgent)
     */
    void publish(Review review);

    /**
     * 查询菜品的评价列表(仅已审核通过)
     */
    List<Review> listByDishId(Long dishId);

    /**
     * 分页查询评价列表
     */
    PageResult pageQuery(int page, int pageSize, Integer auditStatus);

    /**
     * 我的评价（按当前用户过滤）
     */
    PageResult myPageQuery(int page, int pageSize);

    /**
     * 审核评价（通过/驳回）
     */
    void audit(Long id, Integer auditStatus, String auditReason);
}
