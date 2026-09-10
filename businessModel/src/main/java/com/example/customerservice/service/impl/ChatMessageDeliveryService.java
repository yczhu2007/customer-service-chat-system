package com.example.customerservice.service.impl;

import static com.example.customerservice.constant.ChatDestinations.USER_CHAT_QUEUE;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.customerservice.constant.ChatConstants;
import com.example.customerservice.constant.ChatMessageType;
import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.constant.RoleCodes;
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
import com.example.customerservice.mapper.ChatAttachmentMapper;
import com.example.customerservice.domain.ChatAttachment;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.ChatMessageDeliveryOperations;
import com.example.customerservice.service.ChatOfflineMessageOperations;
import com.example.customerservice.service.MessagePersistService;
import com.example.customerservice.util.ChatMessageContentValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.DataType;
import org.springframework.core.task.TaskRejectedException;
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
import java.util.concurrent.ScheduledFuture;

@Slf4j
public class ChatMessageDeliveryService implements ChatMessageDeliveryOperations {

    private final ChatRedisRepository chatRedisRepository;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatMessageReadMapper chatMessageReadMapper;
    private final ChatAttachmentMapper chatAttachmentMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessagePersistService messagePersistService;
    private final ObjectMapper objectMapper;
    private final ChatOfflineMessageOperations offlineMessageOperations;
    private final long messageRecallWindowSeconds;
    private final long messageEditWindowSeconds;
    private final long messageRateLimitMax;
    private final long messageRateLimitWindowSeconds;

    public ChatMessageDeliveryService(
            ChatRedisRepository chatRedisRepository,
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            ChatMessageReadMapper chatMessageReadMapper,
            ChatAttachmentMapper chatAttachmentMapper,
            SimpMessagingTemplate messagingTemplate,
            MessagePersistService messagePersistService,
            ObjectMapper objectMapper,
            ChatOfflineMessageOperations offlineMessageOperations,
            long messageRecallWindowSeconds,
            long messageEditWindowSeconds,
            long messageRateLimitMax,
            long messageRateLimitWindowSeconds
    ) {
        this.chatRedisRepository = chatRedisRepository;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.chatMessageReadMapper = chatMessageReadMapper;
        this.chatAttachmentMapper = chatAttachmentMapper;
        this.messagingTemplate = messagingTemplate;
        this.messagePersistService = messagePersistService;
        this.objectMapper = objectMapper;
        this.offlineMessageOperations = offlineMessageOperations;
        this.messageRecallWindowSeconds = Math.max(1L, messageRecallWindowSeconds);
        this.messageEditWindowSeconds = Math.max(1L, messageEditWindowSeconds);
        this.messageRateLimitMax = Math.max(1L, messageRateLimitMax);
        this.messageRateLimitWindowSeconds = Math.max(1L, messageRateLimitWindowSeconds);
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

        /* Redis 原子限流。 */
        String rateKey = RedisConstants.MSG_RATE_LIMIT + message.getSenderId();
        Long msgCount = null;
        try {
            // 使用Lua脚本原子执行INCR和条件EXPIRE，避免竞态条件
            // 失败策略：Redis不可用时采用fail-open（允许消息通过），仅记录警告日志
            msgCount = chatRedisRepository.incrementAndExpire(rateKey, messageRateLimitWindowSeconds, TimeUnit.SECONDS);
        } catch (RuntimeException redisException) {
            log.warn("Redis消息限流检查失败，采用fail-open策略放行消息，senderId={}", message.getSenderId(), redisException);
        }
        if (msgCount != null && msgCount > messageRateLimitMax) {
            throw new BusinessStateException("消息发送过于频繁，请稍后重试");
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
                    USER_CHAT_QUEUE,
                    duplicateAcknowledgement
            );

            log.info(
                    "检测到重复消息，clientMsgId：{}",
                    message.getClientMsgId()
            );

            return 0;
        }

        String operationLockToken;
        try {
            operationLockToken = chatRedisRepository.acquireSessionOperationLock(message.getSessionId());
        } catch (RuntimeException exception) {
            deleteDeduplicationKeySafely(dedupKey);
            throw exception;
        }
        if (operationLockToken == null) {
            deleteDeduplicationKeySafely(dedupKey);
            throw new BusinessStateException("会话正在转接、结束或执行超时处理，请稍后重试");
        }
        ScheduledFuture<?> operationLockRenewal = chatRedisRepository.startLockRenewal(
                RedisConstants.SESSION_OPERATION_LOCK + message.getSessionId(),
                operationLockToken,
                RedisConstants.SESSION_OPERATION_LOCK_TTL_SECONDS,
                TimeUnit.SECONDS
        );
        ChatSession session;
        try {
            session = chatSessionMapper.selectById(
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

            message.setSenderRole(RoleCodes.USER);

        } else if (
                message.getSenderId()
                        .equals(session.getAgentId())
        ) {

            message.setSenderRole(RoleCodes.AGENT);

        } else {

            chatRedisRepository.delete(dedupKey);

            throw new IllegalArgumentException(
                    "当前用户不属于这个聊天会话"
            );
        }
        validateReplyTarget(message, session.getId());
        validateAttachmentOwnership(messageType, message.getContent(), session.getId());
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


        } catch (RuntimeException exception) {
            deleteDeduplicationKeySafely(dedupKey);
            throw exception;
        } finally {
            chatRedisRepository.stopLockRenewal(operationLockRenewal);
            chatRedisRepository.releaseSessionOperationLock(
                    message.getSessionId(),
                    operationLockToken
            );
        }

