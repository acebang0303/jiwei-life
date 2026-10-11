package com.jiwei.ai.nlsql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlValidatorTest {

    private final SqlValidator validator = new SqlValidator();

    @Test
    void shouldRejectMultipleStatements() {
        // 两个独立 SELECT：当前实现无分号计数会误放行（本次要补的漏洞）
        assertThat(validator.validate("SELECT id FROM a; SELECT id FROM b")).isFalse();
    }

    @Test
    void shouldAcceptSemicolonInsideStringLiteral() {
        // 分号在字符串字面量内，非语句分隔符，不应误判为多语句（字符串剥离的回归守卫）
        assertThat(validator.validate("SELECT id, name FROM merchant WHERE remark = 'a;b'")).isTrue();
    }

    @Test
    void shouldAcceptPlainSelect() {
        assertThat(validator.validate("SELECT id, name FROM merchant WHERE id = 1")).isTrue();
    }
}
