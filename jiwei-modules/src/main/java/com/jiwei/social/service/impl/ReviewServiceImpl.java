package com.jiwei.social.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jiwei.common.context.BaseContext;
import com.jiwei.common.result.PageResult;
import com.jiwei.pojo.entity.Review;
import com.jiwei.social.mapper.ReviewMapper;
import com.jiwei.social.service.ReviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 评价服务实现类
 */
@DS("social")
@Service
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    @Autowired
    private ReviewMapper reviewMapper;

    @Override
    public void publish(Review review) {
        //1、设置作者ID和审核状态
        review.setUserId(BaseContext.getCurrentId());
        //开发环境直接过审，否则评价永远待审核不展示；生产环境应接 AI AuditAgent 审核
        review.setAuditStatus(1);

        //2、保存评价
        reviewMapper.insert(review);
        log.info("评价发布成功，reviewId: {}, userId: {}", review.getId(), review.getUserId());

        //3、异步触发AI内容审核
        //TODO: 发送RocketMQ消息 → AI AuditAgent → 审核后回调更新audit_status
    }

    @Override
    public List<Review> listByDishId(Long dishId) {
        //1、查询该菜品的所有已审核通过的评价
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Review::getDishId, dishId)
               .eq(Review::getAuditStatus, 1)  // 仅已审核通过
               .orderByDesc(Review::getCreateTime);

        //2、返回评价列表
        return reviewMapper.selectList(wrapper);
    }

    @Override
    public PageResult pageQuery(int page, int pageSize, Integer auditStatus) {
        //1、构建分页条件
        Page<Review> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(auditStatus != null, Review::getAuditStatus, auditStatus)
               .orderByDesc(Review::getCreateTime);

        //2、执行分页查询
        Page<Review> result = reviewMapper.selectPage(p, wrapper);
        return new PageResult(result.getTotal(), result.getRecords());
    }

    @Override
    public PageResult myPageQuery(int page, int pageSize) {
        //只查当前登录用户的评价
        Page<Review> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Review::getUserId, BaseContext.getCurrentId())
               .orderByDesc(Review::getCreateTime);
        Page<Review> result = reviewMapper.selectPage(p, wrapper);
        return new PageResult(result.getTotal(), result.getRecords());
    }

    @Override
    public void audit(Long id, Integer auditStatus, String auditReason) {
        Review review = reviewMapper.selectById(id);
        if (review == null) {
            throw new com.jiwei.common.exception.BaseException("评价不存在");
        }
        review.setAuditStatus(auditStatus);
        review.setAuditReason(auditReason);
        reviewMapper.updateById(review);
        log.info("评价审核完成: id={}, status={}, reason={}", id, auditStatus, auditReason);
    }
}
