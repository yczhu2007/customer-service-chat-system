package com.example.customerservice.dto;

import java.io.Serializable;
import java.util.Set;

/** Authentication information returned after a successful login. */
public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        String userId,
        String username,
        String nickname,
        Set<String> roles
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
