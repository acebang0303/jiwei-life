package com.jiwei.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 只读自检的四种结局 + fail-fast 的两个方向。
 *
 * 其中「驱动客户端拦截」这条来自真机跑出来的缺陷：readOnly 连接下
 * MySQL Connector/J 会自己拒掉 DML（errorCode=0、无 SQLState、报文带 read-only），
 * 原实现只认服务端错误码，会把「通过」误判成「未定论」。
 */
class BizDbReadOnlyCheckTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private BizDbReadOnlyCheck check(boolean enabled, boolean failFast) {
        BizDbProperties props = new BizDbProperties();
        props.getReadOnlyCheck().setEnabled(enabled);
        props.getReadOnlyCheck().setFailFast(failFast);
        return new BizDbReadOnlyCheck(jdbc, props);
    }

    private void probeThrows(Exception e) {
        // 用 doThrow 形式：JdbcTemplate.execute(String) 是 <T> T 的泛型方法，
        // when(...) 会因类型推断失败报「不允许使用空类型」
        doThrow(e).when(jdbc).execute(anyString());
    }

    // ---------- 通过：三种「被拒」形态都不能炸 ----------

    @Test
    void shouldPassWhenServerRejectsByReadOnlyTransaction() {
        SQLException sql = new SQLException("Cannot execute statement in a READ ONLY transaction.",
                "25006", 1792);
        probeThrows(new UncategorizedSQLException("StatementCallback", "CREATE TEMPORARY TABLE x", sql));

        assertThatCode(() -> check(true, true).run(null)).doesNotThrowAnyException();
    }

    @Test
    void shouldPassWhenServerRejectsByPrivilege() {
        SQLException sql = new SQLException("CREATE command denied to user 'jiwei_ai_ro'",
                "42000", 1142);
        probeThrows(new UncategorizedSQLException("StatementCallback", "CREATE TEMPORARY TABLE x", sql));

        assertThatCode(() -> check(true, true).run(null)).doesNotThrowAnyException();
    }

    @Test
    void shouldPassWhenDriverBlocksWriteOnReadOnlyConnection() {
        // MySQL Connector/J 客户端拦截：无错误码、无 SQLState，只能靠报文识别
        SQLException sql = new SQLException(
                "Connection is read-only. Queries leading to data modification are not allowed.");
        probeThrows(new UncategorizedSQLException("StatementCallback", "CREATE TEMPORARY TABLE x", sql));

        assertThatCode(() -> check(true, true).run(null)).doesNotThrowAnyException();
    }

    // ---------- 未定论：非只读类错误不拦启动 ----------

    @Test
    void shouldNotFailFastOnIndeterminateError() {
        probeThrows(new IllegalStateException("Communications link failure"));

        assertThatCode(() -> check(true, true).run(null)).doesNotThrowAnyException();
    }

    // ---------- 可写：fail-fast 生效 ----------

    @Test
    void shouldFailStartupWhenProbeSucceedsAndFailFastOn() {
        BizDbReadOnlyCheck checker = check(true, true);

        assertThatThrownBy(() -> checker.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("该连接可写");
        // 探针建出来的临时表要被清掉
        verify(jdbc).execute("DROP TEMPORARY TABLE IF EXISTS jiwei_ro_probe");
    }

    @Test
    void shouldOnlyWarnWhenProbeSucceedsButFailFastOff() {
        assertThatCode(() -> check(true, false).run(null)).doesNotThrowAnyException();
        verify(jdbc).execute("DROP TEMPORARY TABLE IF EXISTS jiwei_ro_probe");
    }

    // ---------- 开关 ----------

    @Test
    void shouldSkipProbeWhenDisabled() {
        check(false, true).run(null);

        verify(jdbc, never()).execute(anyString());
    }
}
