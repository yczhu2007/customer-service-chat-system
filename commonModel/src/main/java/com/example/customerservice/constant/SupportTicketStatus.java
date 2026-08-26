package com.example.customerservice.constant;

public enum SupportTicketStatus {
    OPEN,
    IN_PROGRESS,
    WAITING_USER,
    RESOLVED;

    public boolean canTransitionTo(SupportTicketStatus target) {
        return switch (this) {
            case OPEN -> target == IN_PROGRESS || target == WAITING_USER || target == RESOLVED;
            case IN_PROGRESS -> target == WAITING_USER || target == RESOLVED;
            case WAITING_USER -> target == IN_PROGRESS || target == RESOLVED;
            case RESOLVED -> target == IN_PROGRESS;
        };
    }
}
