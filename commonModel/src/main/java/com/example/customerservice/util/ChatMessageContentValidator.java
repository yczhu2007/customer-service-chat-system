package com.example.customerservice.util;

import com.example.customerservice.constant.ChatMessageType;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * 按消息类型校验 content 的业务格式。
 */
public final class ChatMessageContentValidator {

    /** 禁止外链指向的内网/回环/云元数据地址。 */
    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost", "127.0.0.1", "0.0.0.0", "[::1]", "169.254.169.254"
    );

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

        if (content.matches("^/chat/attachments/[A-Za-z0-9]{32,64}/content$")) {
            return;
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

            /* 阻止指向内网/回环/云元数据地址的外链。 */
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (BLOCKED_HOSTS.contains(host)) {
                throw new IllegalArgumentException(
                        messageType.name() + " 消息地址不允许指向内网或回环地址"
                );
            }

            /* 进一步拦截通过 IP 字面量访问的内网地址。 */
            try {
                InetAddress addr = InetAddress.getByName(host);
                if (addr.isLoopbackAddress()
                        || addr.isSiteLocalAddress()
                        || addr.isLinkLocalAddress()
                        || addr.isAnyLocalAddress()) {
                    throw new IllegalArgumentException(
                            messageType.name() + " 消息地址不允许指向内网或回环地址"
                    );
                }
            } catch (java.net.UnknownHostException ignored) {
                /* 主机名不是 IP 字面量（如 cdn.example.com），放行。 */
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
                        + " 消息内容必须是合法的附件地址或http/https地址"
        );
    }
}
