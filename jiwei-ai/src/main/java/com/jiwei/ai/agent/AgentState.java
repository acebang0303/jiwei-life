package com.jiwei.ai.agent;

/**
 * Agent 状态机。
 * IDLE → THINKING/EXECUTING → FINISHED/ERROR
 */
public enum AgentState {
    IDLE,       // 空闲
    THINKING,   // 思考中
    EXECUTING,  // 执行中
    FINISHED,   // 正常结束
    ERROR       // 错误结束
}
