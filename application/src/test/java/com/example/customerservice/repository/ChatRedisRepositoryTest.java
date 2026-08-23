package com.example.customerservice.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatRedisRepositoryTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void renewLockUsesOwnerTokenAndConfiguredTtl() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);
        ChatRedisRepository repository = new ChatRedisRepository(template);

        assertTrue(repository.renewLock("session:operation:lock:S001", "owner-a", 30));

        verify(template).execute(
                any(RedisScript.class),
                org.mockito.ArgumentMatchers.eq(List.of("session:operation:lock:S001")),
                org.mockito.ArgumentMatchers.eq("owner-a"),
                org.mockito.ArgumentMatchers.eq("30")
        );
    }

    @Test
    void renewLockRejectsExpiredOrInvalidLeaseArguments() {
        ChatRedisRepository repository = new ChatRedisRepository(mock(StringRedisTemplate.class));

        assertFalse(repository.renewLock("key", "token", 0));
        assertFalse(repository.renewLock("key", null, 30));
    }
}
