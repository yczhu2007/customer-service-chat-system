package com.example.customerservice.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceRoleResolverTest {
    private final WorkspaceRoleResolver resolver = new WorkspaceRoleResolver();

    @Test
    void resolvesRequestedRoleOwnedByUser() {
        assertEquals("AGENT", resolver.resolve("AGENT", Set.of("AGENT"), "USER", "AGENT"));
    }

    @Test
    void rejectsRoleNotOwnedByUser() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("ADMIN", Set.of("AGENT"), "USER", "AGENT", "ADMIN"));
    }

    @Test
    void fallsBackToAvailableRoleWhenHeaderMissing() {
        assertEquals("ADMIN", resolver.resolve(null, Set.of("ADMIN", "AGENT"), "USER", "AGENT", "ADMIN"));
    }
}
