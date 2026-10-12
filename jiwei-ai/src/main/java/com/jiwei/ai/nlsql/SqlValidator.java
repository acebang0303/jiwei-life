package com.jiwei.ai.nlsql;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 安全校验器：NL2SQL 生成 SQL 后的多层防御。
 *
 * 分层策略：
 * 1. 只允许 SELECT / WITH(CTE) / EXPLAIN 开头
 * 2. 多语句检测（剥离注释与字符串字面量后，中间分号即拒绝）
 * 3. 黑名单：写操作关键字（DOTALL 单词边界匹配，换行也不能绕过）
 * 4. 黑名单：注入模式（UNION SELECT / INTO OUTFILE / 时间盲注 / 注释注入 ...）
 * 5. 系统表禁止（INFORMATION_SCHEMA / MYSQL / PG_CATALOG ...）
 * 6. 表白名单：FROM/JOIN/逗号连接引用的表 + 一切 schema 限定标识符都必须命中白名单
 * 7. 长度限制 + 强制 LIMIT（validateAndHarden 阶段补齐）
 *
 * 长期方向：LLM 只产出「查询意图」，由后端按意图拼参数化查询，彻底消灭文本层风险。
 */
@Component
@Slf4j
public class SqlValidator {

    /** 禁止的写操作关键字（DOTALL + 单词边界，避免 "description" 误伤） */
    private static final Set<String> FORBIDDEN_WRITE_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "TRUNCATE",
            "CREATE", "EXEC", "EXECUTE", "GRANT", "REVOKE", "REPLACE", "MERGE",
            "CALL", "HANDLER", "LOAD"
    );

    /** 禁止的注入模式 */
    private static final Set<String> INJECTION_PATTERNS = Set.of(
            "UNION SELECT", "UNION ALL SELECT",              // UNION 注入
            "INTO OUTFILE", "INTO DUMPFILE", "LOAD_FILE",    // 文件读写
            "BENCHMARK(", "SLEEP(", "WAITFOR DELAY",         // 时间盲注
            "--", "/*", "*/",                                // 注释注入
            "EXTRACTVALUE(", "UPDATEXML(",                   // XPATH 报错注入
            "OUTFILE", "DUMPFILE", "LOAD DATA"               // 文件写入
    );

    /** 禁止访问的系统表/库 */
    private static final Set<String> FORBIDDEN_SCHEMAS = Set.of(
            "INFORMATION_SCHEMA", "PERFORMANCE_SCHEMA",
            "MYSQL", "SYS", "PG_CATALOG", "PG_CLASS"
    );

    /** SQL 关键字：用于把「别名」从表名里区分出来 */
    private static final Set<String> SQL_KEYWORDS = Set.of(
            "select", "from", "where", "join", "left", "right", "inner", "outer", "full", "cross",
            "on", "using", "and", "or", "not", "null", "as", "group", "by", "order", "having",
            "limit", "offset", "union", "all", "distinct", "case", "when", "then", "else", "end",
            "asc", "desc", "is", "in", "exists", "between", "like", "with", "explain", "for",
            "straight_join", "natural", "interval", "day", "month", "year", "date", "now",
            "curdate", "date_sub", "date_add", "over", "partition", "rows", "range", "unbounded",
            "preceding", "following", "current", "row", "ifnull", "coalesce", "sum", "count",
            "avg", "max", "min", "round", "concat"
    );

    /** FROM/JOIN 后紧跟的表引用（可带 schema 前缀） */
    private static final Pattern TABLE_REF =
            Pattern.compile("\\b(?:from|join)\\s+([a-z0-9_]+(?:\\.[a-z0-9_]+)*)");
    /** FROM 子句主体（到 WHERE/GROUP/ORDER/HAVING/LIMIT/ON 为止），用于解析逗号连接的表 */
    private static final Pattern FROM_CLAUSE =
            Pattern.compile("\\bfrom\\b([^()]*?)(?=\\bwhere\\b|\\bgroup\\b|\\border\\b|\\bhaving\\b"
                    + "|\\blimit\\b|\\bon\\b|\\bjoin\\b|\\bunion\\b|$)");
    /** schema 限定标识符链：a.b 或 a.b.c */
    private static final Pattern DOTTED_CHAIN =
            Pattern.compile("\\b[a-z_][a-z0-9_]*(?:\\.[a-z_][a-z0-9_]*)+\\b");
    /** 派生表别名：FROM (SELECT ...) x */
    private static final Pattern DERIVED_ALIAS =
            Pattern.compile("\\)\\s*(?:as\\s+)?([a-z_][a-z0-9_]*)");
    /** WITH 子句里定义的 CTE 名 */
    private static final Pattern CTE_NAME =
            Pattern.compile("(?:^|,|with)\\s+([a-z_][a-z0-9_]*)\\s+as\\s*\\(");

    private static final int MAX_SQL_LENGTH = 5000;
    private static final String IDENTIFIER_TOKEN = "[a-z0-9_]+(?:\\.[a-z0-9_]+)*";

    public boolean validate(String sql) {
        return validate(sql, null);
    }

    /**
     * 校验 SQL 安全性。
     *
     * @param sql           LLM 生成的 SQL
     * @param allowedTables 允许访问的表（全限定名，如 "jiwei_trade.orders"；null/空表示不限制表）
     */
    public boolean validate(String sql, Set<String> allowedTables) {
        if (sql == null || sql.isBlank()) {
            log.warn("SQL校验失败: SQL为空");
            return false;
        }
        if (sql.length() > MAX_SQL_LENGTH) {
            log.warn("SQL校验失败: SQL过长 ({} 字符)", sql.length());
            return false;
        }
        String upperSql = sql.toUpperCase(Locale.ROOT).trim();

        //1、只允许查询类语句
        if (!upperSql.startsWith("SELECT") && !upperSql.startsWith("WITH") && !upperSql.startsWith("EXPLAIN")) {
            log.warn("SQL校验失败: 非查询语句 - {}", sql);
            return false;
        }

        //2、多语句检测（末尾单个分号合法，中间分号危险）
        if (hasMultipleStatements(sql)) {
            log.warn("SQL校验失败: 检测到多语句");
            return false;
        }

        //3、写操作关键字（(?s) 必须加：'.' 默认不匹配换行，否则多行 SQL 能整段绕过黑名单）
        for (String keyword : FORBIDDEN_WRITE_KEYWORDS) {
            if (upperSql.matches("(?s).*\\b" + keyword + "\\b.*")) {
                log.warn("SQL校验失败: 包含禁止关键字 {} - {}", keyword, sql);
                return false;
            }
        }

        //4、注入模式
        for (String pattern : INJECTION_PATTERNS) {
            if (upperSql.contains(pattern)) {
                log.warn("SQL校验失败: 包含注入模式 {} - {}", pattern, sql);
                return false;
            }
        }

        //5、系统表访问
        for (String schema : FORBIDDEN_SCHEMAS) {
            if (upperSql.contains(schema)) {
                log.warn("SQL校验失败: 尝试访问系统表 {} - {}", schema, sql);
                return false;
            }
        }

        //6、表白名单
        if (allowedTables != null && !allowedTables.isEmpty()) {
            Set<String> allowed = new HashSet<>();
            allowedTables.forEach(t -> allowed.add(t.toLowerCase(Locale.ROOT)));
            for (String table : extractTables(sql, allowed)) {
                if (!allowed.contains(table)) {
                    log.warn("SQL校验失败: 访问了白名单外数据表 {} - {}", table, sql);
                    return false;
                }
            }
        }

        log.info("SQL校验通过: {}", sql.substring(0, Math.min(200, sql.length())));
        return true;
    }

    /** 校验并返回加固后的 SQL（补 LIMIT）；未通过校验返回 null。 */
    public String validateAndHarden(String sql, Set<String> allowedTables) {
        if (!validate(sql, allowedTables)) {
            return null;
        }
        if (!sql.toUpperCase(Locale.ROOT).contains("LIMIT")) {
            return sql.trim() + " LIMIT 1000";
        }
        return sql;
    }

    /**
     * 剥离注释与字符串字面量后检测「中间」分号。
     * 字符串里的分号（'a;b'）不是语句分隔符，不能误判。
     */
    private boolean hasMultipleStatements(String sql) {
        String noComment = sql.replaceAll("/\\*.*?\\*/", " ").replaceAll("--[^\\n]*", " ");
        String stripped = noComment.replaceAll("'[^']*'", "''").replaceAll("\"[^\"]*\"", "\"\"");
        String trimmed = stripped.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed.contains(";");
    }

    /**
     * 提取 SQL 引用的全部数据表（小写、schema 限定优先）。
     *
     * 覆盖三种写法，避免被绕开：
     *   1. FROM/JOIN 后的表引用（含反引号写法 `db`.`tbl`）
     *   2. FROM 子句里逗号连接的表：FROM a.dish d, other.employee e
     *   3. 任何 schema 限定标识符 a.b —— 左半既不是表别名/CTE 名，其前缀也不在白名单里时，
     *      视为越权访问。这条兜住了「不走 FROM 关键字引用别的库」的写法。
     *
     * @param allowed 已小写的白名单，用于识别 a.b.c 这类「列引用」而非「表引用」
     */
    Set<String> extractTables(String sql, Set<String> allowed) {
        String normalized = normalize(sql);
        Set<String> tables = new LinkedHashSet<>();
        Set<String> cteNames = extractCteNames(normalized);
        Set<String> aliases = new LinkedHashSet<>(cteNames);

        Matcher ref = TABLE_REF.matcher(normalized);
        while (ref.find()) {
            String name = ref.group(1);
            if (!cteNames.contains(name)) {
                tables.add(name);
            }
            addAlias(aliases, nextIdentifier(normalized, ref.end()));
        }

        Matcher fromClause = FROM_CLAUSE.matcher(normalized);
        while (fromClause.find()) {
            String clause = fromClause.group(1).trim();
            String[] parts = clause.split(",");
            for (int i = 1; i < parts.length; i++) {
                String[] tokens = parts[i].trim().split("\\s+");
                String token = tokens.length > 0 ? tokens[0] : "";
                if (token.matches(IDENTIFIER_TOKEN) && !cteNames.contains(token)) {
                    tables.add(token);
                    if (tokens.length > 1) {
                        addAlias(aliases, tokens[1]);
                    }
                }
            }
        }

        Matcher derived = DERIVED_ALIAS.matcher(normalized);
        while (derived.find()) {
            addAlias(aliases, derived.group(1));
        }

        // 兜底：任何不在别名/CTE 下、也不是白名单表前缀的 schema 限定标识符
        Matcher dotted = DOTTED_CHAIN.matcher(normalized);
        while (dotted.find()) {
            String chain = dotted.group(0);
            String head = chain.split("\\.")[0];
            if (aliases.contains(head)) {
                continue;
            }
            if (isWhitelistedChain(chain, allowed)) {
                continue;
            }
            tables.add(chain);
        }
        return tables;
    }

    /**
     * 判断 schema 限定链是否属于白名单范畴：
     *   - 链本身是白名单表（jiwei_trade.orders）
     *   - 链是白名单表的列引用（jiwei_trade.orders.amount / orders.amount）
     *   - 链的首段就是某张白名单表的名字（dish.price，用表名当限定符）
     * 其余（如 auth.employee）一律视为越权。
     */
    private boolean isWhitelistedChain(String chain, Set<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return false;
        }
        String[] parts = chain.split("\\.");
        String head = parts[0];
        boolean twoParts = parts.length == 2;
        for (String table : allowed) {
            if (table.equals(chain)
                    || chain.startsWith(table + ".")
                    || table.startsWith(chain + ".")) {
                return true;
            }
            int dot = table.lastIndexOf('.');
            String tableName = dot < 0 ? table : table.substring(dot + 1);
            if (twoParts && tableName.equals(head)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String sql) {
        return sql.replaceAll("'[^']*'", "''")
                .replace("`", "")
                .replace("\"", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Set<String> extractCteNames(String normalized) {
        Set<String> names = new LinkedHashSet<>();
        String body = normalized.startsWith("with ") ? normalized.substring(4) : normalized;
        Matcher m = CTE_NAME.matcher(body);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    private void addAlias(Set<String> aliases, String candidate) {
        if (candidate != null && !candidate.isEmpty() && !SQL_KEYWORDS.contains(candidate)) {
            aliases.add(candidate);
        }
    }

    /** 取 from 位置之后的第一个纯标识符（用作表别名）。 */
    private String nextIdentifier(String text, int from) {
        String rest = text.substring(Math.min(from, text.length())).trim();
        if (rest.isEmpty()) {
            return null;
        }
        String word = rest.split("\\s+")[0].replaceAll("[^a-z0-9_]", "");
        return word.isEmpty() ? null : word;
    }
}
