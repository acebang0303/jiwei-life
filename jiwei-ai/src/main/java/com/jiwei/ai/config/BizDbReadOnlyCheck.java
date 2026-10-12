package com.jiwei.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.regex.Pattern;

/**
 * NL2SQL 只读数据源的启动自检（默认 fail-fast）。
 *
 * 为什么要有它：「只读」在配置层有三处开关（只读账号、连接池 readOnly、去掉 allowMultiQueries），
 * 任何一处被改回默认值（比如为了本地调试把 read-only-username 清空、换回 root），
 * 服务照常启动、SQL 照样能跑，问题只在被注入时才会暴露——那时已经晚了。
 * 这里在启动时主动验证一次，把「配置意图」变成「启动期就拦住的事实」。
 *
 * 探针选的是 CREATE TEMPORARY TABLE：
 *   - 只读事务里会被 MySQL 拒绝（ERROR 1792 Cannot execute statement in a READ ONLY transaction）；
 *   - 只读账号则连权限都没有（ERROR 1142 CREATE command denied）；
 *   - 若它竟然成功了，说明这个连接真的可写 —— 默认直接抛异常让应用启动失败（fail-fast），
 *     避免一个「自以为是只读、实际可写」的服务带着风险上线。
 *
 * 三种结果的处理：
 *   探针被拒且原因是只读/权限  -> 通过，打 INFO
 *   探针成功（确认可写）        -> fail-fast 抛异常终止启动；配置 fail-fast=false 时仅打 ERROR
 *   其它异常（连不上等，无法定论）-> 打 WARN 不终止：这是可用性问题，不是安全门禁该管的事
 *
 * 关掉或降级：biz-db.read-only-check.enabled=false / fail-fast=false
 */
@Component
@Slf4j
public class BizDbReadOnlyCheck implements ApplicationRunner {

    private static final String PROBE = "CREATE TEMPORARY TABLE jiwei_ro_probe (id INT)";
    /** 客户端/服务端两种拦截的报文里都带 read-only 字样 */
    private static final Pattern READ_ONLY_HINT = Pattern.compile("read[\\s-]?only", Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate bizJdbcTemplate;
    private final BizDbProperties.ReadOnlyCheck config;

    public BizDbReadOnlyCheck(@Qualifier("bizJdbcTemplate") JdbcTemplate bizJdbcTemplate,
                              BizDbProperties properties) {
        this.bizJdbcTemplate = bizJdbcTemplate;
        this.config = properties.getReadOnlyCheck();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!config.isEnabled()) {
            log.warn("NL2SQL 只读自检已关闭（biz-db.read-only-check.enabled=false），跳过");
            return;
        }

        boolean writable;
        Exception failure = null;
        try {
            bizJdbcTemplate.execute(PROBE);
            writable = true;
        } catch (Exception e) {
            writable = false;
            failure = e;
        }

        if (writable) {
            // 探针成功建了临时表：这个连接可写，立刻清掉并把问题抛出去
            dropProbeQuietly();
            String msg = "NL2SQL 只读校验未通过：写探针 CREATE TEMPORARY TABLE 执行成功，该连接可写！"
                    + "请检查 biz-db.read-only-username/password 与连接池 readOnly 配置";
            if (config.isFailFast()) {
                throw new IllegalStateException(msg + "（fail-fast 已开启，拒绝启动）");
            }
            log.error("{}（biz-db.read-only-check.fail-fast=false，仅告警）", msg);
            return;
        }

        if (isReadOnlyRejection(failure)) {
            log.info("NL2SQL 只读校验通过：写探针被数据库拒绝（{}）", describe(failure));
        } else {
            log.warn("NL2SQL 只读校验未得出明确结论（非只读相关错误，请确认数据源可用）: {}",
                    failure == null ? "unknown" : failure.getMessage());
        }
    }

    private void dropProbeQuietly() {
        try {
            bizJdbcTemplate.execute("DROP TEMPORARY TABLE IF EXISTS jiwei_ro_probe");
        } catch (Exception ignored) {
            // 临时表随会话销毁，清理失败无副作用
        }
    }

    /**
     * 识别「因为只读而被拒」。三种形态都要认：
     *   1. 服务端只读事务拒绝: errorCode 1792 / SQLState 25006
     *   2. 服务端权限不足:     errorCode 1142 / 1044 / SQLState 42000
     *   3. 驱动客户端拦截:     MySQL Connector/J 在 readOnly 连接上直接拒掉 DML，
     *      报文是「Connection is read-only. Queries leading to data modification are not allowed.」，
     *      errorCode 为 0、SQLState 为空 —— 语句根本没到服务端。
     *      所以必须补一条按报文里的 read-only 关键字识别，否则会把「通过」误报成「未定论」。
     */
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
            String msg = cursor.getMessage();
            if (msg != null && READ_ONLY_HINT.matcher(msg).find()) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private String describe(Throwable t) {
        Throwable cursor = t;
        while (cursor != null) {
            if (cursor instanceof SQLException sql && sql.getErrorCode() != 0) {
                return "errorCode=" + sql.getErrorCode() + ", sqlState=" + sql.getSQLState();
            }
            cursor = cursor.getCause();
        }
        // 客户端拦截没有错误码，退回用报文摘要说明（截断避免刷屏）
        Throwable root = t;
        while (root != null && root.getCause() != null) {
            root = root.getCause();
        }
        if (root == null || root.getMessage() == null) {
            return "unknown";
        }
        String msg = root.getMessage().replaceAll("\\s+", " ");
        return msg.length() > 120 ? msg.substring(0, 120) + "..." : msg;
    }
}
