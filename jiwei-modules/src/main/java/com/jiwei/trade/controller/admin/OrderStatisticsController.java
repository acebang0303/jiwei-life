package com.jiwei.trade.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiwei.common.result.Result;
import com.jiwei.pojo.entity.Orders;
import com.jiwei.trade.mapper.OrderMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理端订单统计接口
 */
@RestController
@RequestMapping("/admin/order")
@Slf4j
@Tag(name = "订单统计相关接口")
public class OrderStatisticsController {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 订单统计数据
     * 从数据库实时聚合查询当日订单数、交易额、各状态订单数
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取订单统计数据（merchantId 为空为全平台，商家端传自身店铺）")
    public Result<Map<String, Object>> statistics(@RequestParam(required = false) Long merchantId) {
        log.info("获取订单统计数据, merchantId: {}", merchantId);

        //1、计算今日日期范围
        LocalDate today = LocalDate.now();
        String todayStart = today.format(DateTimeFormatter.ISO_DATE) + " 00:00:00";
        String todayEnd = today.format(DateTimeFormatter.ISO_DATE) + " 23:59:59";

        //2、查询今日订单数
        LambdaQueryWrapper<Orders> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.eq(merchantId != null, Orders::getMerchantId, merchantId)
                    .ge(Orders::getCreateTime, todayStart)
                    .le(Orders::getCreateTime, todayEnd);
        Long todayOrders = orderMapper.selectCount(todayWrapper);

        //3、查询待处理订单数（待接单+备货中+待取餐）
        LambdaQueryWrapper<Orders> pendingWrapper = new LambdaQueryWrapper<>();
        pendingWrapper.eq(merchantId != null, Orders::getMerchantId, merchantId)
                .in(Orders::getStatus,
                Orders.TO_BE_CONFIRMED, Orders.PREPARING, Orders.AWAITING_PICKUP);
        Long pendingOrders = orderMapper.selectCount(pendingWrapper);

        //4、查询已完成订单数
        LambdaQueryWrapper<Orders> completedWrapper = new LambdaQueryWrapper<>();
        completedWrapper.eq(merchantId != null, Orders::getMerchantId, merchantId)
                .eq(Orders::getStatus, Orders.COMPLETED);
        Long completedOrders = orderMapper.selectCount(completedWrapper);

        //5、查询今日已支付总金额（简化为统计今日创建且已支付的订单）
        LambdaQueryWrapper<Orders> revenueWrapper = new LambdaQueryWrapper<>();
        revenueWrapper.eq(merchantId != null, Orders::getMerchantId, merchantId)
                      .ge(Orders::getCreateTime, todayStart)
                      .le(Orders::getCreateTime, todayEnd)
                      .eq(Orders::getPayStatus, Orders.PAID);
        // 通过遍历计算总金额
        java.util.List<Orders> paidOrders = orderMapper.selectList(revenueWrapper);
        double todayRevenue = paidOrders.stream()
                .mapToDouble(o -> o.getPayAmount().doubleValue())
                .sum();

        //6、构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("todayOrders", todayOrders);
        result.put("todayRevenue", String.format("%.2f", todayRevenue));
        result.put("pendingOrders", pendingOrders);
        result.put("completedOrders", completedOrders);
        result.put("date", today.format(DateTimeFormatter.ISO_DATE));
        return Result.success(result);
    }
}
