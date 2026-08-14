package com.example.customerservice.config;

import com.example.customerservice.mapper.ChatMessageMapper;
import com.example.customerservice.mapper.ChatMessageReadMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import com.example.customerservice.mapper.ChatAgentSkillMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.ChatMessageOperations;
import com.example.customerservice.service.ChatAgentOperations;
import com.example.customerservice.service.ChatRoutingOperations;
import com.example.customerservice.service.ChatMessageDeliveryOperations;
import com.example.customerservice.service.ChatMessageManagementOperations;
import com.example.customerservice.service.ChatOfflineMessageOperations;
import com.example.customerservice.service.ChatPresenceCallbacks;
import com.example.customerservice.service.ChatPresenceOperations;
import com.example.customerservice.service.ChatSessionTransferOperations;
import com.example.customerservice.service.ChatSessionNotificationOperations;
import com.example.customerservice.service.MessagePersistService;
import com.example.customerservice.service.impl.ChatMessageService;
import com.example.customerservice.service.impl.ChatMessageDeliveryService;
import com.example.customerservice.service.impl.ChatMessageManagementService;
import com.example.customerservice.service.impl.ChatOfflineMessageService;
import com.example.customerservice.service.impl.ChatPresenceService;
import com.example.customerservice.service.impl.ChatRoutingSessionService;
import com.example.customerservice.service.impl.ChatAgentSessionRecoveryService;
import com.example.customerservice.service.impl.ChatAgentService;
import com.example.customerservice.service.impl.ChatSessionTransferService;
import com.example.customerservice.service.impl.ChatSessionNotificationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** 聊天业务服务的显式装配配置。 */
@Configuration
public class ChatServiceConfiguration {

    @Bean
    public TransactionTemplate chatTransactionTemplate(
            PlatformTransactionManager transactionManager
    ) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    public ChatOfflineMessageOperations chatOfflineMessageOperations(
            ChatRedisRepository chatRedisRepository,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper
    ) {
        return new ChatOfflineMessageService(
                chatRedisRepository,
                messagingTemplate,
                objectMapper
        );
    }

    @Bean
    public ChatMessageDeliveryOperations chatMessageDeliveryOperations(
            ChatRedisRepository chatRedisRepository,
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            ChatMessageReadMapper chatMessageReadMapper,
            SimpMessagingTemplate messagingTemplate,
            MessagePersistService messagePersistService,
            ObjectMapper objectMapper,
            ChatOfflineMessageOperations chatOfflineMessageOperations,
            @Value("${app.chat.message.recall-window-seconds:120}") long messageRecallWindowSeconds,
            @Value("${app.chat.message.edit-window-seconds:300}") long messageEditWindowSeconds
    ) {
        return new ChatMessageDeliveryService(
                chatRedisRepository,
                chatSessionMapper,
                chatMessageMapper,
                chatMessageReadMapper,
                messagingTemplate,
                messagePersistService,
                objectMapper,
                chatOfflineMessageOperations,
                messageRecallWindowSeconds,
                messageEditWindowSeconds
        );
    }

    @Bean
    public ChatMessageManagementOperations chatMessageManagementOperations(
            ChatRedisRepository chatRedisRepository,
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            ChatMessageReadMapper chatMessageReadMapper,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper,
            @Value("${app.chat.message.recall-window-seconds:120}") long messageRecallWindowSeconds,
            @Value("${app.chat.message.edit-window-seconds:300}") long messageEditWindowSeconds
    ) {
        return new ChatMessageManagementService(
                chatRedisRepository,
                chatSessionMapper,
                chatMessageMapper,
                chatMessageReadMapper,
                messagingTemplate,
                objectMapper,
                messageRecallWindowSeconds,
                messageEditWindowSeconds
        );
    }

    @Bean
    public ChatMessageOperations chatMessageOperations(
            ChatMessageDeliveryOperations chatMessageDeliveryOperations,
            ChatMessageManagementOperations chatMessageManagementOperations
    ) {
        return new ChatMessageService(
                chatMessageDeliveryOperations,
                chatMessageManagementOperations
        );
    }

    @Bean
    public ChatPresenceOperations chatPresenceOperations(
            ChatRedisRepository chatRedisRepository,
            ChatSessionMapper chatSessionMapper,
            ChatMessageReadMapper chatMessageReadMapper,
            SimpMessagingTemplate messagingTemplate,
            SysUserRoleMapper sysUserRoleMapper,
            ObjectProvider<ChatPresenceCallbacks> callbacksProvider,
            @Value("${app.chat.agent-reconnect-grace-seconds:20}") long agentReconnectGraceSeconds
    ) {
        return new ChatPresenceService(
                chatRedisRepository,
                chatSessionMapper,
                chatMessageReadMapper,
                messagingTemplate,
                sysUserRoleMapper,
                callbacksProvider,
                agentReconnectGraceSeconds
        );
    }

