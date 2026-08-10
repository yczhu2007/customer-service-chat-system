package com.example.customerservice.dto;

import com.example.customerservice.domain.ChatSession;

/**
 * 用户接入客服时的分配结果。
 */
public class AssignResult {

    public static final String ASSIGNED = "ASSIGNED";
    public static final String RECONNECTED = "RECONNECTED";
    public static final String WAITING = "WAITING";
    public static final String PROCESSING = "PROCESSING";

    private String event;
    private String assignmentStatus;
    private String message;
    private ChatSessionDTO session;
    private Long waitingPosition;
    private Long estimatedWaitSeconds;
    private Integer vipLevel;

    public static AssignResult assigned(
            ChatSession session
    ) {
        return of(
                "SESSION_CREATED",
                ASSIGNED,
                "客服分配成功",
                ChatSessionDTO.fromEntity(
                        session,
                        "SESSION_CREATED"
                )
        );
    }

    public static AssignResult reconnected(
            ChatSession session
    ) {
        return of(
                "SESSION_RECONNECTED",
                RECONNECTED,
                "已恢复原有会话",
                ChatSessionDTO.fromEntity(
                        session,
                        "SESSION_RECONNECTED"
                )
        );
    }

    public static AssignResult waiting() {
        return waiting(null);
    }

    public static AssignResult waiting(Long waitingPosition) {
        return waiting(waitingPosition, null);
    }

    public static AssignResult waiting(
            Long waitingPosition,
            Long estimatedWaitSeconds
    ) {
        AssignResult result = of(
                "WAITING_FOR_AGENT",
                WAITING,
                "暂无可用客服，您已进入等待队列",
                null
        );
        result.setWaitingPosition(waitingPosition);
        result.setEstimatedWaitSeconds(estimatedWaitSeconds);
        return result;
    }

    public static AssignResult vipCallbackRequired(
            Long waitingPosition
    ) {
        AssignResult result = of(
                "VIP_CALLBACK_REQUIRED",
                WAITING,
                "当前客服全部离线，已为VIP用户登记优先回呼",
                null
        );
        result.setWaitingPosition(waitingPosition);
        return result;
    }

    public static AssignResult processing() {
        return of(
                "ASSIGNMENT_PROCESSING",
                PROCESSING,
                "当前接入请求正在处理中",
                null
        );
    }

    private static AssignResult of(
            String event,
            String assignmentStatus,
            String message,
            ChatSessionDTO session
    ) {
        AssignResult result = new AssignResult();
        result.setEvent(event);
        result.setAssignmentStatus(assignmentStatus);
        result.setMessage(message);
        result.setSession(session);
        return result;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getAssignmentStatus() {
        return assignmentStatus;
    }

    public void setAssignmentStatus(
            String assignmentStatus
    ) {
        this.assignmentStatus = assignmentStatus;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ChatSessionDTO getSession() {
        return session;
    }

    public void setSession(
            ChatSessionDTO session
    ) {
        this.session = session;
    }

    public Long getWaitingPosition() {
        return waitingPosition;
    }

    public void setWaitingPosition(Long waitingPosition) {
        this.waitingPosition = waitingPosition;
    }

    public Long getEstimatedWaitSeconds() {
        return estimatedWaitSeconds;
    }

    public void setEstimatedWaitSeconds(Long estimatedWaitSeconds) {
        this.estimatedWaitSeconds = estimatedWaitSeconds;
    }

    public Integer getVipLevel() {
        return vipLevel;
    }

    public void setVipLevel(Integer vipLevel) {
        this.vipLevel = vipLevel;
    }
}
