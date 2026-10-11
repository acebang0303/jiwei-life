package com.jiwei.ai.service;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MongoDB 对话历史服务
 * 存储 AI Agent 的完整对话记录，支持上下文续接
 */
@Service
@Slf4j
public class ConversationService {

    private static final String COLLECTION = "conversations";

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * 创建新对话
     *
     * @param userId 用户ID
     * @param agentType Agent类型: EXPLORE/MERCHANT/AUDIT
     * @return 对话ID
     */
    public String createConversation(Long userId, String agentType) {
        String conversationId = "conv_" + System.currentTimeMillis();

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("_id", conversationId);
        doc.put("userId", userId);
        doc.put("agentType", agentType);
        doc.put("messages", new ArrayList<>());
        doc.put("createdAt", LocalDateTime.now());
        doc.put("updatedAt", LocalDateTime.now());

        mongoTemplate.insert(doc, COLLECTION);
        log.info("创建对话: convId={}, userId={}, agentType={}", conversationId, userId, agentType);
        return conversationId;
    }

    /**
     * 追加消息到对话历史
     *
     * @param conversationId 对话ID
     * @param role 角色: user / assistant / system
     * @param content 消息内容
     */
    public void appendMessage(String conversationId, String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        message.put("timestamp", LocalDateTime.now());

        Query query = new Query(Criteria.where("_id").is(conversationId));
        Update update = new Update()
                .push("messages", message)
                .set("updatedAt", LocalDateTime.now());

        mongoTemplate.updateFirst(query, update, COLLECTION);
    }

    /**
     * 追加消息（含工具调用信息）
     *
     * @param conversationId 对话ID
     * @param role 角色
     * @param content 消息内容
     * @param toolCalls 工具调用信息
     */
    public void appendMessage(String conversationId, String role, String content,
                               List<Map<String, Object>> toolCalls) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        message.put("toolCalls", toolCalls != null ? toolCalls : Collections.emptyList());
        message.put("timestamp", LocalDateTime.now());

        Query query = new Query(Criteria.where("_id").is(conversationId));
        Update update = new Update()
                .push("messages", message)
                .set("updatedAt", LocalDateTime.now());

        mongoTemplate.updateFirst(query, update, COLLECTION);
    }

    /**
     * 获取对话详情
     *
     * @param conversationId 对话ID
     * @return 对话文档
     */
    public Map<String, Object> getConversation(String conversationId) {
        Query query = new Query(Criteria.where("_id").is(conversationId));
        Map<String, Object> result = mongoTemplate.findOne(query, Map.class, COLLECTION);
        return result;
    }

    /**
     * 获取对话历史消息列表（最近 N 轮）
     *
     * @param conversationId 对话ID
     * @param recentRounds 最近 N 轮对话
     * @return 消息列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getRecentMessages(String conversationId, int recentRounds) {
        Map<String, Object> conv = getConversation(conversationId);
        if (conv == null || !conv.containsKey("messages")) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> messages = (List<Map<String, Object>>) conv.get("messages");
        int total = messages.size();

        // 取最近 N*2 条消息（每轮对话有 user + assistant 两条）
        int fromIndex = Math.max(0, total - (recentRounds * 2));
        return messages.subList(fromIndex, total);
    }

    /**
     * 获取用户的所有对话列表（按 userId + agentType 双条件隔离，
     * 避免商家/管理端/用户端同一数字 ID 撞号导致会话串扰）
     *
     * @param userId 用户ID
     * @param agentType Agent类型（可空，空则只看 userId）
     * @return 对话列表（按更新时间倒序）
     */
    @SuppressWarnings("unchecked")
    public List<Map> listConversations(Long userId, String agentType) {
        Criteria criteria = Criteria.where("userId").is(userId);
        if (agentType != null && !agentType.isBlank()) {
            criteria.and("agentType").is(agentType.toUpperCase());
        }
        Query query = new Query(criteria);
        query.fields()
                .include("_id")
                .include("agentType")
                .include("summary")
                .include("createdAt")
                .include("updatedAt");
        // 按更新时间倒序
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"));

        List<Map> result = mongoTemplate.find(query, Map.class, COLLECTION);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 更新对话摘要
     *
     * @param conversationId 对话ID
     * @param summary 摘要内容
     */
    public void updateSummary(String conversationId, String summary) {
        Query query = new Query(Criteria.where("_id").is(conversationId));
        Update update = new Update().set("summary", summary);
        mongoTemplate.updateFirst(query, update, COLLECTION);
    }

    /**
     * 删除对话
     *
     * @param conversationId 对话ID
     */
    public void deleteConversation(String conversationId) {
        Query query = new Query(Criteria.where("_id").is(conversationId));
        mongoTemplate.remove(query, COLLECTION);
        log.info("删除对话: convId={}", conversationId);
    }
}
