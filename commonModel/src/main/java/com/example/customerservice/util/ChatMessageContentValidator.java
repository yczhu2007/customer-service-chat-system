package com.example.customerservice.util;

import com.example.customerservice.constant.ChatMessageType;


/**
 * 按消息类型校验 content 的业务格式。
 */
public final class ChatMessageContentValidator {

    /** TEXT 消息最大长度。 */
    private static final int MAX_TEXT_LENGTH = 4000;

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

        if (messageType == ChatMessageType.TEXT) {
            if (content.length() > MAX_TEXT_LENGTH) {
                throw new IllegalArgumentException("消息内容不能超过" + MAX_TEXT_LENGTH + "个字符");
            }
        }

        if (messageType == ChatMessageType.IMAGE
                || messageType == ChatMessageType.FILE) {
            validateAttachmentResourceUrl(messageType, content);
        }

        return messageType;
    }

    private static void validateAttachmentResourceUrl(
            ChatMessageType messageType,
            String content
    ) {
        if (!content.equals(content.trim())) {
            throw new IllegalArgumentException(
                    messageType.name() + " 消息地址不能包含首尾空格"
            );
        }

        if (content.matches("^/chat/attachments/[A-Za-z0-9]{32,64}/content$")) {
            return;
        }

        throw invalidResourceUrl(messageType);
    }

    private static IllegalArgumentException invalidResourceUrl(
            ChatMessageType messageType
    ) {
        return new IllegalArgumentException(
                messageType.name()
                        + " 消息内容必须是合法的附件地址"
        );
    }
}
