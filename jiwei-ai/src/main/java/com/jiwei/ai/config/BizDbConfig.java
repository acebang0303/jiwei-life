package com.jiwei.ai.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(BizDbProperties.class)
public class BizDbConfig {

    private final BizDbProperties props;

    public BizDbConfig(BizDbProperties props) {
        this.props = props;
    }

    @Bean
    public DataSource bizDataSource() {
        HikariConfig config = new HikariConfig();
        // 默认连 jiwei_merchant，跨库访问用全限定表名 jiwei_merchant.dish / jiwei_social.note
        config.setJdbcUrl(String.format(
                "jdbc:mysql://%s:%d/jiwei_merchant?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowMultiQueries=true",
                props.getHost(), props.getPort()));
        config.setUsername(props.getUsername());
        config.setPassword(props.getPassword());
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate bizJdbcTemplate(DataSource bizDataSource) {
        return new JdbcTemplate(bizDataSource);
    }
}
