package com.jiwei.framework.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云OSS配置属性
 */
@Component
@ConfigurationProperties(prefix = "jiwei.alioss")
@Data
public class AliOssProperties {
    //Endpoint
    private String endpoint;
    //AccessKey ID
    private String accessKeyId;
    //AccessKey Secret
    private String accessKeySecret;
    //Bucket名称
    private String bucketName;
}
