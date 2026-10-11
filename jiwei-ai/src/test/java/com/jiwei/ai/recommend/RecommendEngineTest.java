package com.jiwei.ai.recommend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * getUserPreferenceText 对同一个 varargs 方法 queryForList(String, Object...)
 * 按不同实参个数做了多个 when；strict stubbing 下（CI 环境已复现）会抛
 * PotentialStubbingProblem 把实现里的 catch 触发、返回空串。这里用 LENIENT
 * 关闭 strict 检查，让两个 stub 按参数正常匹配（本地/CI 行为一致）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendEngineTest {

    @Mock(name = "bizJdbcTemplate")
    private JdbcTemplate bizJdbcTemplate;

    @InjectMocks
    private RecommendEngine recommendEngine;

    @Test
    void getUserPreferenceTextJoinsTags() {
        // 实现先查行为关联店铺名（双参 union SQL），再查已有偏好标签
        // 1) 店铺名查询返回空（无行为关联）
        when(bizJdbcTemplate.queryForList(anyString(), eq(1L), eq(1L)))
                .thenReturn(Collections.emptyList());
        // 2) 偏好标签查询返回该用户的 preference_tags
        Map<String, Object> row = new HashMap<>();
        row.put("preference_tags", "[\"火锅\",\"川菜\",\"深夜食堂\"]");
        when(bizJdbcTemplate.queryForList(anyString(), eq(1L)))
                .thenReturn(List.of(row));

        String text = recommendEngine.getUserPreferenceText(1L);

        assertEquals("火锅 川菜 深夜食堂", text);
    }

    @Test
    void getUserPreferenceTextReturnsEmptyWhenMissing() {
        // 店铺名查询返回空 + 标签查询返回空行 → 结果为空字符串
        when(bizJdbcTemplate.queryForList(anyString(), eq(2L), eq(2L)))
                .thenReturn(Collections.emptyList());
        when(bizJdbcTemplate.queryForList(anyString(), eq(2L)))
                .thenReturn(Collections.emptyList());

        assertEquals("", recommendEngine.getUserPreferenceText(2L));
    }
}
