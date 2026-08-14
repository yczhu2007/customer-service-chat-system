package com.example.customerservice.runner;

import com.example.customerservice.service.ChatAgentOperations;
import com.example.customerservice.service.ChatMessageManagementOperations;
import com.example.customerservice.service.ChatPresenceOperations;
import com.example.customerservice.service.impl.ChatRoutingSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** 验证手工构造后由@Bean注册的聊天服务确实进入Spring事务代理。 */
@Component
@Slf4j
public class ChatTransactionalBeanVerifier implements SmartInitializingSingleton {

    private final ChatPresenceOperations chatPresenceOperations;
    private final ChatMessageManagementOperations chatMessageManagementOperations;
    private final ChatRoutingSessionService chatRoutingSessionService;
    private final ChatAgentOperations chatAgentOperations;

    public ChatTransactionalBeanVerifier(
            ChatPresenceOperations chatPresenceOperations,
            ChatMessageManagementOperations chatMessageManagementOperations,
            ChatRoutingSessionService chatRoutingSessionService,
            ChatAgentOperations chatAgentOperations
    ) {
        this.chatPresenceOperations = chatPresenceOperations;
        this.chatMessageManagementOperations = chatMessageManagementOperations;
        this.chatRoutingSessionService = chatRoutingSessionService;
        this.chatAgentOperations = chatAgentOperations;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, Object> transactionalBeans = new LinkedHashMap<>();
        transactionalBeans.put("chatPresenceOperations", chatPresenceOperations);
        transactionalBeans.put("chatMessageManagementOperations", chatMessageManagementOperations);
        transactionalBeans.put("chatRoutingSessionService", chatRoutingSessionService);
        transactionalBeans.put("chatAgentOperations", chatAgentOperations);

        for (Map.Entry<String, Object> entry : transactionalBeans.entrySet()) {
            if (!AopUtils.isAopProxy(entry.getValue())) {
                throw new IllegalStateException(
                        "聊天事务Bean未进入Spring代理：" + entry.getKey()
                );
            }
        }
        log.info("聊天服务@Bean事务代理检查通过，数量={}", transactionalBeans.size());
    }
}
