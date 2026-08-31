package com.example.customerservice.constant;

import java.util.Locale;

/**
 * 坐席固定会话视图。
 */
public enum AgentSessionView {
    MY_ACTIVE("MY_ACTIVE", "我的处理中"),
    MY_TICKETS("MY_TICKETS", "我的工单"),
    MY_PARTICIPATED_TICKETS("MY_PARTICIPATED_TICKETS", "我参与过的工单"),
    MY_UNREAD("MY_UNREAD", "我的未读"),
    MY_HIGH_PRIORITY("MY_HIGH_PRIORITY", "我的高优先级"),
    MY_UNARCHIVED("MY_UNARCHIVED", "我的未归档"),
    MY_RECENT_CLOSED("MY_RECENT_CLOSED", "我最近关闭"),
    MY_ARCHIVED_COMPLETED("MY_ARCHIVED_COMPLETED", "我的已解决"),
    MY_ARCHIVED_PENDING("MY_ARCHIVED_PENDING", "我的待处理"),
    MY_ARCHIVED_ON_HOLD("MY_ARCHIVED_ON_HOLD", "我的暂停"),
    MY_ARCHIVED_OTHER("MY_ARCHIVED_OTHER", "我的其他");

    private final String code;
    private final String label;

    AgentSessionView(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static AgentSessionView fromCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("坐席会话视图不能为空");
        }
        try {
            return valueOf(rawCode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "坐席会话视图不支持该类型"
            );
        }
    }
}
