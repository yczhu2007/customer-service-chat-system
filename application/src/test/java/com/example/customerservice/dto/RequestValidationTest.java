package com.example.customerservice.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class RequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void rejectsBlankAckMessageId() {
        AckRequest request = new AckRequest();
        request.setMessageId(" ");

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsBlankEndSessionId() {
        EndSessionRequest request = new EndSessionRequest();
        request.setSessionId(" ");

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsMissingHistorySessionId() {
        HistoryRequest request = new HistoryRequest();

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsOverlongChatContent() {
        ChatMessageDTO request = new ChatMessageDTO();
        request.setSessionId("S001");
        request.setType("TEXT");
        request.setClientMsgId("C001");
        request.setContent("x".repeat(4001));

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsMissingTransferTargetAgent() {
        TransferSessionRequest request = new TransferSessionRequest();
        request.setSessionId("S001");

        assertFalse(validator.validate(request).isEmpty());
    }
}
