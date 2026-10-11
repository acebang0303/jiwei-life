package com.jiwei.modules.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 *
 * 多数据源路由由 baomidou dynamic-datasource 自动配置，
 * 通过 @DS 注解指定各 Mapper 使用的数据源：
 *   @DS("auth")     → jiwei_auth     (认证)
 *   @DS("user")     → jiwei_user     (用户)
 *   @DS("merchant") → jiwei_merchant (商家)
 *   @DS("trade")    → jiwei_trade    (交易)
 *   @DS("market")   → jiwei_market   (营销)
 *   @DS("social")   → jiwei_social   (社区)
 */
@Configuration
@MapperScan(basePackages = {
        "com.jiwei.*.mapper",
        "com.jiwei.market.shortlink.mapper",
        "com.jiwei.trade.pay.mapper"
})
public class DataSourceConfig {
}
