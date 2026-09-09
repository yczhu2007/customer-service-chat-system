package com.example.customerservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** STOMP 输入状态消息。 */
public record TypingRequest(
        @NotBlank(message = "sessionId不能为空")
        @Size(max = 64, message = "sessionId长度不能超过64个字符")
        String sessionId,
        boolean typing
) {
}
