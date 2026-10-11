package com.jiwei.ai.agent;

import com.jiwei.ai.sse.AgentEventSink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 商家经营助手：解析 empId → merchantId 后，委托 AgentRuntimeFactory
 * 组装「queryBusinessData(Text2SQL) 工具」的 JChatMind 运行时实例。
 */
@Component
@Slf4j
public class MerchantAgent {

    private final AgentRuntimeFactory factory;
    private final JdbcTemplate bizJdbcTemplate;

    public MerchantAgent(AgentRuntimeFactory factory,
                         @Qualifier("bizJdbcTemplate") JdbcTemplate bizJdbcTemplate) {
        this.factory = factory;
        this.bizJdbcTemplate = bizJdbcTemplate;
    }

    public Long resolveMerchantId(Long empId) {
        try {
            List<Map<String, Object>> rows = bizJdbcTemplate.queryForList(
                    "SELECT id FROM jiwei_merchant.merchant_info WHERE emp_id = ?", empId);
            if (rows == null || rows.isEmpty()) {
                log.warn("未找到 emp_id={} 对应的商户", empId);
                return null;
            }
            return ((Number) rows.get(0).get("id")).longValue();
        } catch (Exception e) {
            log.warn("解析 merchantId 失败: empId={}, err={}", empId, e.getMessage());
            return null;
        }
    }

    public JChatMind execute(String model, String sessionId, String question, Long empId) {
        Long merchantId = resolveMerchantId(empId);
        if (merchantId == null) {
            return null;
        }
        return factory.createMerchant(model, sessionId, merchantId, question);
    }

    public JChatMind execute(String model, String sessionId, String question, Long empId,
                             List<Message> history, AgentEventSink sink) {
        Long merchantId = resolveMerchantId(empId);
        if (merchantId == null) {
            return null;
        }
        return factory.createMerchant(model, sessionId, merchantId, question, history, sink);
    }
}
