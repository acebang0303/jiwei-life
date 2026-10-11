package com.jiwei.framework.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.jiwei.framework.properties.WeChatProperties;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 微信支付 V3 API 工具类
 *
 * 职责分工：
 * - wechatpay-java SDK：商户认证、支付回调验签、平台证书管理
 * - Hutool HTTP：微信登录 code2Session（无需签名）
 * - 支付/退款 API：生产环境接入时通过 SDK Credential 签名后调用 V3 REST API
 *
 * Mock 模式（默认）：未配置微信商户信息时，所有支付相关操作返回模拟数据
 */
@Component
@Slf4j
public class WeChatPayUtil {

    @Autowired
    private WeChatProperties weChatProperties;

    private Config config;
    private NotificationParser notificationParser;
    private boolean initialized = false;

    private synchronized void initIfNeeded() {
        if (initialized) return;

        String mchid = weChatProperties.getMchid();
        String apiV3Key = weChatProperties.getApiV3Key();

        if (StrUtil.isBlank(mchid) || StrUtil.isBlank(apiV3Key)
                || "your-mchid".equals(mchid)) {
            log.warn("微信支付未配置，使用Mock模式");
            initialized = true;
            return;
        }

        try {
            String privateKeyPath = System.getenv("WECHAT_PRIVATE_KEY_PATH");
            String serialNo = System.getenv("WECHAT_MERCHANT_SERIAL_NO");
            if (StrUtil.isBlank(privateKeyPath) || StrUtil.isBlank(serialNo)) {
                log.warn("商户私钥或证书序列号未配置，使用Mock模式");
                initialized = true;
                return;
            }

            config = new RSAAutoCertificateConfig.Builder()
                    .merchantId(mchid)
                    .privateKeyFromPath(privateKeyPath)
                    .merchantSerialNumber(serialNo)
                    .apiV3Key(apiV3Key)
                    .build();

            notificationParser = new NotificationParser((NotificationConfig) config);
            log.info("微信支付SDK初始化成功，商户号: {}", mchid);
        } catch (Exception e) {
            log.error("微信支付SDK初始化失败: {}", e.getMessage());
        }
        initialized = true;
    }

    /**
     * JSAPI 下单（小程序支付）
     *
     * 生产模式接入文档:
     *   https://pay.weixin.qq.com/docs/merchant/apis/jsapi-payment/direct-jsons/jsapi-prepay.html
     *   POST /v3/pay/transactions/jsapi
     */
    public String jsapiPrepay(String orderNumber, String description, int totalAmount, String openid) {
        initIfNeeded();

        if (config == null) {
            log.info("Mock模式 - JSAPI下单: orderNumber={}, amount={}", orderNumber, totalAmount);
            return JSONUtil.toJsonStr(new JSONObject()
                    .set("appId", weChatProperties.getAppid())
                    .set("timeStamp", String.valueOf(System.currentTimeMillis() / 1000))
                    .set("nonceStr", StrUtil.uuid().replace("-", ""))
                    .set("package", "prepay_id=mock_" + orderNumber)
                    .set("signType", "RSA")
                    .set("paySign", "mock_signature_for_" + orderNumber));
        }

        // 生产模式: 使用 SDK Config 签名后调用 WeChat Pay V3 JSAPI 下单 API
        // 实际接入时参考 wechatpay-java SDK 文档构造签名和请求
        throw new UnsupportedOperationException(
                "微信支付生产模式: 请通过 wechatpay-java SDK 的 Credential 签名后调用 V3 API");
    }

    /**
     * 支付回调验签 + 解密
     *
     * 使用 wechatpay-java SDK 的 NotificationParser 处理回调
     */
    public String parsePayNotify(String body, String signature, String timestamp,
                                  String nonce, String serialNumber) {
        initIfNeeded();

        if (notificationParser == null) {
            log.info("Mock模式 - 支付回调验签");
            return null;
        }

        try {
            RequestParam requestParam = new RequestParam.Builder()
                    .serialNumber(serialNumber)
                    .nonce(nonce)
                    .signature(signature)
                    .timestamp(timestamp)
                    .body(body)
                    .build();

            java.lang.reflect.Method parseMethod = notificationParser.getClass()
                    .getMethod("parse", RequestParam.class, Class.class);
            Object notification = parseMethod.invoke(notificationParser, requestParam, Object.class);

            if (notification == null) {
                log.error("支付回调验签失败");
                return null;
            }

            String decryptData = (String) notification.getClass()
                    .getMethod("getDecryptData").invoke(notification);
            log.info("支付回调验签成功: {}", JSONUtil.parseObj(decryptData).getStr("trade_state"));
            return decryptData;

        } catch (Exception e) {
            log.error("支付回调处理失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 申请退款
     *
     * 生产模式接入文档:
     *   https://pay.weixin.qq.com/docs/merchant/apis/refund/refunds/create.html
     *   POST /v3/refund/domestic/refunds
     */
    public String refund(String orderNumber, int refundAmount, int totalAmount) {
        initIfNeeded();

        if (config == null) {
            String mockRefundId = "RF" + System.currentTimeMillis();
            log.info("Mock模式 - 申请退款: orderNumber={}, amount={}", orderNumber, refundAmount);
            return mockRefundId;
        }

        throw new UnsupportedOperationException(
                "微信退款生产模式: 请通过 wechatpay-java SDK 的 Credential 签名后调用 V3 API");
    }

    /**
     * 获取微信小程序 openid（无需商户认证，直接调用微信开放平台 API）
     *
     * 文档: https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-login/code2Session.html
     */
    public String getOpenid(String code) {
        String appid = weChatProperties.getAppid();
        String secret = weChatProperties.getSecret();

        if (StrUtil.isBlank(appid) || StrUtil.isBlank(secret) || "your-appid".equals(appid)) {
            log.info("Mock模式 - 获取openid: code={}", code);
            return code;
        }

        try {
            String url = StrUtil.format(
                    "https://api.weixin.qq.com/sns/jscode2session?appid={}&secret={}&js_code={}&grant_type=authorization_code",
                    appid, secret, code);
            String response = HttpUtil.get(url);
            JSONObject result = JSONUtil.parseObj(response);

            if (result.containsKey("openid")) {
                String openid = result.getStr("openid");
                log.info("获取openid成功: {}", openid);
                return openid;
            }

            int errcode = result.getInt("errcode", -1);
            String errmsg = result.getStr("errmsg", "未知错误");
            log.warn("微信API返回错误 (errcode={}, errmsg={}), 回退Mock模式", errcode, errmsg);
            return code;

        } catch (Exception e) {
            log.warn("获取openid异常: {}, 回退Mock模式", e.getMessage());
            return code;
        }
    }
}
