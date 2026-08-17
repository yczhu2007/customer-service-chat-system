package com.example.customerservice.service;

import com.example.customerservice.domain.ChatQuickReply;
import com.example.customerservice.exception.NotFoundException;
import com.example.customerservice.mapper.ChatQuickReplyMapper;
import com.example.customerservice.service.impl.QuickReplyServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class QuickReplyServiceImplTest {
    @Test
    void agentCannotDeleteAnotherAgentsQuickReply() {
        ChatQuickReplyMapper mapper = mock(ChatQuickReplyMapper.class);
        ChatQuickReply reply = new ChatQuickReply(); reply.setId("Q1"); reply.setAgentId("A2");
        when(mapper.selectById("Q1")).thenReturn(reply);
        QuickReplyServiceImpl service = new QuickReplyServiceImpl(mapper);

        assertThrows(NotFoundException.class, () -> service.delete("A1", "Q1"));
        verify(mapper, never()).deleteById(anyString());
    }
}
