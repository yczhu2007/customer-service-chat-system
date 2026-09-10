package com.example.customerservice.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SharedConstantsTest {

    @Test
    void accountAndMessagingValuesHaveCanonicalConstants() throws Exception {
        assertEquals("ENABLED", value("AccountStatus", "ENABLED"));
        assertEquals("DISABLED", value("AccountStatus", "DISABLED"));
        assertEquals("/queue/chat", value("ChatDestinations", "USER_CHAT_QUEUE"));
    }

    private Object value(String type, String field) throws Exception {
        Class<?> constants = Class.forName("com.example.customerservice.constant." + type);
        return constants.getField(field).get(null);
    }
}
