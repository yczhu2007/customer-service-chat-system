package com.example.customerservice.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordUtilSecurityTest {

    @Test
    void plaintextDatabasePasswordIsNeverAccepted() {
        assertFalse(PasswordUtil.matches("testpass123", "testpass123"));
    }

    @Test
    void pbkdf2PasswordCanBeVerified() {
        String storedPassword = PasswordUtil.hash("testpass123");

        assertTrue(PasswordUtil.matches("testpass123", storedPassword));
        assertFalse(PasswordUtil.matches("wrong-password", storedPassword));
    }
}
