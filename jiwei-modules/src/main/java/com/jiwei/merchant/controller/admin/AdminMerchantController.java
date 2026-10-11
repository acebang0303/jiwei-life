package com.jiwei.merchant.controller.admin;

import com.jiwei.common.context.BaseContext;
import com.jiwei.common.result.PageResult;
import com.jiwei.common.result.Result;
import com.jiwei.merchant.service.MerchantInfoService;
import com.jiwei.pojo.entity.MerchantInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端商户接口
 */
@RestController
@RequestMapping("/admin/merchant")
@Slf4j
@Tag(name = "商户管理相关接口")
public class AdminMerchantController {

    @Autowired
    private MerchantInfoService merchantInfoService;

    /**
     * 分页查询商户列表
     *
     * @param page
     * @param pageSize
     * @param name
     * @param status
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询商户列表")
    public Result<PageResult> page(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            String name, Integer status) {
        log.info("分页查询商户，page: {}, name: {}", page, name);
        PageResult pageResult = merchantInfoService.pageQuery(page, pageSize, name, status);
        return Result.success(pageResult);
    }

    /**
     * 审核商户（通过/驳回）
     *
     * @param id
     * @param status
     * @param auditReason
     * @return
     */
    @PutMapping("/{id}/audit")
    @Operation(summary = "审核商户")
    public Result<String> audit(@PathVariable Long id,
                                 @RequestParam Integer status,
                                 @RequestParam(required = false) String auditReason) {
        log.info("审核商户，id: {}, status: {}, reason: {}", id, status, auditReason);
        merchantInfoService.audit(id, status, auditReason);
        return Result.success();
    }

    /**
     * 修改商户营业状态
     *
     * @param id
     * @param status
     * @return
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "修改商户营业状态")
    public Result<String> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        log.info("修改商户状态，id: {}, status: {}", id, status);
        merchantInfoService.updateStatus(id, status);
        return Result.success();
    }

    /**
     * 获取当前商家账号的店铺信息
     */
    @GetMapping("/info")
    @Operation(summary = "获取当前商家店铺信息")
    public Result<MerchantInfo> info() {
        Long empId = BaseContext.getCurrentId();
        MerchantInfo info = merchantInfoService.getByEmpId(empId);
        return Result.success(info);
    }

    /**
     * 更新当前商家账号的店铺资料（保存店铺信息）
     */
    @PutMapping("/info")
    @Operation(summary = "保存当前商家店铺信息")
    public Result<String> updateInfo(@RequestBody MerchantInfo merchantInfo) {
        log.info("保存店铺信息，merchantId: {}", merchantInfo.getId());
        merchantInfoService.update(merchantInfo);
        return Result.success();
    }
}