        /*
         * WebSocket 推送移出会话操作锁：
         * 发送是阻塞调用，客户端连接缓慢或断开时会拉长持锁时间，
         * 阻塞同会话的转接、结束和超时处理。
         *
         * 推送失败不再向外抛出、也不删除去重Key：
         * 消息已进入待确认列表，客户端可通过离线拉取获得；
         * 若因推送失败删除去重Key，客户端重试会生成新messageId，
         * 与待落库补偿叠加产生重复消息。
         */
        ChatMessageDTO receivedAcknowledgement =
                ChatMessageDTO.fromEntity(message);
        receivedAcknowledgement.setAckStatus("RECEIVED");
        sendToUserSafely(
                message.getSenderId(),
                receivedAcknowledgement,
                "RECEIVED回执"
        );
        routeAndPush(message, session);
        try {
            messagePersistService.persistMessageAsync(message);
        } catch (TaskRejectedException exception) {
            log.warn("消息已接收，异步落库队列繁忙，将由补偿任务处理，messageId={}", message.getId());
        }


        log.info(
                "消息处理完成，messageId：{}",
                message.getId()
        );


        return 1;
    }

    private void sendToUserSafely(
            String userId,
            Object payload,
            String description
    ) {
        try {
            messagingTemplate.convertAndSendToUser(
                    userId,
                    USER_CHAT_QUEUE,
                    payload
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "WebSocket推送失败（{}），接收者：{}",
                    description,
                    userId,
                    exception
            );
        }
    }

    private void deleteDeduplicationKeySafely(String dedupKey) {
        try {
            chatRedisRepository.delete(dedupKey);
        } catch (RuntimeException cleanupException) {
            log.warn("清理消息去重Key失败，dedupKey={}", dedupKey, cleanupException);
        }
    }

    private void validateReplyTarget(ChatMessage message, String sessionId) {
        String replyToMessageId = message.getReplyToMessageId();
        if (replyToMessageId == null || replyToMessageId.isBlank()) {
            message.setReplyToMessageId(null);
            return;
        }
        ChatMessage target = chatMessageMapper.selectById(replyToMessageId);
        if (target == null || !sessionId.equals(target.getSessionId())) {
            throw new IllegalArgumentException("引用消息不存在或不属于当前会话");
        }
        if (Boolean.TRUE.equals(target.getRecalled())) {
            throw new IllegalArgumentException("不能引用已撤回的消息");
        }
        message.setReplyPreview(target.getContent() == null || target.getContent().isBlank() ? "附件消息" : target.getContent());
        message.setReplyPreviewSenderRole(target.getSenderRole());
    }

    private void validateAttachmentOwnership(ChatMessageType type, String content, String sessionId) {
        if (type != ChatMessageType.IMAGE && type != ChatMessageType.FILE) {
            return;
        }
        String prefix = "/chat/attachments/";
        String suffix = "/content";
        if (!content.startsWith(prefix) || !content.endsWith(suffix)) {
            return; // 外部http/https资源仍按既有规则允许。
        }
        String attachmentId = content.substring(prefix.length(), content.length() - suffix.length());
        ChatAttachment attachment = chatAttachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new IllegalArgumentException("附件不存在或已经失效");
        }
        if (!sessionId.equals(attachment.getSessionId()) || !type.name().equals(attachment.getMessageType())) {
            throw new IllegalArgumentException("附件不属于当前会话或附件类型不匹配");
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
        routeAndPush(message, session);
    }

    /**
     * 会话已加载时的推送路径，避免热路径上重复查询数据库。
     */
    private void routeAndPush(
            ChatMessage message,
            ChatSession session
    ) {
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
        /*
         * 推送失败不抛出：消息已在待确认列表中，
         * 接收者上线或主动拉取时会重新投递。
         */
        try {
            messagingTemplate.convertAndSendToUser(
                    receiverId,
                    USER_CHAT_QUEUE,
                    ChatMessageDTO.fromEntity(
                            message
                    )
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "实时推送失败，消息保留在待确认列表等待重投，接收者：{}",
                    receiverId,
                    exception
            );
            return;
        }


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
