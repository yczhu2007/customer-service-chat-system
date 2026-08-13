package com.example.customerservice.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.customerservice.constant.ChatConstants;
import com.example.customerservice.constant.ChatMessageType;
import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.domain.ChatMessage;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.dto.ChatHistoryPage;
import com.example.customerservice.dto.ChatMessageDTO;
import com.example.customerservice.dto.MessageMutationResult;
import com.example.customerservice.dto.MessageReadResult;
import com.example.customerservice.mapper.ChatMessageMapper;
import com.example.customerservice.mapper.ChatMessageReadMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.ChatMessageManagementOperations;
import com.example.customerservice.util.ChatMessageContentValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class ChatMessageManagementService implements ChatMessageManagementOperations {

    private final ChatRedisRepository chatRedisRepository;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatMessageReadMapper chatMessageReadMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final long messageRecallWindowSeconds;
    private final long messageEditWindowSeconds;

    public ChatMessageManagementService(
            ChatRedisRepository chatRedisRepository,
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            ChatMessageReadMapper chatMessageReadMapper,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper,
            long messageRecallWindowSeconds,
            long messageEditWindowSeconds
    ) {
        this.chatRedisRepository = chatRedisRepository;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.chatMessageReadMapper = chatMessageReadMapper;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.messageRecallWindowSeconds = Math.max(1L, messageRecallWindowSeconds);
        this.messageEditWindowSeconds = Math.max(1L, messageEditWindowSeconds);
    }

    @Override
    @Transactional
    public MessageReadResult markMessagesRead(
            String sessionId,
            String lastReadMessageId,
            String readerId
    ) {
        ChatSession session = requireSessionParticipant(sessionId, readerId);
        ChatMessage anchorMessage = chatMessageMapper.selectById(lastReadMessageId);
        if (anchorMessage == null
                || !sessionId.equals(anchorMessage.getSessionId())) {
            throw new IllegalArgumentException("最后已读消息不存在或尚未完成持久化");
        }
        if (readerId.equals(anchorMessage.getSenderId())) {
            throw new IllegalArgumentException("最后已读消息必须由会话对方发送");
        }

        int markedCount = chatMessageReadMapper.markReadThrough(
                sessionId,
                readerId,
                lastReadMessageId,
                LocalDateTime.now()
        );
        long unreadCount = chatMessageReadMapper.countUnread(
                sessionId,
                readerId
        );
        MessageReadResult result = MessageReadResult.completed(
                sessionId,
                readerId,
                lastReadMessageId,
                markedCount,
                unreadCount
        );

        String counterpartId = readerId.equals(session.getUserId())
                ? session.getAgentId()
                : session.getUserId();
        if (counterpartId != null && !counterpartId.isBlank()) {
            messagingTemplate.convertAndSendToUser(
                    counterpartId,
                    "/queue/messages",
                    result
            );
        }
        return result;
    }

    @Override
    public long countUnreadMessages(
            String sessionId,
            String userId
    ) {
        requireSessionParticipant(sessionId, userId);
        return chatMessageReadMapper.countUnread(sessionId, userId);
    }

    @Override
    public MessageMutationResult editMessage(
            String messageId,
            String newContent,
            String operatorId
    ) {
        ChatMessageContentValidator.validate(ChatMessageType.TEXT.name(), newContent);
        ChatMessage message = requireMutableOwnMessage(messageId, operatorId);
        if (!ChatMessageType.TEXT.name().equalsIgnoreCase(message.getType())) {
            throw new IllegalArgumentException("只有文本消息支持编辑");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusSeconds(messageEditWindowSeconds);
        if (message.getCreateTime().isBefore(cutoff)) {
            throw new IllegalArgumentException("消息已超过允许编辑的时间");
        }

        String lockToken = chatRedisRepository.acquireSessionOperationLock(message.getSessionId());
        if (lockToken == null) {
            throw new IllegalStateException("当前会话正在执行其他操作，请稍后重试");
        }
        try {
            int updated = chatMessageMapper.editOwnMessage(
                    messageId,
                    operatorId,
                    newContent,
                    now,
                    cutoff
            );
            if (updated != 1) {
                throw new IllegalStateException("消息状态已发生变化，请刷新后重试");
            }
            if (message.getOriginalContent() == null) {
                message.setOriginalContent(message.getContent());
            }
            message.setContent(newContent);
            message.setEdited(true);
            message.setEditedAt(now);
            synchronizeMutatedMessageCache(message);
            touchSessionActivity(message.getSessionId());

            MessageMutationResult result = MessageMutationResult.edited(message);
            notifyMessageMutationCounterpart(message, result);
            return result;
        } finally {
            chatRedisRepository.releaseSessionOperationLock(message.getSessionId(), lockToken);
        }
    }

    @Override
    public MessageMutationResult recallMessage(
            String messageId,
            String operatorId
    ) {
        ChatMessage message = requireMutableOwnMessage(messageId, operatorId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusSeconds(messageRecallWindowSeconds);
        if (message.getCreateTime().isBefore(cutoff)) {
            throw new IllegalArgumentException("消息已超过允许撤回的时间");
        }

        String lockToken = chatRedisRepository.acquireSessionOperationLock(message.getSessionId());
        if (lockToken == null) {
            throw new IllegalStateException("当前会话正在执行其他操作，请稍后重试");
        }
        try {
            int updated = chatMessageMapper.recallOwnMessage(
                    messageId,
                    operatorId,
                    now,
                    cutoff
            );
            if (updated != 1) {
                throw new IllegalStateException("消息状态已发生变化，请刷新后重试");
            }
            message.setRecalled(true);
            message.setRecalledAt(now);
            synchronizeMutatedMessageCache(message);
            touchSessionActivity(message.getSessionId());

            MessageMutationResult result = MessageMutationResult.recalled(message);
            notifyMessageMutationCounterpart(message, result);
            return result;
        } finally {
            chatRedisRepository.releaseSessionOperationLock(message.getSessionId(), lockToken);
        }
    }

    private ChatMessage requireMutableOwnMessage(
            String messageId,
            String operatorId
    ) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId不能为空");
        }
        if (operatorId == null || operatorId.isBlank()) {
            throw new IllegalArgumentException("当前用户不能为空");
        }
        ChatMessage message = chatMessageMapper.selectById(messageId);
        if (message == null) {
            throw new IllegalArgumentException("消息不存在或尚未完成持久化");
        }
        requireSessionParticipant(message.getSessionId(), operatorId);
        if (!operatorId.equals(message.getSenderId())) {
            throw new IllegalArgumentException("只能编辑或撤回自己发送的消息");
        }
        if (Boolean.TRUE.equals(message.getRecalled())) {
            throw new IllegalArgumentException("消息已经撤回");
        }
        if (message.getCreateTime() == null) {
            throw new IllegalStateException("消息创建时间不存在");
        }
        return message;
    }

    private void notifyMessageMutationCounterpart(
            ChatMessage message,
            MessageMutationResult result
    ) {
        ChatSession session = chatSessionMapper.selectById(message.getSessionId());
        if (session == null) {
            return;
        }
        String counterpartId = message.getSenderId().equals(session.getUserId())
                ? session.getAgentId()
                : session.getUserId();
        if (counterpartId != null && !counterpartId.isBlank()) {
            messagingTemplate.convertAndSendToUser(
                    counterpartId,
                    "/queue/messages",
                    result
            );
        }
    }

    private void synchronizeMutatedMessageCache(ChatMessage message) {
        final String messageJson;
        try {
            messageJson = objectMapper.writeValueAsString(message);
        } catch (Exception exception) {
            throw new IllegalStateException("更新消息缓存时序列化失败", exception);
        }
        replaceMessageInRedisList(
                RedisConstants.SESSION_MSG + message.getSessionId(),
                message.getId(),
                messageJson
        );

        ChatSession session = chatSessionMapper.selectById(message.getSessionId());
        if (session == null) {
            return;
        }
        String receiverId = message.getSenderId().equals(session.getUserId())
                ? session.getAgentId()
                : session.getUserId();
        if (receiverId != null && !receiverId.isBlank()) {
            replaceMessageInRedisList(
                    RedisConstants.OFFLINE_MSG + receiverId,
                    message.getId(),
                    messageJson
            );
        }
    }

    private void replaceMessageInRedisList(
            String key,
            String messageId,
            String replacementJson
    ) {
        List<String> cachedMessages = chatRedisRepository.listRange(key, 0, -1);
        if (cachedMessages == null || cachedMessages.isEmpty()) {
            return;
        }
        for (int index = 0; index < cachedMessages.size(); index++) {
            try {
                ChatMessage cached = objectMapper.readValue(
                        cachedMessages.get(index),
                        ChatMessage.class
                );
                if (messageId.equals(cached.getId())) {
                    chatRedisRepository.listSet(key, index, replacementJson);
                    return;
                }
            } catch (Exception exception) {
                log.warn("跳过无法解析的消息缓存，key={}，index={}", key, index);
            }
        }
    }

    private void touchSessionActivity(String sessionId) {
        chatRedisRepository.sortedSetAdd(
                RedisConstants.SESSION_LAST_ACTIVITY,
                sessionId,
                System.currentTimeMillis()
        );
    }

    private ChatSession requireSessionParticipant(
            String sessionId,
            String userId
    ) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId不能为空");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("当前用户不能为空");
        }
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("聊天会话不存在");
        }
        if (!userId.equals(session.getUserId())
                && !userId.equals(session.getAgentId())) {
            throw new IllegalArgumentException("当前用户不属于这个聊天会话");
        }
        return session;
    }

    @Override
    public ChatHistoryPage getHistory(
            String sessionId,
            String operatorId,
            String beforeMessageId,
            int pageSize
    ) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId不能为空");
        }
        if (operatorId == null || operatorId.isBlank()) {
            throw new IllegalArgumentException("当前查询用户不能为空");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("每页数量必须在1到100之间");
        }

        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("聊天会话不存在");
        }
        boolean isUser = operatorId.equals(session.getUserId());
        boolean isAgent = operatorId.equals(session.getAgentId());
        if (!isUser && !isAgent) {
            throw new IllegalArgumentException("当前用户无权查看这个会话的聊天记录");
        }

        ChatMessage cursorMessage = null;
        if (beforeMessageId != null && !beforeMessageId.isBlank()) {
            cursorMessage = chatMessageMapper.selectById(beforeMessageId);
            if (cursorMessage == null
                    || cursorMessage.getCreateTime() == null
                    || !sessionId.equals(cursorMessage.getSessionId())) {
                throw new IllegalArgumentException("历史消息游标无效");
            }
        }

        List<ChatMessage> queriedMessages = loadHistoryPage(
                sessionId,
                cursorMessage,
                pageSize + 1
        );
        boolean hasMore = queriedMessages.size() > pageSize;
        List<ChatMessage> records = new ArrayList<>(
                queriedMessages.subList(0, Math.min(pageSize, queriedMessages.size()))
        );
        String nextCursor = hasMore && !records.isEmpty()
                ? records.get(records.size() - 1).getId()
                : null;
        Collections.reverse(records);
        long total = chatMessageMapper.selectCount(
                Wrappers.<ChatMessage>lambdaQuery()
                        .eq(ChatMessage::getSessionId, sessionId)
        );

        log.info(
                "聊天历史查询成功，sessionId={}，查询者={}，游标={}，本次消息数量={}",
                sessionId,
                operatorId,
                beforeMessageId,
                records.size()
        );
        return new ChatHistoryPage(
                records,
                total,
                pageSize,
                nextCursor,
                hasMore,
                chatMessageReadMapper.countUnread(sessionId, operatorId)
        );
    }

    private List<ChatMessage> loadHistoryPage(
            String sessionId,
            ChatMessage cursorMessage,
            int limit
    ) {
        if (cursorMessage == null) {
            return chatMessageMapper.selectLatestHistory(sessionId, limit);
        }

        List<ChatMessage> messages = new ArrayList<>(
                chatMessageMapper.selectHistoryAtCursorTime(
                        sessionId,
                        cursorMessage.getCreateTime(),
                        cursorMessage.getId(),
                        limit
                )
        );
        int remaining = limit - messages.size();
        if (remaining > 0) {
            messages.addAll(chatMessageMapper.selectHistoryBeforeTime(
                    sessionId,
                    cursorMessage.getCreateTime(),
                    remaining
            ));
        }
        return messages;
    }
}
