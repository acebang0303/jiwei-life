package com.jiwei.framework.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信相关配置属性
 */
@Component
@ConfigurationProperties(prefix = "jiwei.wechat")
@Data
public class WeChatProperties {
    //小程序AppID
    private String appid;
    //小程序AppSecret
    private String secret;
    //商户号
    private String mchid;
    //API V3密钥
    private String apiV3Key;
    //支付结果回调通知URL
    private String notifyUrl;
}
