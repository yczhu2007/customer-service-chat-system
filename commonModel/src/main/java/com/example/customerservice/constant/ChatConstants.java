package com.example.customerservice.constant;

/**
 * 聊天会话状态、通知事件及断线原因常量。
 */
public final class ChatConstants {

    private ChatConstants() {
    }

    public static final String SESSION_STATUS_ACTIVE = "ACTIVE";
    public static final String SESSION_STATUS_CLOSED = "CLOSED";
    public static final String EVENT_SESSION_CLOSED = "SESSION_CLOSED";
    public static final String EVENT_SESSION_ENDED = "SESSION_ENDED";
    public static final String REASON_AGENT_OFFLINE = "AGENT_OFFLINE";
    public static final String REASON_WEBSOCKET_DISCONNECT = "WEBSOCKET_DISCONNECT";
    public static final String REASON_HEARTBEAT_TIMEOUT = "HEARTBEAT_TIMEOUT";
    public static final String REASON_AGENT_DISCONNECTED = "AGENT_DISCONNECTED";
    public static final String REASON_SESSION_INACTIVITY_TIMEOUT = "SESSION_INACTIVITY_TIMEOUT";
}
