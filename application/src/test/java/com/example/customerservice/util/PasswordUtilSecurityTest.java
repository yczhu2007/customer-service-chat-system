package com.example.customerservice.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordUtilSecurityTest {

    @Test
    void plaintextDatabasePasswordIsNeverAccepted() {
        assertFalse(PasswordUtil.matches("123456", "123456"));
    }

    @Test
    void pbkdf2PasswordCanBeVerified() {
        String storedPassword = PasswordUtil.hash("123456");

        assertTrue(PasswordUtil.matches("123456", storedPassword));
        assertFalse(PasswordUtil.matches("wrong-password", storedPassword));
    }
}
