package com.jiwei.trade.controller.user;

import com.alibaba.fastjson2.JSON;
import com.jiwei.common.result.Result;
import com.jiwei.merchant.mapper.MerchantInfoMapper;
import com.jiwei.pojo.entity.MerchantInfo;
import com.jiwei.pojo.entity.Orders;
import com.jiwei.trade.mapper.OrderMapper;
import com.jiwei.trade.mq.NotifyMessageProducer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * C端催单接口
 */
@RestController
@RequestMapping("/user/order")
@Slf4j
@Tag(name = "催单相关接口")
public class OrderRemindController {

    @Autowired
    private NotifyMessageProducer notifyMessageProducer;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private MerchantInfoMapper merchantInfoMapper;

    @PutMapping("/{id}/remind")
    @Operation(summary = "催单（定向推送给该订单的商家）")
    public Result<String> remind(@PathVariable Long id) {
        log.info("用户催单: orderId={}", id);

        //1、查订单归属商家
        Orders order = orderMapper.selectById(id);
        if (order == null) {
            return Result.error("订单不存在");
        }
        MerchantInfo merchant = merchantInfoMapper.selectById(order.getMerchantId());
        if (merchant == null || merchant.getEmpId() == null) {
            return Result.error("商家不存在");
        }

        //2、定向推送给商家（empId 即商家 WebSocket 连接身份）
        String content = JSON.toJSONString(Map.of(
                "orderId", id,
                "orderNo", order.getNumber(),
                "message", "用户催单，请尽快处理"
        ));
        notifyMessageProducer.sendToUser(merchant.getEmpId(), "remind", content);

        return Result.success();
    }
}
