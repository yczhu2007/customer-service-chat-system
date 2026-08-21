package com.example.customerservice.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatConstantsTest {

    @Test
    void sessionCategoryValidationAllowsNullAndKnownCategoriesOnly() {
        assertTrue(ChatConstants.isValidSessionCategory(null));
        assertTrue(ChatConstants.isValidSessionCategory(ChatConstants.CATEGORY_ACCOUNT));
        assertTrue(ChatConstants.isValidSessionCategory(ChatConstants.CATEGORY_PAYMENT));
        assertTrue(ChatConstants.isValidSessionCategory(ChatConstants.CATEGORY_TECHNICAL));
        assertTrue(ChatConstants.isValidSessionCategory(ChatConstants.CATEGORY_AFTER_SALES));
        assertTrue(ChatConstants.isValidSessionCategory(ChatConstants.CATEGORY_OTHER));
        assertFalse(ChatConstants.isValidSessionCategory("UNKNOWN"));
    }
}
