package com.example.customerservice.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ChatSecurityConstantsTest {
    @Test
    void controllersDoNotPassStringLiteralsToRoleOrPermissionChecks() throws Exception {
        Path controllers = Path.of("src/main/java/com/example/customerservice/controller");
        try (var files = Files.walk(controllers)) {
            String source = files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try { return Files.readString(path); }
                        catch (Exception exception) { throw new IllegalStateException(exception); }
                    })
                    .reduce("", String::concat);
            assertFalse(source.matches("(?s).*require(Role|Permission)\\(\\s*\\\"[^\\\"]+\\\"\\s*\\).*"));
        }
    }
}
