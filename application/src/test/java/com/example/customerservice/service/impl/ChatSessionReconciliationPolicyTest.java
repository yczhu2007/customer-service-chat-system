package com.example.customerservice.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatSessionReconciliationPolicyTest {

    @Test
    void reconciliationPolicyUsesNamedValues() throws Exception {
        assertEquals(60L, constant("ASSIGNMENT_SETTLE_SECONDS"));
        assertEquals(10L, constant("FINALIZATION_SETTLE_SECONDS"));
        assertEquals(100, constant("PENDING_BATCH_SIZE"));
    }

    private Object constant(String name) throws Exception {
        var field = ChatSessionReconciliationService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }
}
