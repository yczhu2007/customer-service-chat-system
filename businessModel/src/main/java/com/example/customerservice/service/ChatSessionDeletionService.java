package com.example.customerservice.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.customerservice.domain.ChatAttachment;
import com.example.customerservice.domain.SupportTicket;
import com.example.customerservice.mapper.ChatAttachmentMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.SupportTicketMapper;
import com.example.customerservice.storage.AttachmentObjectStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Service
@Slf4j
public class ChatSessionDeletionService {
    private final ChatSessionMapper sessionMapper;
    private final SupportTicketMapper ticketMapper;
    private final ChatAttachmentMapper attachmentMapper;
    private final AttachmentObjectStorage storage;

    public ChatSessionDeletionService(ChatSessionMapper sessionMapper, SupportTicketMapper ticketMapper,
                                      ChatAttachmentMapper attachmentMapper, AttachmentObjectStorage storage) {
        this.sessionMapper = sessionMapper;
        this.ticketMapper = ticketMapper;
        this.attachmentMapper = attachmentMapper;
        this.storage = storage;
    }

    @Transactional
    public void deleteSession(String sessionId) {
        if (sessionMapper.selectById(sessionId) == null) throw new IllegalArgumentException("会话不存在");
        List<String> storedNames = attachmentMapper.selectList(Wrappers.<ChatAttachment>lambdaQuery()
                        .select(ChatAttachment::getStoredName).eq(ChatAttachment::getSessionId, sessionId))
                .stream().map(ChatAttachment::getStoredName).toList();
        ticketMapper.delete(Wrappers.<SupportTicket>lambdaQuery().eq(SupportTicket::getSessionId, sessionId));
        if (sessionMapper.deleteById(sessionId) != 1) throw new IllegalArgumentException("会话不存在");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                storedNames.forEach(storedName -> {
                    try { storage.delete(storedName); }
                    catch (RuntimeException exception) { log.error("会话附件对象删除失败，sessionId={}, storedName={}", sessionId, storedName, exception); }
                });
            }
        });
    }
}
