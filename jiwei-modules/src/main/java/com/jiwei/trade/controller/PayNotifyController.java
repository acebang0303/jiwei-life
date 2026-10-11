package com.jiwei.trade.controller;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.jiwei.common.result.Result;
import com.jiwei.framework.utils.WeChatPayUtil;
import com.jiwei.pojo.entity.Orders;
import com.jiwei.trade.mapper.OrderMapper;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 微信支付回调通知接口
 * 无需认证，由微信服务器直接回调
 */
@RestController
@RequestMapping("/api/notify")
@Slf4j
public class PayNotifyController {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private WeChatPayUtil weChatPayUtil;

    /**
     * 微信支付结果回调
     *
     * 文档: https://pay.weixin.qq.com/docs/merchant/apis/jsapi-payment/payment-notice.html
     *
     * 微信会以 POST 方式发送 JSON 密文到 notify_url
     * 需要在 5 秒内返回成功应答 {"code": "SUCCESS", "message": "成功"}
     * 否则微信会每隔一定时间重试（最多 5 次）
     *
     * @param body 加密的支付结果通知数据
     * @param signature 微信签名（Wechatpay-Signature 头）
     * @param timestamp 时间戳（Wechatpay-Timestamp 头）
     * @param nonce 随机串（Wechatpay-Nonce 头）
     * @param serial 证书序列号（Wechatpay-Serial 头）
     * @return
     */
    @PostMapping("/pay")
    @Transactional
    @Operation(summary = "微信支付结果回调")
    public Result<String> payNotify(@RequestBody String body,
                                     @RequestHeader("Wechatpay-Signature") String signature,
                                     @RequestHeader("Wechatpay-Timestamp") String timestamp,
                                     @RequestHeader("Wechatpay-Nonce") String nonce,
                                     @RequestHeader("Wechatpay-Serial") String serial) {
        log.info("收到微信支付回调: signature={}, timestamp={}", signature, timestamp);

        try {
            //1、验签 + 解密回调数据
            // 如果微信支付未配置（Mock模式），parsePayNotify 返回 null
            String decryptData = weChatPayUtil.parsePayNotify(
                    body, signature, timestamp, nonce, serial);

            if (decryptData == null) {
                // Mock 模式或验签失败，使用开发模式处理
                log.info("支付回调 Mock 模式，不做自动状态更新");
                return Result.success();
            }

            //2、解析交易信息
            JSONObject tradeInfo = JSONUtil.parseObj(decryptData);
            String outTradeNo = tradeInfo.getStr("out_trade_no");
            String transactionId = tradeInfo.getStr("transaction_id");
            String tradeState = tradeInfo.getStr("trade_state");

            //3、根据 out_trade_no 查询订单
            Orders order = orderMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Orders>()
                            .eq(Orders::getNumber, outTradeNo));

            if (order == null) {
                log.error("支付回调找不到对应订单: outTradeNo={}", outTradeNo);
                return Result.success(); // 仍需返回成功，避免微信重复回调
            }

            //4、根据交易状态更新订单
            if ("SUCCESS".equals(tradeState)) {
                // 支付成功
                if (order.getStatus().equals(Orders.PENDING_PAYMENT)) {
                    order.setPayStatus(Orders.PAID);
                    order.setStatus(Orders.TO_BE_CONFIRMED);
                    order.setPayTime(LocalDateTime.now());
                    order.setTransactionId(transactionId);
                    orderMapper.updateById(order);
                    log.info("支付回调更新订单成功: orderId={}, transactionId={}",
                            order.getId(), transactionId);

                    //5、TODO: WebSocket 推送商家端新订单提醒
                }
            } else if ("CLOSED".equals(tradeState) || "REVOKED".equals(tradeState)) {
                // 支付已关闭或已撤销
                log.info("支付已关闭: outTradeNo={}, tradeState={}", outTradeNo, tradeState);
            }

        } catch (Exception e) {
            log.error("支付回调处理异常: {}", e.getMessage(), e);
            // 即使处理失败也要返回成功，避免微信重复回调
        }

        //6、返回成功应答
        return Result.success();
    }
}
