package com.jiwei.ai.controller;

import com.jiwei.ai.embedding.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * Embedding 灌入与语义搜索接口
 */
@RestController
@RequestMapping("/ai")
@Slf4j
public class EmbeddingController {

    @Autowired
    private EmbeddingService embeddingService;

    /**
     * 重建菜品向量
     */
    @PostMapping("/embedding/dish/rebuild")
    public Object rebuildDishEmbeddings() {
        log.info("触发重建菜品向量");
        return embeddingService.rebuildDishEmbeddings();
    }

    /**
     * 重建笔记向量
     */
    @PostMapping("/embedding/note/rebuild")
    public Object rebuildNoteEmbeddings() {
        log.info("触发重建笔记向量");
        return embeddingService.rebuildNoteEmbeddings();
    }

    /**
     * 菜品语义搜索
     */
    @GetMapping("/search/dish")
    public Object searchDish(@RequestParam String keyword,
                             @RequestParam(defaultValue = "10") Integer topK) {
        log.info("菜品语义搜索: keyword={}, topK={}", keyword, topK);
        return embeddingService.searchDish(keyword, topK);
    }

    /**
     * 笔记语义搜索
     */
    @GetMapping("/search/note")
    public Object searchNote(@RequestParam String keyword,
                             @RequestParam(defaultValue = "10") Integer topK) {
        log.info("笔记语义搜索: keyword={}, topK={}", keyword, topK);
        return embeddingService.searchNote(keyword, topK);
    }
}
