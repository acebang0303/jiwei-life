package com.jiwei.framework.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 百度地图配置属性
 */
@Component
@ConfigurationProperties(prefix = "jiwei.baidu")
@Data
public class BaiduProperties {
    //百度地图AK
    private String ak;
}
