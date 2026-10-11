package com.jiwei.trade.controller.user;

import com.jiwei.common.result.PageResult;
import com.jiwei.common.result.Result;
import com.jiwei.pojo.entity.Orders;
import com.jiwei.trade.dto.OrderSubmitDTO;
import com.jiwei.trade.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * C端订单接口
 */
@RestController
@RequestMapping("/user/order")
@Slf4j
@Tag(name = "用户订单相关接口")
public class UserOrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 用户提交订单
     */
    @PostMapping("/submit")
    @Operation(summary = "提交订单")
    public Result<Orders> submit(@RequestBody OrderSubmitDTO orderSubmitDTO) {
        log.info("用户提交订单: {}", orderSubmitDTO);
        Orders order = orderService.submit(orderSubmitDTO);
        return Result.success(order);
    }

    /**
     * 用户取消订单
     */
    @PutMapping("/{id}/cancel")
    @Operation(summary = "取消订单")
    public Result<String> cancel(@PathVariable Long id) {
        log.info("用户取消订单，orderId: {}", id);
        orderService.cancel(id, com.jiwei.common.context.BaseContext.getCurrentId());
        return Result.success();
    }

    /**
     * 支付订单
     */
    @PostMapping("/pay")
    @Operation(summary = "发起支付")
    public Result<String> pay(@RequestBody Map<String, Object> body) {
        Long orderId = body.get("orderId") == null
                ? null : Long.valueOf(body.get("orderId").toString());
        // 默认微信支付渠道（开发环境 mock 模式，发起即自动确认）；余额渠道需账户有余额
        String channelCode = body.get("channelCode") == null
                ? "wechat" : body.get("channelCode").toString();
        if (orderId == null) {
            return Result.error("orderId 不能为空");
        }
        log.info("用户支付订单，orderId: {}, channelCode: {}", orderId, channelCode);
        orderService.pay(orderId, com.jiwei.common.context.BaseContext.getCurrentId(), channelCode);
        return Result.success();
    }

    /**
     * 订单详情（含明细）
     */
    @GetMapping("/{id}")
    @Operation(summary = "订单详情")
    public Result<Orders> detail(@PathVariable Long id) {
        log.info("查询订单详情，orderId: {}", id);
        return Result.success(orderService.getOrderDetail(id));
    }

    /**
     * 再来一单（原订单明细重新加入购物车）
     */
    @PostMapping("/{id}/repetition")
    @Operation(summary = "再来一单")
    public Result<String> repetition(@PathVariable Long id) {
        log.info("再来一单，orderId: {}", id);
        orderService.repetition(id);
        return Result.success();
    }

    /**
     * 历史订单查询
     */
    @GetMapping("/page")
    @Operation(summary = "历史订单查询")
    public Result<PageResult> page(@RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "10") Integer pageSize,
                                    @RequestParam(required = false) Integer status) {
        log.info("查询订单，page: {}, pageSize: {}, status: {}", page, pageSize, status);
        PageResult pageResult = orderService.pageQuery(page, pageSize, status);
        return Result.success(pageResult);
    }
}
