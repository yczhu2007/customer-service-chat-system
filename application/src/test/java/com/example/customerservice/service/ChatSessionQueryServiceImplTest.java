package com.example.customerservice.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.customerservice.constant.SessionParticipantType;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.domain.ChatSessionRating;
import com.example.customerservice.dto.SessionRatingDTO;
import com.example.customerservice.dto.SessionRatingVO;
import com.example.customerservice.mapper.ChatMessageReadMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.ChatSessionRatingMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.impl.ChatSessionQueryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionQueryServiceImplTest {

    @Mock private ChatSessionMapper sessionMapper;
    @Mock private ChatSessionRatingMapper ratingMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private ChatMessageReadMapper messageReadMapper;
    @Mock private ChatRedisRepository chatRedisRepository;

    private ChatSessionQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChatSessionQueryServiceImpl(
                sessionMapper,
                ratingMapper,
                userMapper,
                messageReadMapper,
                chatRedisRepository,
                300L
        );
    }

    @Test
    void userScopeUsesOnlyUserPredicateWithoutCrossScopeLeakage() {
        when(sessionMapper.selectPage(any(Page.class), any()))
                .thenReturn(new Page<ChatSession>(1, 20));

        service.findMySessions("U001", SessionParticipantType.USER, null, null, 1, 20);

        String sqlSegment = captureSqlSegment();
        assertTrue(sqlSegment.contains("user_id"));
        assertTrue(!sqlSegment.contains("agent_id"));
        assertTrue(!sqlSegment.contains(" or "));
    }

    @Test
    void agentScopeUsesOnlyAgentPredicateWithoutCrossScopeLeakage() {
        when(sessionMapper.selectPage(any(Page.class), any()))
                .thenReturn(new Page<ChatSession>(1, 20));

        service.findMySessions("A001", SessionParticipantType.AGENT, null, null, 1, 20);

        String sqlSegment = captureSqlSegment();
        assertTrue(sqlSegment.contains("agent_id"));
        assertTrue(!sqlSegment.contains("user_id"));
        assertTrue(!sqlSegment.contains(" or "));
    }

    @Test
    void invalidParticipantTypeIsRejectedBeforeQueryingDatabase() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findMySessions("U001", null, null, null, 1, 20)
        );

        verifyNoInteractions(sessionMapper, messageReadMapper);
    }

    @Test
    void concurrentDuplicateRatingReturnsPersistedRatingIdempotently() {
        ChatSession session = new ChatSession();
        session.setId("S001");
        session.setUserId("U001");
        session.setStatus("CLOSED");
        ChatSessionRating persisted = new ChatSessionRating();
        persisted.setSessionId("S001");
        persisted.setUserId("U001");
        persisted.setRating(5);
        persisted.setComment("已保存");
        when(sessionMapper.selectById("S001")).thenReturn(session);
        when(ratingMapper.insert(any(ChatSessionRating.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(ratingMapper.selectById("S001")).thenReturn(persisted);
        SessionRatingDTO request = new SessionRatingDTO();
        request.setRating(1);
        request.setComment("重试请求");

        SessionRatingVO result = service.rateSession("U001", "S001", request);

        assertEquals(5, result.getRating());
        assertEquals("已保存", result.getComment());
        verify(ratingMapper).selectById("S001");
    }

    @Test
    void legacyConstructorThrowsControlledExceptionForMetadataTagAccess() {
        ChatSession session = new ChatSession();
        session.setId("S001");
        session.setUserId("U001");
        session.setAgentId("A001");
        when(sessionMapper.selectById("S001")).thenReturn(session);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.getSessionMetadata("U001", false, "S001")
        );

        assertEquals(
                "Metadata operations require the ChatSessionTagMapper dependency",
                exception.getMessage()
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String captureSqlSegment() {
        ArgumentCaptor<Wrapper> wrapperCaptor =
                ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(sessionMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        return wrapperCaptor.getValue().getSqlSegment().toLowerCase();
    }
}
