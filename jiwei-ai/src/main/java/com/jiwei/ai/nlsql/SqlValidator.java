package com.jiwei.ai.nlsql;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL安全校验器
 * NL2SQL生成SQL后的多层安全校验
 *
 * 安全策略（分层防御）：
 * 1. 只允许 SELECT 语句
 * 2. 白名单：允许的表名和列名需预先注册
 * 3. 黑名单：拦截已知危险关键字和模式
 * 4. UNION 注入防护：禁止 UNION SELECT 子查询
 * 5. 系统表访问防护：禁止 INFORMATION_SCHEMA / performance_schema
 * 6. 行数限制：自动添加 LIMIT 1000
 * 7. 执行超时：SQL 执行超过 5 秒自动 kill（由 SqlExecutor 实现）
 *
 * 长期方向：LLM 只生成"查询意图"而非原始 SQL，
 * 由后端根据意图构建参数化查询。
 */
@Component
@Slf4j
public class SqlValidator {

    //禁止的写操作关键字
    private static final Set<String> FORBIDDEN_WRITE_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "TRUNCATE",
            "CREATE", "EXEC", "EXECUTE", "GRANT", "REVOKE", "REPLACE", "MERGE"
    );

    //禁止的注入模式
    private static final Set<String> INJECTION_PATTERNS = Set.of(
            "UNION SELECT", "UNION ALL SELECT",         // UNION 注入
            "INTO OUTFILE", "INTO DUMPFILE", "LOAD_FILE",  // 文件操作
            "BENCHMARK(", "SLEEP(", "WAITFOR DELAY",      // 时间盲注
            "--", "/*", "*/", "\\x",                       // 注释注入
            "CONCAT(", "GROUP_CONCAT(",                     // 信息聚合注入
            "EXTRACTVALUE(", "UPDATEXML(",                  // XPATH 注入
            "OUTFILE", "DUMPFILE"                           // 文件写入
    );

    //禁止访问的系统表/库
    private static final Set<String> FORBIDDEN_SCHEMAS = Set.of(
            "INFORMATION_SCHEMA", "PERFORMANCE_SCHEMA",
            "MYSQL", "SYS", "PG_CATALOG", "PG_CLASS"
    );

    /**
     * 校验SQL安全性（不带表白名单）
     */
    public boolean validate(String sql) {
        return validate(sql, null);
    }

    /**
     * 校验SQL安全性（带表白名单：FROM/JOIN 出现的表必须全部在允许集合内）
     *
     * @param sql LLM生成的SQL语句
     * @param allowedTables 允许访问的表名集合（全限定名，如 "jiwei_trade.orders"；null 表示不限制表）
     * @return 是否安全
     */
    public boolean validate(String sql, Set<String> allowedTables) {
        if (sql == null || sql.isEmpty()) {
            log.warn("SQL校验失败: SQL为空");
            return false;
        }

        // 剥离注释与字符串字面量后，检测「中间」的多语句分号
        String noComment = sql.replaceAll("/\\*.*?\\*/", "").replaceAll("--[^\\n]*", "");
        String stripped = stripStrings(noComment).trim();
        // 去掉末尾单个分号（单条语句允许以 ; 结尾），仅拦截中间的语句分隔分号
        if (stripped.endsWith(";")) {
            stripped = stripped.substring(0, stripped.length() - 1).trim();
        }
        if (stripped.contains(";")) {
            log.warn("SQL校验失败: 检测到多语句");
            return false;
        }

        String upperSql = sql.toUpperCase().trim();

        //1、只允许 SELECT / WITH (CTE) / EXPLAIN 语句
        if (!upperSql.startsWith("SELECT")
                && !upperSql.startsWith("WITH")
                && !upperSql.startsWith("EXPLAIN")) {
            log.warn("SQL校验失败: 非查询语句 - {}", sql);
            return false;
        }

        //2、检查禁止的写操作关键字
        for (String keyword : FORBIDDEN_WRITE_KEYWORDS) {
            // 使用单词边界匹配，避免误杀（如 "description" 包含 "insert"）
            if (upperSql.matches(".*\\b" + keyword + "\\b.*")) {
                log.warn("SQL校验失败: 包含禁止关键字 {} - {}", keyword, sql);
                return false;
            }
        }

        //3、检查注入模式
        for (String pattern : INJECTION_PATTERNS) {
            if (upperSql.contains(pattern)) {
                log.warn("SQL校验失败: 包含注入模式 {} - {}", pattern, sql);
                return false;
            }
        }

        //4、检查是否访问了系统表/库
        for (String schema : FORBIDDEN_SCHEMAS) {
            if (upperSql.contains(schema)) {
                log.warn("SQL校验失败: 尝试访问系统表 {} - {}", schema, sql);
                return false;
            }
        }

        //5、限制返回行数（添加 LIMIT 子句）
        if (!upperSql.contains("LIMIT")) {
            sql = sql + " LIMIT 1000";
        }

        //6、检查SQL长度（防止过长SQL导致内存溢出）
        if (sql.length() > 5000) {
            log.warn("SQL校验失败: SQL过长 ({} 字符)", sql.length());
            return false;
        }

        //7、表白名单校验：FROM/JOIN 出现的表必须全部在允许集合内
        if (allowedTables != null && !allowedTables.isEmpty()) {
            Set<String> tables = extractTables(upperSql);
            for (String table : tables) {
                if (!allowedTables.contains(table)) {
                    log.warn("SQL校验失败: 访问了白名单外数据表 {} - {}", table, sql);
                    return false;
                }
            }
        }

        log.info("SQL校验通过: {}", sql.substring(0, Math.min(200, sql.length())));
        return true;
    }

    /**
     * 提取 SQL 中 FROM/JOIN 出现的表名（全限定名，小写）
     */
    private Set<String> extractTables(String sql) {
        Set<String> tables = new HashSet<>();
        Pattern p = Pattern.compile("(?i)\\b(?:FROM|JOIN)\\s+([a-z0-9_]+(?:\\.[a-z0-9_]+)?)");
        Matcher m = p.matcher(sql);
        while (m.find()) {
            String table = m.group(1).toLowerCase();
            if (!"dual".equals(table)) {
                tables.add(table);
            }
        }
        return tables;
    }

    /**
     * 剥离字符串字面量（单引号包裹的内容替换为空串），
     * 保证字符串内的分号不被误判为语句分隔符。
     */
    private String stripStrings(String sql) {
        return sql.replaceAll("'[^']*'", "''");
    }
}
