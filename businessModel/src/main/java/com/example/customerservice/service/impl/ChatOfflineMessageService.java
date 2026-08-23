package com.example.customerservice.service.impl;

import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.domain.ChatMessage;
import com.example.customerservice.dto.ChatMessageDTO;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.ChatOfflineMessageOperations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.DataType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Redis based pending delivery, offline replay and receiver ACK handling. */
@Slf4j
public class ChatOfflineMessageService implements ChatOfflineMessageOperations {

    private final ChatRedisRepository chatRedisRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public ChatOfflineMessageService(
            ChatRedisRepository chatRedisRepository,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper
    ) {
        this.chatRedisRepository = chatRedisRepository;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String cacheForReceiver(
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
             * 待确认队列不能按固定数量截断；消息在 MySQL 持久化前后都可能
             * 依赖该队列重投。容量控制由 Redis 内存告警和数据库历史回放承担。
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
                chatRedisRepository.expire(
                        ackKey,
                        RedisConstants.MSG_ACK_TTL_DAYS,
                        TimeUnit.DAYS
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
        chatRedisRepository.expire(
                ackKey,
                RedisConstants.MSG_ACK_TTL_DAYS,
                TimeUnit.DAYS
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
