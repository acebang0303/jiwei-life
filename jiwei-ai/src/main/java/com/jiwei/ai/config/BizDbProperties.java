package com.jiwei.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "biz-db")
@Data
public class BizDbProperties {
    private String host = "localhost";
    private int port = 3307;
    /** 回退账号（本地开发默认 root）；生产应留空，强制走只读账号 */
    private String username = "root";
    private String password;
    /** NL2SQL 专用只读账号：仅 SELECT 权限，配置后优先使用 */
    private String readOnlyUsername;
    private String readOnlyPassword;
    /** 单语句超时（秒），交给 MySQL max_execution_time 兜底，防慢查询拖垮连接池 */
    private int maxExecutionTimeSeconds = 5;
}
