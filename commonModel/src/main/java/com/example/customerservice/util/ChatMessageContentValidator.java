package com.example.customerservice.util;

import com.example.customerservice.constant.ChatMessageType;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 按消息类型校验 content 的业务格式。
 */
public final class ChatMessageContentValidator {

    private ChatMessageContentValidator() {
    }

    public static ChatMessageType validate(
            String rawType,
            String content
    ) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }

        ChatMessageType messageType =
                ChatMessageType.from(rawType);

        if (messageType == ChatMessageType.IMAGE
                || messageType == ChatMessageType.FILE) {
            validateHttpResourceUrl(messageType, content);
        }

        return messageType;
    }

    private static void validateHttpResourceUrl(
            ChatMessageType messageType,
            String content
    ) {
        if (!content.equals(content.trim())) {
            throw new IllegalArgumentException(
                    messageType.name() + " 消息地址不能包含首尾空格"
            );
        }

        try {
            URI uri = new URI(content);
            String scheme = uri.getScheme();
            boolean supportedScheme =
                    "http".equalsIgnoreCase(scheme)
                            || "https".equalsIgnoreCase(scheme);
            if (!supportedScheme
                    || uri.getHost() == null
                    || uri.getHost().isBlank()) {
                throw invalidResourceUrl(messageType);
            }
        } catch (URISyntaxException exception) {
            throw invalidResourceUrl(messageType);
        }
    }

    private static IllegalArgumentException invalidResourceUrl(
            ChatMessageType messageType
    ) {
        return new IllegalArgumentException(
                messageType.name()
                        + " 消息内容必须是合法的 http/https 地址"
        );
    }
}
