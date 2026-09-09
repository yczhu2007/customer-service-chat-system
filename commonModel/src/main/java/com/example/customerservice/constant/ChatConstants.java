package com.example.customerservice.constant;

import java.util.Set;

/**
 * 聊天会话状态、通知事件及断线原因常量。
 */
public final class ChatConstants {

    private ChatConstants() {
    }

    public static final String SESSION_STATUS_ACTIVE = "ACTIVE";
    public static final String ROLE_USER = "USER";
    public static final String ROLE_AGENT = "AGENT";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String PERMISSION_CHAT_USER_ACCESS = "chat:user:access";
    public static final String TICKET_FEEDBACK_CONFIRM = "CONFIRM";
    public static final String SESSION_STATUS_CLOSED = "CLOSED";
    public static final String EVENT_SESSION_CLOSED = "SESSION_CLOSED";
    public static final String EVENT_SESSION_ENDED = "SESSION_ENDED";
    public static final String EVENT_AGENT_RECONNECTING = "AGENT_RECONNECTING";
    public static final String REASON_AGENT_OFFLINE = "AGENT_OFFLINE";
    public static final String REASON_WEBSOCKET_DISCONNECT = "WEBSOCKET_DISCONNECT";
    public static final String REASON_HEARTBEAT_TIMEOUT = "HEARTBEAT_TIMEOUT";
    public static final String REASON_AGENT_DISCONNECTED = "AGENT_DISCONNECTED";
    public static final String REASON_SESSION_INACTIVITY_TIMEOUT = "SESSION_INACTIVITY_TIMEOUT";
    public static final String AGENT_SKILL_VIP_CODE = "VIP";
    public static final String DEFAULT_SESSION_TITLE = "新咨询";
    public static final String PRIORITY_LOW = "LOW";
    public static final String PRIORITY_NORMAL = "NORMAL";
    public static final String PRIORITY_HIGH = "HIGH";
    public static final String PRIORITY_URGENT = "URGENT";
    public static final String CATEGORY_ACCOUNT = "ACCOUNT";
    public static final String CATEGORY_PAYMENT = "PAYMENT";
    public static final String CATEGORY_TECHNICAL = "TECHNICAL";
    public static final String CATEGORY_AFTER_SALES = "AFTER_SALES";
    public static final String CATEGORY_OTHER = "OTHER";
    public static final int SESSION_TITLE_MAX_LENGTH = 100;
    public static final int SESSION_TAG_MAX_LENGTH = 32;
    public static final int SESSION_TAG_MAX_COUNT = 10;

    // ── 会话归档状态（参考 Zendesk Ticket Status） ──────────────────────────
    /** 已完成（Solved），允许 reopen 回到 PENDING/ON_HOLD。 */
    public static final String ARCHIVE_COMPLETED = "COMPLETED";
    /** 挂起·等客户回复（Pending），对应 Zendesk Pending。 */
    public static final String ARCHIVE_PENDING = "PENDING";
    /** 挂起·等内部处理（On-hold），对应 Zendesk On-hold。 */
    public static final String ARCHIVE_ON_HOLD = "ON_HOLD";
    /** 其他情况（自定义备注）。 */
    public static final String ARCHIVE_OTHER = "OTHER";

    /** 合法的归档状态枚举值。 */
    private static final Set<String> VALID_ARCHIVE_STATUSES = Set.of(
            ARCHIVE_COMPLETED, ARCHIVE_PENDING, ARCHIVE_ON_HOLD, ARCHIVE_OTHER
    );
    private static final Set<String> VALID_SESSION_PRIORITIES = Set.of(
            PRIORITY_LOW, PRIORITY_NORMAL, PRIORITY_HIGH, PRIORITY_URGENT
    );
    private static final Set<String> VALID_SESSION_CATEGORIES = Set.of(
            CATEGORY_ACCOUNT, CATEGORY_PAYMENT, CATEGORY_TECHNICAL,
            CATEGORY_AFTER_SALES, CATEGORY_OTHER
    );

    /** 判断给定值是否为合法的归档状态。 */
    public static boolean isValidArchiveStatus(String status) {
        return status != null && VALID_ARCHIVE_STATUSES.contains(status);
    }

    public static boolean isValidSessionPriority(String priority) {
        return priority != null && VALID_SESSION_PRIORITIES.contains(priority);
    }

    public static boolean isValidSessionCategory(String category) {
        return category == null || VALID_SESSION_CATEGORIES.contains(category);
    }

    /**
     * 合法的归档状态流转表（Zendesk 风格）。
     *
     * <ul>
     *   <li>未归档（null/空）→ 任意状态</li>
     *   <li>PENDING / ON_HOLD / OTHER → 任意状态（含 COMPLETED，已完成终态）</li>
     *   <li>COMPLETED → PENDING / ON_HOLD（reopen，但不允许直接改 OTHER 或再次 COMPLETED）</li>
     * </ul>
     *
     * @return true 表示允许流转
     */
    public static boolean canTransitionTo(String currentStatus, String targetStatus) {
        if (!isValidArchiveStatus(targetStatus)) {
            return false;
        }
        // 未归档：首次设置，允许任意目标
        if (currentStatus == null || currentStatus.isBlank()) {
            return true;
        }
        if (!isValidArchiveStatus(currentStatus)) {
            return false;
        }
        // COMPLETED → 只允许 reopen 到 PENDING / ON_HOLD
        if (ARCHIVE_COMPLETED.equals(currentStatus)) {
            return ARCHIVE_PENDING.equals(targetStatus)
                    || ARCHIVE_ON_HOLD.equals(targetStatus);
        }
        // PENDING / ON_HOLD / OTHER → 允许任意目标（含 COMPLETED）
        return true;
    }
}
