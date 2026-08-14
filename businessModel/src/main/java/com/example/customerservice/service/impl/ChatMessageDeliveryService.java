package com.example.customerservice.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.customerservice.constant.ChatConstants;
import com.example.customerservice.constant.ChatMessageType;
import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.domain.ChatMessage;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.dto.MessageMutationResult;
import com.example.customerservice.dto.MessageReadResult;
import com.example.customerservice.dto.ChatMessageDTO;
import com.example.customerservice.exception.BusinessStateException;
import com.example.customerservice.dto.ChatHistoryPage;
import com.example.customerservice.mapper.ChatMessageMapper;
import com.example.customerservice.mapper.ChatMessageReadMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.ChatMessageDeliveryOperations;
import com.example.customerservice.service.ChatOfflineMessageOperations;
import com.example.customerservice.service.MessagePersistService;
import com.example.customerservice.util.ChatMessageContentValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.DataType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ChatMessageDeliveryService implements ChatMessageDeliveryOperations {

    private final ChatRedisRepository chatRedisRepository;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatMessageReadMapper chatMessageReadMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessagePersistService messagePersistService;
    private final ObjectMapper objectMapper;
    private final ChatOfflineMessageOperations offlineMessageOperations;
    private final long messageRecallWindowSeconds;
    private final long messageEditWindowSeconds;

    public ChatMessageDeliveryService(
            ChatRedisRepository chatRedisRepository,
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            ChatMessageReadMapper chatMessageReadMapper,
            SimpMessagingTemplate messagingTemplate,
            MessagePersistService messagePersistService,
            ObjectMapper objectMapper,
            ChatOfflineMessageOperations offlineMessageOperations,
            long messageRecallWindowSeconds,
            long messageEditWindowSeconds
    ) {
        this.chatRedisRepository = chatRedisRepository;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.chatMessageReadMapper = chatMessageReadMapper;
        this.messagingTemplate = messagingTemplate;
        this.messagePersistService = messagePersistService;
        this.objectMapper = objectMapper;
        this.offlineMessageOperations = offlineMessageOperations;
        this.messageRecallWindowSeconds = Math.max(1L, messageRecallWindowSeconds);
        this.messageEditWindowSeconds = Math.max(1L, messageEditWindowSeconds);
    }
    @Override
    public int handleMessage(ChatMessage message) {
        if (
                message.getSessionId() == null ||
                        message.getSessionId().isBlank()
        ) {
            throw new IllegalArgumentException(
                    "sessionId不能为空"
            );
        }


        if (
                message.getSenderId() == null ||
                        message.getSenderId().isBlank()
        ) {
            throw new IllegalArgumentException(
                    "senderId不能为空"
            );
        }


        if (
                message.getClientMsgId() == null ||
                        message.getClientMsgId().isBlank()
        ) {
            throw new IllegalArgumentException(
                    "clientMsgId不能为空"
            );
        }


        if (
                message.getContent() == null ||
                        message.getContent().isBlank()
        ) {
            throw new IllegalArgumentException(
                    "消息内容不能为空"
            );
        }

        ChatMessageType messageType =
                ChatMessageContentValidator.validate(
                        message.getType(),
                        message.getContent()
                );
        message.setType(messageType.name());
        message.setId(UUID.randomUUID().toString());
        String dedupKey =
                RedisConstants.CLIENT_MSG_DEDUP
                        + message.getSessionId()
                        + ":"
                        + message.getSenderId()
                        + ":"
                        + message.getClientMsgId();


        Boolean firstSend =
                chatRedisRepository.setValueIfAbsent(
                                 dedupKey,
                                 message.getId(),
                                24,
                                TimeUnit.HOURS
                        );


        if (!Boolean.TRUE.equals(firstSend)) {

            String existingMessageId = chatRedisRepository.getValue(dedupKey);
            ChatMessage existingMessage = loadExistingMessage(existingMessageId, message);
            ChatMessageDTO duplicateAcknowledgement = ChatMessageDTO.fromEntity(existingMessage);
            duplicateAcknowledgement.setAckStatus("DUPLICATE");
            messagingTemplate.convertAndSendToUser(
                    message.getSenderId(),
                    "/queue/chat",
                    duplicateAcknowledgement
            );

            log.info(
                    "检测到重复消息，clientMsgId："
                            + message.getClientMsgId()
            );

            return 0;
        }

        String operationLockToken = chatRedisRepository.acquireSessionOperationLock(message.getSessionId());
        if (operationLockToken == null) {
            chatRedisRepository.delete(dedupKey);
            throw new BusinessStateException("会话正在转接、结束或执行超时处理，请稍后重试");
        }
        try {
        ChatSession session =
                chatSessionMapper.selectById(
                        message.getSessionId()
                );


        if (session == null) {

            // 当前消息处理失败，删除刚才写入的去重Key
            chatRedisRepository.delete(dedupKey);

            throw new IllegalArgumentException(
                    "聊天会话不存在"
            );
        }


        if (!ChatConstants.SESSION_STATUS_ACTIVE.equals(session.getStatus())) {

            chatRedisRepository.delete(dedupKey);

            throw new IllegalArgumentException(
                    "聊天会话已经结束"
            );
        }


        /*
         * 判断发送者是不是会话参与者，
         *    同时确定发送者角色
         */
        if (
                message.getSenderId()
                        .equals(session.getUserId())
        ) {

            message.setSenderRole("USER");

        } else if (
                message.getSenderId()
                        .equals(session.getAgentId())
        ) {

            message.setSenderRole("AGENT");

        } else {

            chatRedisRepository.delete(dedupKey);

            throw new IllegalArgumentException(
                    "当前用户不属于这个聊天会话"
            );
        }
        message.setCreateTime(
                LocalDateTime.now()
        );
        chatRedisRepository.sortedSetAdd(
                RedisConstants.SESSION_LAST_ACTIVITY,
                message.getSessionId(),
                System.currentTimeMillis()
        );


        /*
         * 在提交异步任务前先记录待落库状态；应用崩溃后由定时任务补偿。
         */
        messagePersistService.markPending(
                message
        );
        cacheMessage(message);


        ChatMessageDTO receivedAcknowledgement =
                ChatMessageDTO.fromEntity(message);
        receivedAcknowledgement.setAckStatus("RECEIVED");
        messagingTemplate.convertAndSendToUser(
                message.getSenderId(),
                "/queue/chat",
                receivedAcknowledgement
        );
        routeAndPush(message);
        messagePersistService.persistMessageAsync(
                message
        );


        log.info(
                "消息处理完成，messageId："
                        + message.getId()
        );


        return 1;
        } finally {
            chatRedisRepository.releaseSessionOperationLock(
                    message.getSessionId(),
                    operationLockToken
            );
        }
    }
    /**
     * 把消息放入Redis热缓存
     */
    @Override
    public void cacheMessage(
            ChatMessage message
    ) {

        String messageKey =
                RedisConstants.SESSION_MSG
                        + message.getSessionId();


        final String messageJson;

        try {

            /*
             * 保存完整ChatMessage：
             *
             * id
             * sessionId
             * senderId
             * senderRole
             * type
             * content
             * clientMsgId
             * createTime
             */
            messageJson =
                    objectMapper.writeValueAsString(
                            message
                    );

        } catch (Exception e) {

            throw new IllegalStateException(
                    "消息序列化失败，messageId："
                            + message.getId(),
                    e
            );
        }


        /*
         * 使用rightPush保证消息按产生顺序排列。
         */
        chatRedisRepository.listRightPush(
                        messageKey,
                        messageJson
                );


        /*
         * 只保留最后200条。
         *
         * -200到-1表示列表末尾最近200条。
         */
        chatRedisRepository.listTrim(
                        messageKey,
                        -RedisConstants
                                .SESSION_MESSAGE_LIMIT,
                        -1
                );
    }
    /**
     * 保存尚未确认的消息
     * 无论接收者当前在线还是离线，
     * 消息都会先进入这个列表。
     * 收到客户端ACK以后再删除。
     */
    private String cacheOfflineMessage(
            String receiverId,
            ChatMessage message
    ) {
        return offlineMessageOperations.cacheForReceiver(receiverId, message);
    }

    private ChatMessage loadExistingMessage(
            String existingMessageId,
            ChatMessage fallbackMessage
    ) {
        if (existingMessageId == null || existingMessageId.isBlank()) {
            return fallbackMessage;
        }

        try {
            ChatMessage storedMessage = chatMessageMapper.selectById(existingMessageId);
            if (storedMessage != null) {
                return storedMessage;
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "查询重复消息的数据库记录失败，继续读取待落库记录，messageId={}",
                    existingMessageId,
                    exception
            );
        }

        String pendingPayload = chatRedisRepository.getValue(
                RedisConstants.PERSIST_PENDING_PAYLOAD + existingMessageId
        );
        if (pendingPayload != null && !pendingPayload.isBlank()) {
            try {
                return objectMapper.readValue(pendingPayload, ChatMessage.class);
            } catch (Exception exception) {
                log.warn(
                        "重复消息的待落库记录解析失败，messageId={}",
                        existingMessageId,
                        exception
                );
            }
        }

        fallbackMessage.setId(existingMessageId);
        return fallbackMessage;
    }
    @Override
    public void routeAndPush(
            ChatMessage message
    ) {
        ChatSession session =
                chatSessionMapper.selectById(
                        message.getSessionId()
                );


        if (session == null) {

            throw new IllegalArgumentException(
                    "聊天会话不存在"
            );
        }
        String receiverId;


        if (
                message.getSenderId()
                        .equals(
                                session.getUserId()
                        )
        ) {

            receiverId =
                    session.getAgentId();

        } else if (
                message.getSenderId()
                        .equals(
                                session.getAgentId()
                        )
        ) {

            receiverId =
                    session.getUserId();

        } else {

            throw new IllegalArgumentException(
                    "发送者不属于这个聊天会话"
            );
        }


        /*
         * 消息先进入待确认列表
         *
         * 只有收到客户端ACK后才删除。
         */
        cacheOfflineMessage(
                receiverId,
                message
        );
        String wsSessionId =
                chatRedisRepository.getValue(
                        RedisConstants.USER_WS
                                + receiverId
                );


        /*
         * 接收者离线
         *
         * 消息已经保存在Redis中，
         * 等待用户上线后拉取。
         */
        if (
                wsSessionId == null ||
                        wsSessionId.isBlank()
        ) {

            log.info(
                    "接收者当前离线，消息等待上线拉取："
                            + receiverId
            );


            return;
        }
        messagingTemplate.convertAndSendToUser(
                receiverId,
                "/queue/chat",
                ChatMessageDTO.fromEntity(
                        message
                )
        );


        log.info(
                "消息已实时推送，等待ACK，接收者："
                        + receiverId
        );
    }
    /**
     * 拉取当前用户的离线消息

     * 拉取当前用户尚未确认的消息
     */
    @Override
    public void pullOfflineMessages(
            String userId
    ) {
        offlineMessageOperations.pullOfflineMessages(userId);
    }
    /**
     * 处理客户端业务ACK
     */
    @Override
    public void handleAck(
            String messageId,
            String receiverId
    ) {
        offlineMessageOperations.handleAck(messageId, receiverId);
    }

}
