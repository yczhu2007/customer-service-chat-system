package com.example.customerservice.constant;

import java.util.Locale;

/**
 * 技术文档规定的聊天消息类型。
 */
public enum ChatMessageType {
    TEXT,
    IMAGE,
    FILE;

    public static ChatMessageType from(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            throw new IllegalArgumentException("消息类型不能为空");
        }
        try {
            return valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "消息类型只支持 TEXT、IMAGE、FILE"
            );
        }
    }
}
