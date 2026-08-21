package com.example.customerservice.service;

import com.example.customerservice.domain.ChatQuickReply;
import com.example.customerservice.dto.QuickReplySaveDTO;
import com.example.customerservice.exception.NotFoundException;
import com.example.customerservice.mapper.ChatQuickReplyMapper;
import com.example.customerservice.service.impl.QuickReplyServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class QuickReplyServiceImplTest {
    @Test
    void createsQuickReplyFromContentWithoutRequiringASeparateTitle() {
        ChatQuickReplyMapper mapper = mock(ChatQuickReplyMapper.class);
        java.util.concurrent.atomic.AtomicReference<ChatQuickReply> saved = new java.util.concurrent.atomic.AtomicReference<>();
        when(mapper.insert(any(ChatQuickReply.class))).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return 1;
        });
        when(mapper.selectById(anyString())).thenAnswer(invocation -> saved.get());
        QuickReplySaveDTO request = new QuickReplySaveDTO();
        request.setContent("您好，请问有什么可以帮您？");

        var result = new QuickReplyServiceImpl(mapper).create("A1", request);

        assertEquals("您好，请问有什么可以帮您？", result.getTitle());
        assertEquals("您好，请问有什么可以帮您？", result.getContent());
    }

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
