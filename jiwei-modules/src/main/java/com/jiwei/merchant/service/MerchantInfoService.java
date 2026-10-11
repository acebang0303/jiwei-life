package com.jiwei.merchant.service;

import com.jiwei.common.result.PageResult;
import com.jiwei.pojo.entity.MerchantInfo;

import java.util.List;

/**
 * 商户服务接口
 */
public interface MerchantInfoService {

    /**
     * 分页查询商户列表
     */
    PageResult pageQuery(int page, int pageSize, String name, Integer status);

    /**
     * 审核商户
     */
    void audit(Long id, Integer status, String auditReason);

    /**
     * 修改商户营业状态
     */
    void updateStatus(Long id, Integer status);

    /**
     * 查询商户详情
     */
    MerchantInfo getById(Long id);

    /**
     * 查询所有营业中的商户
     */
    List<MerchantInfo> listOpen();

    /**
     * 根据员工ID查询关联商户
     */
    MerchantInfo getByEmpId(Long empId);

    /**
     * 更新商户资料（商家端保存店铺信息，校验归属当前登录员工）
     */
    void update(MerchantInfo merchantInfo);
}
