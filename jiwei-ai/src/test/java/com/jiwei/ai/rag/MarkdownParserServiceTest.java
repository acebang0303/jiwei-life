package com.jiwei.ai.rag;

import com.jiwei.ai.rag.impl.MarkdownParserServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownParserServiceTest {

    private final MarkdownParserService parser = new MarkdownParserServiceImpl();

    @Test
    void extractSections_shouldSplitByHeading() {
        String md = """
            # 标题一
            内容一第一段。

            ## 标题二
            内容二。
            """;
        List<String> sections = parser.extractSections(md);
        assertThat(sections).hasSize(2);
        assertThat(sections.get(0)).contains("标题一").contains("内容一第一段");
        assertThat(sections.get(1)).contains("标题二").contains("内容二");
    }
}
