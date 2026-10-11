package com.jiwei.ai.controller;

import com.jiwei.ai.config.ChatClientRegistry;
import com.jiwei.ai.recommend.RecommendEngine;
import com.jiwei.ai.service.ConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * AI 推荐与辅助接口
 */
@RestController
@RequestMapping("/ai")
@Slf4j
public class AiRecommendController {

    @Autowired
    private RecommendEngine recommendEngine;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ChatClientRegistry registry;

    /**
     * AI个性化菜品推荐
     */
    @GetMapping("/recommend/dish")
    public Object recommendDish(@RequestParam Long userId,
                                 @RequestParam(defaultValue = "10") Integer topN) {
        log.info("AI推荐: userId={}, topN={}", userId, topN);
        return recommendEngine.recommend(userId, topN);
    }

    /**
     * AI 辅助写评价
     */
    @PostMapping("/review/assist")
    public Object reviewAssist(@RequestBody String orderContext) {
        log.info("AI辅助写评价: {}", orderContext);
        String prompt = """
                请根据以下订单信息，生成一段 50 字左右真实、亲切的美食评价。
                要求：语气自然，不要模板化、不要夸张，可以提到口味、分量、配送等实际体验点。

                订单信息：
                """ + orderContext;
        ChatClient client = registry.get("deepseek");
        return client.prompt().user(prompt).call().content();
    }

    /**
     * 对话历史列表（按 agentType 隔离：管理端/商家端/用户端互不可见）
     */
    @GetMapping("/conversation/list")
    public Object conversationList(@RequestParam Long userId,
                                   @RequestParam(required = false) String agentType) {
        return conversationService.listConversations(userId, agentType);
    }

    /**
     * 对话详情
     */
    @GetMapping("/conversation/{id}")
    public Object conversationDetail(@PathVariable String id) {
        return conversationService.getConversation(id);
    }

    /**
     * 删除对话
     */
    @DeleteMapping("/conversation/{id}")
    public Object deleteConversation(@PathVariable String id) {
        conversationService.deleteConversation(id);
        return "ok";
    }
}
