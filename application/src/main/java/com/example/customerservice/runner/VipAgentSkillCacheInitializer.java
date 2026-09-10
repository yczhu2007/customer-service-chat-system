package com.example.customerservice.runner;

import com.example.customerservice.service.ChatAgentOperations;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 启动时使用数据库中的客服技能记录重建Redis技能集合。 */
@Component
public class VipAgentSkillCacheInitializer implements ApplicationRunner {

    private final ChatAgentOperations chatAgentOperations;

    public VipAgentSkillCacheInitializer(ChatAgentOperations chatAgentOperations) {
        this.chatAgentOperations = chatAgentOperations;
    }

    @Override
    public void run(ApplicationArguments args) {
        chatAgentOperations.rebuildVipSkillCache();
    }
}
