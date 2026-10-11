package com.jiwei.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "biz-db")
public class BizDbProperties {
    private String host = "localhost";
    private int port = 3307;
    private String username = "root";
    private String password;
}
