package com.jiwei.market.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jiwei.common.context.BaseContext;
import com.jiwei.common.result.PageResult;
import com.jiwei.pojo.entity.CouponTemplate;
import com.jiwei.pojo.entity.UserCoupon;
import com.jiwei.market.mapper.CouponTemplateMapper;
import com.jiwei.market.mapper.UserCouponMapper;
import com.jiwei.market.service.CouponService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 优惠券服务实现类
 */
@DS("market")
@Service
@Slf4j
public class CouponServiceImpl implements CouponService {

    @Autowired
    private CouponTemplateMapper couponTemplateMapper;

    @Autowired
    private UserCouponMapper userCouponMapper;

    @Override
    @Transactional
    public void createTemplate(CouponTemplate template) {
        //1、保存优惠券模板
        couponTemplateMapper.insert(template);
        log.info("创建优惠券模板成功，templateId: {}", template.getId());
    }

    @Override
    public PageResult pageTemplate(int page, int pageSize) {
        //1、构建分页条件
        Page<CouponTemplate> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<CouponTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(CouponTemplate::getCreateTime);

        //2、执行分页查询
        Page<CouponTemplate> result = couponTemplateMapper.selectPage(p, wrapper);
        return new PageResult(result.getTotal(), result.getRecords());
    }

    @Override
    public PageResult availableTemplates(int page, int pageSize) {
        Long userId = com.jiwei.common.context.BaseContext.getCurrentId();
        Page<CouponTemplate> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<CouponTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CouponTemplate::getStatus, 1)
               .orderByAsc(CouponTemplate::getId);
        Page<CouponTemplate> result = couponTemplateMapper.selectPage(p, wrapper);

        //统计当前用户已领取数，标记可领/已领满
        for (CouponTemplate t : result.getRecords()) {
            long received = userCouponMapper.selectCount(
                    new LambdaQueryWrapper<com.jiwei.pojo.entity.UserCoupon>()
                            .eq(com.jiwei.pojo.entity.UserCoupon::getTemplateId, t.getId())
                            .eq(com.jiwei.pojo.entity.UserCoupon::getUserId, userId));
            t.setReceivedCount((int) received);
        }
        return new PageResult(result.getTotal(), result.getRecords());
    }

    @Override
    @Transactional
    public void grant(Long templateId, List<Long> userIds) {
        //1、查询模板信息
        CouponTemplate template = couponTemplateMapper.selectById(templateId);
        if (template == null) {
            log.warn("优惠券模板不存在，templateId: {}", templateId);
            return;
        }

        //2、批量创建用户优惠券
        for (Long userId : userIds) {
            UserCoupon coupon = UserCoupon.builder()
                    .templateId(templateId)
                    .userId(userId)
                    .status(0)  //未使用
                    .receiveTime(LocalDateTime.now())
                    .expireTime(LocalDateTime.now().plusDays(template.getValidDays()))
                    .build();
            userCouponMapper.insert(coupon);
        }
        log.info("批量发放优惠券成功，templateId: {}, 发放数量: {}", templateId, userIds.size());
    }

    @Override
    @Transactional
    public void receive(Long templateId) {
        Long userId = BaseContext.getCurrentId();

        //1、查询模板信息
        CouponTemplate template = couponTemplateMapper.selectById(templateId);
        if (template == null) {
            log.warn("优惠券模板不存在，templateId: {}", templateId);
            return;
        }

        //2、校验是否可以领取
        LambdaQueryWrapper<UserCoupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCoupon::getTemplateId, templateId)
               .eq(UserCoupon::getUserId, userId);
        long count = userCouponMapper.selectCount(wrapper);
        if (count >= template.getPerUserLimit()) {
            log.warn("用户已达领取上限，userId: {}, templateId: {}", userId, templateId);
            return;
        }

        //3、创建用户优惠券
        UserCoupon coupon = UserCoupon.builder()
                .templateId(templateId)
                .userId(userId)
                .status(0)  //未使用
                .receiveTime(LocalDateTime.now())
                .expireTime(LocalDateTime.now().plusDays(template.getValidDays()))
                .build();
        userCouponMapper.insert(coupon);
        log.info("用户领取优惠券成功，userId: {}, templateId: {}", userId, templateId);
    }

    @Override
    public PageResult list(Integer page, Integer pageSize) {
        Long userId = BaseContext.getCurrentId();

        //1、构建分页条件
        Page<UserCoupon> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<UserCoupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCoupon::getUserId, userId)
               .orderByDesc(UserCoupon::getReceiveTime);

        //2、执行分页查询
        Page<UserCoupon> result = userCouponMapper.selectPage(p, wrapper);
        //3、批量填充券模板信息（前端展示券面值/名称/门槛）
        fillTemplates(result.getRecords());
        return new PageResult(result.getTotal(), result.getRecords());
    }

    private void fillTemplates(List<UserCoupon> coupons) {
        if (coupons == null || coupons.isEmpty()) {
            return;
        }
        List<Long> templateIds = coupons.stream()
                .map(UserCoupon::getTemplateId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        if (templateIds.isEmpty()) {
            return;
        }
        Map<Long, CouponTemplate> templateMap = couponTemplateMapper.selectBatchIds(templateIds).stream()
                .collect(java.util.stream.Collectors.toMap(CouponTemplate::getId, t -> t));
        coupons.forEach(c -> c.setTemplate(templateMap.get(c.getTemplateId())));
    }

    @Override
    public void release(Long userCouponId) {
        if (userCouponId == null) {
            return;
        }
        UserCoupon coupon = userCouponMapper.selectById(userCouponId);
        if (coupon == null) {
            return;
        }
        //恢复为未使用，解除订单绑定
        coupon.setStatus(0);
        coupon.setOrderId(null);
        coupon.setUseTime(null);
        userCouponMapper.updateById(coupon);
        log.info("优惠券已释放，userCouponId: {}", userCouponId);
    }

    @Override
    public void updateTemplateStatus(Long id, Integer status) {
        CouponTemplate template = couponTemplateMapper.selectById(id);
        if (template == null) {
            throw new com.jiwei.common.exception.BaseException("优惠券模板不存在");
        }
        CouponTemplate update = CouponTemplate.builder().id(id).status(status).build();
        couponTemplateMapper.updateById(update);
        log.info("优惠券模板状态更新: id={}, status={}", id, status);
    }
}
