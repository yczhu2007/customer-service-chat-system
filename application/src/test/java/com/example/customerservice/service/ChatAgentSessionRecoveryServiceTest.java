package com.example.customerservice.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.impl.ChatAgentSessionRecoveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAgentSessionRecoveryServiceTest {

    @Mock private ChatRedisRepository chatRedisRepository;
    @Mock private ChatSessionMapper chatSessionMapper;

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(
                        new MybatisConfiguration(),
                        "ChatAgentSessionRecoveryServiceTest"
                ),
                ChatSession.class
        );
    }

    @Test
    void restoresIndexedSessionsWithBatchedInQueries() {
        Set<String> indexedIds = new LinkedHashSet<>(
                IntStream.rangeClosed(1, 21)
                        .mapToObj(index -> "S" + index)
                        .toList()
        );
        when(chatRedisRepository.setMembers(
                RedisConstants.agentSessionsKey("A001")
        )).thenReturn(indexedIds);
        when(chatSessionMapper.selectList(any()))
                .thenReturn(sessions(1, 10))
                .thenReturn(sessions(11, 20))
                .thenReturn(List.of());

        ChatAgentSessionRecoveryService service =
                new ChatAgentSessionRecoveryService(
                        chatRedisRepository,
                        chatSessionMapper,
                        10
                );

        List<ChatSession> result = service.restoreActiveSessions("A001");

        assertEquals(20, result.size());
        verify(chatSessionMapper, times(3)).selectList(any());
        verify(chatRedisRepository).setRemove(
                eq(RedisConstants.agentSessionsKey("A001")),
                eq("S21")
        );
        verify(chatRedisRepository).setAdd(
                eq(RedisConstants.agentSessionsKey("A001")),
                org.mockito.ArgumentMatchers.any(String[].class)
        );
    }

    private static List<ChatSession> sessions(int start, int end) {
        return IntStream.rangeClosed(start, end)
                .mapToObj(index -> {
                    ChatSession session = new ChatSession();
                    session.setId("S" + index);
                    session.setAgentId("A001");
                    session.setStatus("ACTIVE");
                    session.setCreateTime(LocalDateTime.now().plusSeconds(index));
                    return session;
                })
                .toList();
    }
}
