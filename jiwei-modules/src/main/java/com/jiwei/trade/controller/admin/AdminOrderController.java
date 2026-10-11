package com.jiwei.trade.controller.admin;

import com.jiwei.common.result.PageResult;
import com.jiwei.common.result.Result;
import com.jiwei.trade.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端订单接口
 */
@RestController
@RequestMapping("/admin/order")
@Slf4j
@Tag(name = "订单管理相关接口")
public class AdminOrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 分页查询订单
     *
     * @param page
     * @param pageSize
     * @param merchantId
     * @param status
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询订单")
    public Result<PageResult> page(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            Long merchantId, Integer status) {
        log.info("分页查询订单，page: {}, merchantId: {}, status: {}", page, merchantId, status);
        PageResult pageResult = orderService.adminPageQuery(page, pageSize, merchantId, status);
        return Result.success(pageResult);
    }

    /**
     * 商家接单
     *
     * @param id
     * @return
     */
    @PutMapping("/{id}/confirm")
    @Operation(summary = "商家接单")
    public Result<String> confirm(@PathVariable Long id) {
        log.info("商家接单，orderId: {}", id);
        orderService.confirm(id);
        return Result.success();
    }

    /**
     * 商家拒单
     *
     * @param id
     * @param reason
     * @return
     */
    @PutMapping("/{id}/reject")
    @Operation(summary = "商家拒单")
    public Result<String> reject(@PathVariable Long id, @RequestParam(required = false) String reason) {
        log.info("商家拒单，orderId: {}, reason: {}", id, reason);
        orderService.reject(id, reason);
        return Result.success();
    }

    /**
     * 商家备货完成（备货中 → 待取餐）
     */
    @PutMapping("/{id}/prepare")
    @Operation(summary = "商家备货完成")
    public Result<String> prepare(@PathVariable Long id) {
        log.info("商家备货完成，orderId: {}", id);
        orderService.prepare(id);
        return Result.success();
    }

    /**
     * 完成订单
     *
     * @param id
     * @return
     */
    @PutMapping("/{id}/complete")
    @Operation(summary = "完成订单")
    public Result<String> complete(@PathVariable Long id) {
        log.info("完成订单，orderId: {}", id);
        orderService.complete(id);
        return Result.success();
    }

    /**
     * 退款
     *
     * @param id
     * @return
     */
    @PostMapping("/{id}/refund")
    @Operation(summary = "退款")
    public Result<String> refund(@PathVariable Long id) {
        log.info("退款处理，orderId: {}", id);
        orderService.refund(id);
        return Result.success();
    }
}
