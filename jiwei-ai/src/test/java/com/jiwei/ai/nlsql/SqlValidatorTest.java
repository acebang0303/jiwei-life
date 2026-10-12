package com.jiwei.ai.nlsql;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SqlValidatorTest {

    private final SqlValidator validator = new SqlValidator();

    private static final Set<String> MERCHANT_TABLES = Set.of(
            "jiwei_merchant.dish", "jiwei_merchant.category",
            "jiwei_trade.orders", "jiwei_trade.order_detail",
            "jiwei_social.review", "jiwei_user.user");

    // ---------- 基础回归 ----------

    @Test
    void shouldRejectMultipleStatements() {
        assertThat(validator.validate("SELECT id FROM a; SELECT id FROM b")).isFalse();
    }

    @Test
    void shouldAcceptSemicolonInsideStringLiteral() {
        assertThat(validator.validate("SELECT id, name FROM merchant WHERE remark = 'a;b'")).isTrue();
    }

    @Test
    void shouldAcceptPlainSelect() {
        assertThat(validator.validate("SELECT id, name FROM merchant WHERE id = 1")).isTrue();
    }

    // ---------- 黑名单：跨行 SQL 不能绕过写关键字检查 ----------

    @Test
    void shouldRejectForbiddenKeywordAcrossNewlines() {
        // '.' 默认不匹配换行；修复前这条多行 SQL 会让整个关键字黑名单静默失效
        String sql = "SELECT id\nFROM jiwei_merchant.dish\nDELETE FROM jiwei_merchant.dish";
        assertThat(validator.validate(sql, MERCHANT_TABLES)).isFalse();
    }

    @Test
    void shouldRejectForbiddenKeywordSplitByNewlinesAndCase() {
        assertThat(validator.validate("SELECT 1\nFrOm jiwei_merchant.dish\n where 1=1 drOp table x"))
                .isFalse();
    }

    // ---------- 表白名单：反引号 / 逗号连接都不能绕过 ----------

    @Test
    void shouldRejectBacktickQualifiedTableOutsideWhitelist() {
        // 修复前：extractTables 的 [a-z0-9_]+ 匹配不到反引号，白名单被整段跳过
        String sql = "SELECT * FROM `jiwei_auth`.`employee` WHERE username = 'admin'";
        assertThat(validator.validate(sql, MERCHANT_TABLES)).isFalse();
    }

    @Test
    void shouldRejectCommaJoinedTableOutsideWhitelist() {
        String sql = "SELECT * FROM jiwei_merchant.dish d, jiwei_auth.employee e WHERE d.id = e.id";
        assertThat(validator.validate(sql, MERCHANT_TABLES)).isFalse();
    }

    @Test
    void shouldRejectQualifiedIdentifierOutsideWhitelist() {
        // 不经过 FROM 关键字、直接在列表里引用别的库
        String sql = "SELECT jiwei_auth.employee.password FROM jiwei_merchant.dish";
        assertThat(validator.validate(sql, MERCHANT_TABLES)).isFalse();
    }

    @Test
    void shouldAcceptWhitelistedJoinWithAliasesAndQualifiedColumns() {
        String sql = """
                SELECT d.name, SUM(d.sales) AS total
                FROM jiwei_merchant.dish d
                JOIN jiwei_merchant.category c ON d.category_id = c.id
                WHERE d.merchant_id = 5
                GROUP BY c.name
                ORDER BY total DESC
                """;
        assertThat(validator.validate(sql, MERCHANT_TABLES)).isTrue();
    }

    @Test
    void shouldAcceptThreePartColumnReference() {
        String sql = "SELECT jiwei_trade.orders.amount FROM jiwei_trade.orders WHERE merchant_id = 5";
        assertThat(validator.validate(sql, MERCHANT_TABLES)).isTrue();
    }

    @Test
    void shouldAcceptDerivedTableAlias() {
        String sql = """
                SELECT t.total FROM (
                    SELECT COUNT(*) AS total FROM jiwei_trade.orders WHERE merchant_id = 5
                ) t
                """;
        assertThat(validator.validate(sql, MERCHANT_TABLES)).isTrue();
    }

    @Test
    void shouldAcceptCteQuery() {
        String sql = """
                WITH recent AS (
                    SELECT id, pay_amount FROM jiwei_trade.orders WHERE merchant_id = 5
                )
                SELECT COUNT(*) FROM recent
                """;
        assertThat(validator.validate(sql, MERCHANT_TABLES)).isTrue();
    }

    @Test
    void shouldNotRestrictTablesWhenWhitelistAbsent() {
        assertThat(validator.validate("SELECT id FROM anything_else")).isTrue();
    }

    // ---------- 注入模式 / 系统表 ----------

    @Test
    void shouldRejectUnionInjection() {
        assertThat(validator.validate("SELECT id FROM jiwei_merchant.dish UNION SELECT password FROM x"))
                .isFalse();
    }

    @Test
    void shouldRejectSystemSchemaAccess() {
        assertThat(validator.validate("SELECT table_name FROM information_schema.tables")).isFalse();
    }

    @Test
    void shouldRejectCommentInjection() {
        assertThat(validator.validate("SELECT id FROM jiwei_merchant.dish -- and 1=1")).isFalse();
    }

    // ---------- 加固：LIMIT 必须真的落到执行语句上 ----------

    @Test
    void shouldAppendLimitWhenHardening() {
        String hardened = validator.validateAndHarden(
                "SELECT id FROM jiwei_merchant.dish", MERCHANT_TABLES);
        assertThat(hardened).endsWith("LIMIT 1000");
    }

    @Test
    void shouldNotAppendLimitWhenAlreadyPresent() {
        String original = "SELECT id FROM jiwei_merchant.dish LIMIT 5";
        assertThat(validator.validateAndHarden(original, MERCHANT_TABLES)).isEqualTo(original);
    }

    @Test
    void shouldReturnNullWhenHardeningUnsafeSql() {
        assertThat(validator.validateAndHarden("DROP TABLE jiwei_merchant.dish", MERCHANT_TABLES)).isNull();
    }
}
