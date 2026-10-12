package com.jiwei.ai.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * NL2SQL 业务数据源：必须按「只读」配置，让数据库成为最后一道防线。
 *
 * 三层只读保证：
 *   1. 连接池 setReadOnly(true)：Hikari 会把连接标记为只读，
 *      MySQL Connector/J 默认 readOnlyPropagatesToServer=true，
 *      连接建立后服务端会话进入 READ ONLY，写操作直接被 MySQL 拒绝。
 *   2. 单独的只读账号（biz-db.read-only-username），只授予 SELECT。
 *   3. 不开启 allowMultiQueries —— 该参数允许一个请求里堆叠多条语句，
 *      正是 SQL 文本校验最怕的东西，必须关掉。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(BizDbProperties.class)
public class BizDbConfig {

    private final BizDbProperties props;

    public BizDbConfig(BizDbProperties props) {
        this.props = props;
    }

    @Bean
    public DataSource bizDataSource() {
        String username = StringUtils.hasText(props.getReadOnlyUsername())
                ? props.getReadOnlyUsername() : props.getUsername();
        String password = StringUtils.hasText(props.getReadOnlyUsername())
                ? props.getReadOnlyPassword() : props.getPassword();

        HikariConfig config = new HikariConfig();
        // 注意：不带 allowMultiQueries，单请求只能执行一条语句
        config.setJdbcUrl(String.format(
                "jdbc:mysql://%s:%d/jiwei_merchant?useUnicode=true&characterEncoding=utf-8"
                        + "&serverTimezone=Asia/Shanghai&allowMultiQueries=false"
                        + "&sessionVariables=max_execution_time=%d",
                props.getHost(), props.getPort(), props.getMaxExecutionTimeSeconds()));
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("jiwei-ai-biz-ro");
        // 关键：连接只读，写语句在服务端被拒（LLM 若绕过文本校验也写不进去）
        config.setReadOnly(true);
        config.setAutoCommit(true);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5_000);
        if (!StringUtils.hasText(props.getReadOnlyUsername())) {
            log.warn("biz-db.read-only-username 未配置，NL2SQL 正在使用通用账号({})。"
                    + "生产环境请配置仅含 SELECT 权限的只读账号（见 db/08_ai_readonly_user.sql）", username);
        }
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate bizJdbcTemplate(DataSource bizDataSource) {
        JdbcTemplate template = new JdbcTemplate(bizDataSource);
        // 服务端 max_execution_time 之外再加一层客户端语句超时
        template.setQueryTimeout(props.getMaxExecutionTimeSeconds());
        return template;
    }
}
