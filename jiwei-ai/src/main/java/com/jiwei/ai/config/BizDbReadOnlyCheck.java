package com.jiwei.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

/**
 * NL2SQL 只读数据源的启动自检。
 *
 * 为什么要有它：「只读」在配置层有三处开关（只读账号、连接池 readOnly、去掉 allowMultiQueries），
 * 任何一处被改回默认值（比如为了本地调试把 read-only-username 清空、换回 root），
 * 服务照常启动、SQL 照样能跑，问题只在被注入时才会暴露——那时已经晚了。
 * 这里在启动时主动验证一次，把「配置意图」变成「可观测的事实」。
 *
 * 探针选的是 CREATE TEMPORARY TABLE：
 *   - 在只读事务里会被 MySQL 拒绝（ERROR 1792 Cannot execute statement in a READ ONLY transaction）；
 *   - 只读账号则连权限都没有（ERROR 1142 CREATE command denied）；
 *   - 若它竟然成功了，说明这个连接真的可写 —— 此时只报错不中断启动（避免影响其它功能），
 *     并顺手把临时表删掉（临时表是会话级的，本来就随连接消失）。
 */
@Component
@Slf4j
public class BizDbReadOnlyCheck implements ApplicationRunner {

    private static final String PROBE = "CREATE TEMPORARY TABLE jiwei_ro_probe (id INT)";

    private final JdbcTemplate bizJdbcTemplate;

    public BizDbReadOnlyCheck(@Qualifier("bizJdbcTemplate") JdbcTemplate bizJdbcTemplate) {
        this.bizJdbcTemplate = bizJdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            bizJdbcTemplate.execute(PROBE);
            log.error("NL2SQL 只读校验未通过：探针 CREATE TEMPORARY TABLE 执行成功，该连接可写！"
                    + "请检查 biz-db.read-only-username/password 与连接池 readOnly 配置");
            try {
                bizJdbcTemplate.execute("DROP TEMPORARY TABLE IF EXISTS jiwei_ro_probe");
            } catch (Exception ignored) {
                // 清理失败无所谓：临时表随会话销毁
            }
        } catch (Exception e) {
            if (isReadOnlyRejection(e)) {
                log.info("NL2SQL 只读校验通过：写探针被数据库拒绝（{}）", describe(e));
            } else {
                log.warn("NL2SQL 只读校验未得出明确结论（非预期错误，请确认数据源可用）: {}", e.getMessage());
            }
        }
    }

    /** 识别「因为只读而被拒」的错误码：1792=READ ONLY 事务，1142/1044=权限不足。 */
    private boolean isReadOnlyRejection(Throwable t) {
        Throwable cursor = t;
        while (cursor != null) {
            if (cursor instanceof SQLException sql) {
                int code = sql.getErrorCode();
                if (code == 1792 || code == 1142 || code == 1044) {
                    return true;
                }
                String state = sql.getSQLState();
                if ("25006".equals(state) || "42000".equals(state)) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private String describe(Throwable t) {
        Throwable cursor = t;
        while (cursor != null) {
            if (cursor instanceof SQLException sql) {
                return "errorCode=" + sql.getErrorCode() + ", sqlState=" + sql.getSQLState();
            }
            cursor = cursor.getCause();
        }
        return t.getClass().getSimpleName();
    }
}
