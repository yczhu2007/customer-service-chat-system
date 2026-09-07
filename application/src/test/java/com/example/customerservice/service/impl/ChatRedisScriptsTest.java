package com.example.customerservice.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRedisScriptsTest {

    @Test
    void rollbackWithoutRequeueRemovesTheNormalUserDueEntry() {
        String script = ChatRedisScripts.ROLLBACK_ASSIGNMENT_SCRIPT.getScriptAsString();

        assertTrue(script.contains("ZREM', KEYS[6]"));
    }
}
