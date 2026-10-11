package com.jiwei.merchant.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import com.jiwei.common.context.BaseContext;
import com.jiwei.common.result.PageResult;
import com.jiwei.merchant.mapper.MerchantInfoMapper;
import com.jiwei.merchant.service.MerchantInfoService;
import com.jiwei.pojo.entity.MerchantInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商户服务实现类
 */
@DS("merchant")
@Service
@Slf4j
public class MerchantInfoServiceImpl implements MerchantInfoService {

    @Autowired
    private MerchantInfoMapper merchantInfoMapper;

    @Override
    @Cacheable(value = "merchant", key = "#page + '_' + #pageSize + '_' + #name + '_' + #status")
    public PageResult pageQuery(int page, int pageSize, String name, Integer status) {
        //1、构建分页条件
        Page<MerchantInfo> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<MerchantInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(name != null, MerchantInfo::getName, name)
               .eq(status != null, MerchantInfo::getStatus, status)
               .orderByDesc(MerchantInfo::getCreateTime);

        //2、执行分页查询
        Page<MerchantInfo> result = merchantInfoMapper.selectPage(p, wrapper);
        return new PageResult(result.getTotal(), result.getRecords());
    }

    @Override
    @Transactional
    @CacheEvict(value = "merchant", allEntries = true)
    public void audit(Long id, Integer status, String auditReason) {
        //1、查询商户信息
        MerchantInfo merchant = merchantInfoMapper.selectById(id);
        if (merchant == null) {
            log.warn("商户不存在，merchantId: {}", id);
            return;
        }

        //2、更新审核状态
        merchant.setStatus(status);
        merchant.setAuditReason(auditReason);
        merchantInfoMapper.updateById(merchant);
        log.info("商户审核完成，merchantId: {}, status: {}, reason: {}", id, status, auditReason);
    }

    @Override
    @Transactional
    @CacheEvict(value = "merchant", allEntries = true)
    public void updateStatus(Long id, Integer status) {
        //1、更新营业状态
        MerchantInfo merchant = MerchantInfo.builder().id(id).status(status).build();
        merchantInfoMapper.updateById(merchant);
        log.info("更新商户营业状态，merchantId: {}, status: {}", id, status);
    }

    @Override
    public MerchantInfo getById(Long id) {
        //1、查询商户详情
        return merchantInfoMapper.selectById(id);
    }

    @Override
    public List<MerchantInfo> listOpen() {
        LambdaQueryWrapper<MerchantInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MerchantInfo::getStatus, 1)
               .orderByDesc(MerchantInfo::getCreateTime);
        return merchantInfoMapper.selectList(wrapper);
    }

    @Override
    public MerchantInfo getByEmpId(Long empId) {
        //演示数据可能同一 emp_id 绑定多个店铺（mock 数据复用了 emp_id 2-5），
        //selectOne 遇到多行会抛异常，故取最早创建的一个
        LambdaQueryWrapper<MerchantInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MerchantInfo::getEmpId, empId)
               .orderByAsc(MerchantInfo::getId)
               .last("LIMIT 1");
        return merchantInfoMapper.selectOne(wrapper);
    }

    @Override
    public void update(MerchantInfo merchantInfo) {
        Long empId = BaseContext.getCurrentId();
        if (merchantInfo == null || merchantInfo.getId() == null) {
            throw new com.jiwei.common.exception.BaseException("店铺信息不能为空");
        }
        //归属校验：只能修改当前登录员工绑定的店铺
        MerchantInfo own = getByEmpId(empId);
        if (own == null || !own.getId().equals(merchantInfo.getId())) {
            throw new com.jiwei.common.exception.BaseException("无权修改该店铺信息");
        }
        //只允许更新资料字段，防止篡改 emp_id / status / id
        MerchantInfo update = MerchantInfo.builder()
                .id(merchantInfo.getId())
                .name(merchantInfo.getName())
                .logo(merchantInfo.getLogo())
                .description(merchantInfo.getDescription())
                .address(merchantInfo.getAddress())
                .longitude(merchantInfo.getLongitude())
                .latitude(merchantInfo.getLatitude())
                .phone(merchantInfo.getPhone())
                .businessHours(merchantInfo.getBusinessHours())
                .deliveryRange(merchantInfo.getDeliveryRange())
                .build();
        merchantInfoMapper.updateById(update);
        log.info("店铺资料更新成功，merchantId: {}", merchantInfo.getId());
    }
}
