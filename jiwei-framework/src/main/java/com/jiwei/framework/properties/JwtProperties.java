package com.jiwei.framework.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT配置属性
 */
@Component
@ConfigurationProperties(prefix = "jiwei.jwt")
@Data
public class JwtProperties {
    //管理员JWT密钥
    private String adminSecretKey;
    //管理员JWT过期时间(毫秒)
    private long adminTtl;
    //管理员Token请求头名称
    private String adminTokenName;
    //用户JWT密钥
    private String userSecretKey;
    //用户JWT过期时间(毫秒)
    private long userTtl;
    //用户Token请求头名称
    private String userTokenName;
}
