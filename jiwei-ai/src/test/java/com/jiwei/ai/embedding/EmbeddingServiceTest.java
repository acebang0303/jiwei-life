package com.jiwei.ai.embedding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock(name = "pgJdbcTemplate")
    private JdbcTemplate pgJdbcTemplate;

    @Mock(name = "bizJdbcTemplate")
    private JdbcTemplate bizJdbcTemplate;

    @InjectMocks
    private EmbeddingService embeddingService;

    @Test
    void syncDishEmbeddingDeletesWhenDishMissing() {
        when(bizJdbcTemplate.queryForList(anyString(), eq(1L)))
                .thenReturn(Collections.emptyList());

        embeddingService.syncDishEmbedding(1L);

        verify(pgJdbcTemplate).update(eq("DELETE FROM dish_embedding WHERE id = ?"), eq(1L));
        verify(embeddingModel, never()).embed(anyString());
    }

    @Test
    void syncDishEmbeddingUpsertsWhenDishExists() {
        Map<String, Object> dish = new HashMap<>();
        dish.put("id", 1L);
        dish.put("merchant_id", 100L);
        dish.put("name", "红烧肉");
        dish.put("description", "肥而不腻");
        dish.put("price", new BigDecimal("38.00"));
        dish.put("category_name", "家常菜");
        when(bizJdbcTemplate.queryForList(anyString(), eq(1L)))
                .thenReturn(List.of(dish));
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1.0f, 2.0f});

        embeddingService.syncDishEmbedding(1L);

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(pgJdbcTemplate).update(contains("ON CONFLICT"), argsCaptor.capture());
        Object[] args = argsCaptor.getValue();
        assertEquals("[1.0,2.0]", args[args.length - 1]);
    }
}
