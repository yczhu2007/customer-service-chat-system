package com.example.customerservice.service;

import com.example.customerservice.constant.ChatConstants;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.domain.ChatSessionTransferLog;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.ChatSessionTransferLogMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.impl.ChatSessionTransferService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatSessionTransferServiceTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void successfulTransferUpdatesOwnershipAndPersistsAuditLogInTransaction() {
        ChatRedisRepository redisRepository = mock(ChatRedisRepository.class);
        ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
        ChatSessionTransferLogMapper transferLogMapper = mock(ChatSessionTransferLogMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

        ChatSession session = new ChatSession();
        session.setId("S001");
        session.setUserId("U001");
        session.setAgentId("A001");
        session.setStatus(ChatConstants.SESSION_STATUS_ACTIVE);
        SysUser targetAgent = new SysUser();
        targetAgent.setId("A002");

        when(redisRepository.acquireSessionOperationLock("S001")).thenReturn("LOCK");
        when(sessionMapper.selectById("S001")).thenReturn(session);
        when(userMapper.selectById("A002")).thenReturn(targetAgent);
        when(userRoleMapper.findRoleCodesByUserId("A002")).thenReturn(Set.of("AGENT"));
        when(redisRepository.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);
        when(sessionMapper.transferSession("S001", "A001", "A002")).thenReturn(1);
        when(transferLogMapper.insert(any(ChatSessionTransferLog.class))).thenReturn(1);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Integer> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        ChatSessionTransferService service = new ChatSessionTransferService(
                redisRepository,
                sessionMapper,
                transferLogMapper,
                userMapper,
                userRoleMapper,
                messagingTemplate,
                transactionTemplate,
                5
        );

        service.transferSession("S001", "A001", "A002");

        verify(sessionMapper).transferSession("S001", "A001", "A002");
        ArgumentCaptor<ChatSessionTransferLog> logCaptor =
                ArgumentCaptor.forClass(ChatSessionTransferLog.class);
        verify(transferLogMapper).insert(logCaptor.capture());
        assertEquals("S001", logCaptor.getValue().getSessionId());
        assertEquals("A001", logCaptor.getValue().getSourceAgentId());
        assertEquals("A002", logCaptor.getValue().getTargetAgentId());
        assertEquals("MANUAL_TRANSFER", logCaptor.getValue().getReason());
        verify(redisRepository).releaseSessionOperationLock("S001", "LOCK");
    }
}
