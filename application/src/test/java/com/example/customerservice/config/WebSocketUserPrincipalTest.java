package com.example.customerservice.config;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WebSocketUserPrincipalTest {

    @Test
    void retainsIdentityAndRolesWithoutStoringAccessToken() {
        WebSocketUserPrincipal principal = new WebSocketUserPrincipal("U001", Set.of("USER"));

        assertEquals("U001", principal.getName());
        assertEquals(Set.of("USER"), principal.getRoleCodes());
        assertFalse(principal.toString().contains("token"));
    }
}
