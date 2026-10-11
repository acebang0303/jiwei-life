package com.jiwei.trade.websocket;

import lombok.Builder;
import lombok.Data;

/**
 * WebSocket 通知消息模型。
 */
@Data
@Builder
public class NotifyMessage {
    private String id;          // 消息ID（雪花）
    private String userId;      // 目标用户（定向），null 表示广播
    private boolean broadcast;  // 是否广播
    private String type;        // 消息类型（新订单/催单/接单）
    private String content;     // 消息体（JSON）
}
