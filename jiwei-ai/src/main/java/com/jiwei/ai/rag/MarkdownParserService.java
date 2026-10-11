package com.jiwei.ai.rag;

import java.util.List;

/**
 * Markdown 解析服务：按 Heading 切分文档为「标题 + 内容」段落。
 */
public interface MarkdownParserService {

    /**
     * 按 Markdown 标题切分，返回段落列表（每个元素为「标题 + 正文」）。
     */
    List<String> extractSections(String markdown);
}
