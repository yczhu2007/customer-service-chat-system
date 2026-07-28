package com.example.customerservice.dto;

import com.example.customerservice.domain.ChatMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;

/**
 * WebSocket聊天消息传输对象。
 *
 * 客户端发送时只需要填写sessionId、type、content和clientMsgId；
 * 其余字段由服务端生成，客户端传入的值不会被信任。
 */
public class ChatMessageDTO {

    private String id;

    @NotBlank(message = "sessionId不能为空")
    private String sessionId;

    private String senderId;

    private String senderRole;

    @NotBlank(message = "消息类型不能为空")
    @Pattern(
            regexp = "(?i)TEXT|IMAGE|FILE",
            message = "消息类型只支持 TEXT、IMAGE、FILE"
    )
    private String type;

    @NotBlank(message = "消息内容不能为空")
    private String content;

    @NotBlank(message = "clientMsgId不能为空")
    private String clientMsgId;

    private LocalDateTime createTime;

    /**
     * 服务端处理确认状态：RECEIVED 表示已进入 Redis，
     * STORED 表示已确认写入 MySQL。
     */
    private String ackStatus;

    public ChatMessage toEntity(String authenticatedSenderId) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setSenderId(authenticatedSenderId);
        message.setType(type);
        message.setContent(content);
        message.setClientMsgId(clientMsgId);
        return message;
    }

    public static ChatMessageDTO fromEntity(
            ChatMessage message
    ) {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setId(message.getId());
        dto.setSessionId(message.getSessionId());
        dto.setSenderId(message.getSenderId());
        dto.setSenderRole(message.getSenderRole());
        dto.setType(message.getType());
        dto.setContent(message.getContent());
        dto.setClientMsgId(message.getClientMsgId());
        dto.setCreateTime(message.getCreateTime());
        return dto;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getSenderRole() {
        return senderRole;
    }

    public void setSenderRole(String senderRole) {
        this.senderRole = senderRole;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }

    public void setClientMsgId(String clientMsgId) {
        this.clientMsgId = clientMsgId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getAckStatus() {
        return ackStatus;
    }

    public void setAckStatus(String ackStatus) {
        this.ackStatus = ackStatus;
    }
}
