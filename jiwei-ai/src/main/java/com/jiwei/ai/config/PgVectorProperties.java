package com.jiwei.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "pgvector")
public class PgVectorProperties {
    private String host = "localhost";
    private int port = 5432;
    private String database = "jiwei_ai";
    private String username = "postgres";
    private String password;
}
