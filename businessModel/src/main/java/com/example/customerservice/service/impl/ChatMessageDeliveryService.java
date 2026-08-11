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
import com.example.customerservice.dto.ChatHistoryPage;
import com.example.customerservice.mapper.ChatMessageMapper;
import com.example.customerservice.mapper.ChatMessageReadMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.ChatMessageDeliveryOperations;
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
        String dedupKey =
                RedisConstants.CLIENT_MSG_DEDUP
                        + message.getClientMsgId();


        Boolean firstSend =
                chatRedisRepository.setValueIfAbsent(
                                dedupKey,
                                "1",
                                24,
                                TimeUnit.HOURS
                        );


        if (!Boolean.TRUE.equals(firstSend)) {

            log.info(
                    "检测到重复消息，clientMsgId："
                            + message.getClientMsgId()
            );

            return 0;
        }

        String operationLockToken = chatRedisRepository.acquireSessionOperationLock(message.getSessionId());
        if (operationLockToken == null) {
            chatRedisRepository.delete(dedupKey);
            throw new IllegalStateException("会话正在转接、结束或执行超时处理，请稍后重试");
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
        message.setId(
                UUID.randomUUID().toString()
        );


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

        String offlineKey =
                RedisConstants.OFFLINE_MSG
                        + receiverId;


        try {

            /*
             * 将完整消息转换为JSON
             */
            String messageJson =
                    objectMapper.writeValueAsString(
                            message
                    );


            /*
             * 将消息加入接收者的待确认列表
             */
            chatRedisRepository.listRightPush(
                            offlineKey,
                            messageJson
                    );


            /*
             * 最多保留最近200条
             */
            chatRedisRepository.listTrim(
                            offlineKey,
                            -RedisConstants
                                    .OFFLINE_MSG_MAX_COUNT,
                            -1
                    );


            /*
             * 消息最多保留7天
             */
            chatRedisRepository.expire(
                    offlineKey,
                    RedisConstants
                            .OFFLINE_MSG_TTL_DAYS,
                    TimeUnit.DAYS
            );


            log.info(
                    "待确认消息已保存，接收者："
                            + receiverId
                            + "，messageId："
                            + message.getId()
            );


            return messageJson;

        } catch (Exception e) {

            throw new IllegalStateException(
                    "消息序列化失败",
                    e
            );
        }
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
        if (
                userId == null ||
                        userId.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "userId不能为空"
            );
        }
        String wsSessionId =
                chatRedisRepository.getValue(
                        RedisConstants.USER_WS
                                + userId
                );


        if (
                wsSessionId == null ||
                        wsSessionId.isBlank()
        ) {

            log.info(
                    "用户当前不在线，不拉取消息："
                            + userId
            );


            return;
        }


        String offlineKey =
                RedisConstants.OFFLINE_MSG
                        + userId;
        List<String> messageJsonList =
                chatRedisRepository.listRange(
                        offlineKey,
                        0,
                        -1
                );


        if (
                messageJsonList == null ||
                        messageJsonList.isEmpty()
        ) {

            log.info(
                    "待确认消息拉取完成，用户："
                            + userId
                            + "，数量：0"
            );


            return;
        }


        int pushedCount = 0;
        for (String messageJson : messageJsonList) {

            try {

                ChatMessage message =
                        objectMapper.readValue(
                                messageJson,
                                ChatMessage.class
                        );


                /*
                 * 推送消息，收到客户端ACK前仍保留在离线消息列表中
                 */
                messagingTemplate.convertAndSendToUser(
                        userId,
                        "/queue/chat",
                        ChatMessageDTO.fromEntity(
                                message
                        )
                );


                pushedCount++;


                log.info(
                        "待确认消息已重新推送，用户："
                                + userId
                                + "，messageId："
                                + message.getId()
                );

            } catch (Exception e) {

                log.info(
                        "待确认消息反序列化失败："
                                + messageJson
                );


                log.info(
                        "失败原因："
                                + e.getMessage()
                );
            }
        }


        log.info(
                "待确认消息拉取完成，用户："
                        + userId
                        + "，数量："
                        + pushedCount
        );
    }
    /**
     * 处理客户端业务ACK
     */
    @Override
    public void handleAck(
            String messageId,
            String receiverId
    ) {
        if (
                messageId == null ||
                        messageId.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "messageId不能为空"
            );
        }


        if (
                receiverId == null ||
                        receiverId.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "当前确认用户不能为空"
            );
        }


        String ackKey =
                RedisConstants.MSG_ACK
                        + messageId;

        /*
         * 兼容修改前遗留的Hash类型ACK数据，
         * 避免切换为Set后出现WRONGTYPE错误。
         */
        if (
                DataType.HASH.equals(
                        chatRedisRepository.type(
                                ackKey
                        )
                )
        ) {
            Object oldReceiverObject =
                    chatRedisRepository.hashGet(
                                    ackKey,
                                    "receiverId"
                            );

            if (
                    oldReceiverObject != null &&
                            !receiverId.equals(
                                    oldReceiverObject.toString()
                            )
            ) {
                throw new IllegalArgumentException(
                        "无权确认这条消息"
                );
            }

            Object oldStatusObject =
                    chatRedisRepository.hashGet(
                                    ackKey,
                                    "status"
                            );

            chatRedisRepository.delete(
                    ackKey
            );

            if (
                    oldReceiverObject != null &&
                            "ACKED".equals(
                                    String.valueOf(
                                            oldStatusObject
                                    )
                            )
            ) {
                chatRedisRepository.setAdd(
                                ackKey,
                                receiverId
                        );
                return;
            }
        }
        Boolean alreadyAcked =
                chatRedisRepository.setIsMember(
                                ackKey,
                                receiverId
                        );


        if (Boolean.TRUE.equals(alreadyAcked)) {
            return;
        }


        /*
         * 只在当前接收者自己的待确认列表中查找消息，
         * 防止确认其他用户的消息。
         */
        String offlineKey =
                RedisConstants.OFFLINE_MSG
                        + receiverId;

        List<String> messageJsonList =
                chatRedisRepository.listRange(
                                offlineKey,
                                0,
                                -1
                        );

        String matchedMessageJson = null;


        if (messageJsonList != null) {
            for (String messageJson : messageJsonList) {
                try {
                    ChatMessage message =
                            objectMapper.readValue(
                                    messageJson,
                                    ChatMessage.class
                            );

                    if (
                            messageId.equals(
                                    message.getId()
                            )
                    ) {
                        matchedMessageJson =
                                messageJson;
                        break;
                    }
                } catch (Exception exception) {
                    log.info(
                            "检查ACK消息时忽略无效JSON："
                                    + exception.getMessage()
                    );
                }
            }
        }


        if (matchedMessageJson == null) {
            throw new IllegalArgumentException(
                    "无权确认这条消息或消息不存在"
            );
        }


        Long removedCount =
                chatRedisRepository.listRemove(
                                offlineKey,
                                1,
                                matchedMessageJson
                        );

        /*
         * 文档规定msg:ack:{messageId}使用Set，
         * Set成员记录已经确认该消息的接收者。
         */
        chatRedisRepository.setAdd(
                        ackKey,
                        receiverId
                );


        log.info(
                "ACK后删除待确认消息，messageId："
                        + messageId
                        + "，删除数量："
                        + removedCount
        );


        log.info(
                "客户端ACK处理完成，messageId："
                        + messageId
                        + "，接收者："
                        + receiverId
        );
    }

}
