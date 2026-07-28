package com.example.customerservice.util;

import com.example.customerservice.constant.ChatMessageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatMessageContentValidatorTest {

    @Test
    void textMessageAcceptsPlainTextAndNormalizesType() {
        ChatMessageType type =
                ChatMessageContentValidator.validate(
                        "text",
                        "您好，请问有什么可以帮助您？"
                );

        assertEquals(ChatMessageType.TEXT, type);
    }

    @Test
    void imageAndFileMessagesAcceptHttpResources() {
        assertEquals(
                ChatMessageType.IMAGE,
                ChatMessageContentValidator.validate(
                        "IMAGE",
                        "https://cdn.example.com/images/example.png"
                )
        );
        assertEquals(
                ChatMessageType.FILE,
                ChatMessageContentValidator.validate(
                        "FILE",
                        "http://files.example.com/download?id=100"
                )
        );
    }

    @Test
    void rejectsUnsupportedMessageType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChatMessageContentValidator.validate(
                        "VIDEO",
                        "https://cdn.example.com/video.mp4"
                )
        );
    }

    @Test
    void rejectsNonHttpImageOrFileContent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChatMessageContentValidator.validate(
                        "IMAGE",
                        "not-a-url"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ChatMessageContentValidator.validate(
                        "FILE",
                        "ftp://files.example.com/example.pdf"
                )
        );
    }
}