    @Bean
    public ChatSessionTransferOperations chatSessionTransferOperations(
            ChatRedisRepository chatRedisRepository,
            ChatSessionMapper chatSessionMapper,
            SysUserMapper sysUserMapper,
            SysUserRoleMapper sysUserRoleMapper,
            SimpMessagingTemplate messagingTemplate,
            TransactionTemplate chatTransactionTemplate
    ) {
        return new ChatSessionTransferService(
                chatRedisRepository,
                chatSessionMapper,
                sysUserMapper,
                sysUserRoleMapper,
                messagingTemplate,
                chatTransactionTemplate,
                com.example.customerservice.constant.RedisConstants.AGENT_MAX_CONCURRENCY
        );
    }

    @Bean
    public ChatSessionNotificationOperations chatSessionNotificationOperations(
            SimpMessagingTemplate messagingTemplate
    ) {
        return new ChatSessionNotificationService(messagingTemplate);
    }

    @Bean
    public ChatAgentSessionRecoveryService chatAgentSessionRecoveryService(
            ChatRedisRepository chatRedisRepository,
            ChatSessionMapper chatSessionMapper,
            @Value("${app.chat.agent-session-restore-batch-size:100}")
            int restoreBatchSize
    ) {
        return new ChatAgentSessionRecoveryService(
                chatRedisRepository,
                chatSessionMapper,
                restoreBatchSize
        );
    }

    @Bean
    public ChatRoutingSessionService chatRoutingSessionService(
            ChatRedisRepository chatRedisRepository,
            ChatMessageOperations chatMessageOperations,
            ChatPresenceOperations chatPresenceOperations,
            ChatSessionTransferOperations chatSessionTransferOperations,
            ChatSessionNotificationOperations chatSessionNotificationOperations,
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            ChatMessageReadMapper chatMessageReadMapper,
            SimpMessagingTemplate messagingTemplate,
            MessagePersistService messagePersistService,
            ObjectMapper objectMapper,
            SysUserRoleMapper sysUserRoleMapper,
            SysUserMapper sysUserMapper,
            ObjectProvider<ChatAgentOperations> agentOperationsProvider,
            @Value("${app.chat.agent-reconnect-grace-seconds:20}") long agentReconnectGraceSeconds,
            @Value("${app.chat.vip.reserved-slots:1}") int vipReservedSlots,
            @Value("${app.chat.queue.average-handle-seconds:300}") long averageHandleSeconds,
            @Value("${app.chat.queue.vip-priority-step-seconds:1000000000}") long vipPriorityStepSeconds,
            @Value("${app.chat.message.recall-window-seconds:120}") long messageRecallWindowSeconds,
            @Value("${app.chat.message.edit-window-seconds:300}") long messageEditWindowSeconds,
            @Value("${app.chat.reconciliation.active-session-batch-size:200}")
            int activeSessionReconciliationBatchSize
    ) {
        return new ChatRoutingSessionService(
                chatRedisRepository,
                chatMessageOperations,
                chatPresenceOperations,
                chatSessionTransferOperations,
                chatSessionNotificationOperations,
                chatSessionMapper,
                chatMessageMapper,
                chatMessageReadMapper,
                messagingTemplate,
                messagePersistService,
                objectMapper,
                sysUserRoleMapper,
                sysUserMapper,
                agentOperationsProvider,
                agentReconnectGraceSeconds,
                vipReservedSlots,
                averageHandleSeconds,
                vipPriorityStepSeconds,
                messageRecallWindowSeconds,
                messageEditWindowSeconds,
                activeSessionReconciliationBatchSize
        );
    }

    @Bean
    public ChatAgentOperations chatAgentOperations(
            ChatRedisRepository chatRedisRepository,
            ChatAgentSessionRecoveryService sessionRecoveryService,
            ChatRoutingOperations chatRoutingOperations,
            ChatPresenceOperations chatPresenceOperations,
            ChatSessionNotificationOperations notificationOperations,
            SysUserMapper sysUserMapper,
            SysUserRoleMapper sysUserRoleMapper,
            ChatAgentSkillMapper chatAgentSkillMapper
    ) {
        return new ChatAgentService(
                chatRedisRepository,
                sessionRecoveryService,
                chatRoutingOperations,
                chatPresenceOperations,
                notificationOperations,
                sysUserMapper,
                sysUserRoleMapper,
                chatAgentSkillMapper
        );
    }

}
