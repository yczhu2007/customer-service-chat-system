package com.example.customerservice.runner;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.customerservice.constant.ChatConstants;
import com.example.customerservice.constant.RedisConstants;
import com.example.customerservice.domain.ChatAgentSkill;
import com.example.customerservice.mapper.ChatAgentSkillMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.List;

/** 启动时使用数据库中的客服技能记录重建Redis技能集合。 */
@Component
@Slf4j
public class VipAgentSkillCacheInitializer implements ApplicationRunner {

    private final ChatAgentSkillMapper chatAgentSkillMapper;
    private final ChatRedisRepository chatRedisRepository;

    public VipAgentSkillCacheInitializer(
            ChatAgentSkillMapper chatAgentSkillMapper,
            ChatRedisRepository chatRedisRepository
    ) {
        this.chatAgentSkillMapper = chatAgentSkillMapper;
        this.chatRedisRepository = chatRedisRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<String> agentIds = chatAgentSkillMapper.selectList(
                            Wrappers.<ChatAgentSkill>lambdaQuery()
                                    .eq(
                                            ChatAgentSkill::getSkillCode,
                                            ChatConstants.AGENT_SKILL_VIP_CODE
                                    )
                    )
                    .stream()
                    .map(ChatAgentSkill::getAgentId)
                    .distinct()
                    .toList();
            chatRedisRepository.delete(RedisConstants.AGENT_SKILL_VIP);
            if (!agentIds.isEmpty()) {
                chatRedisRepository.setAdd(
                        RedisConstants.AGENT_SKILL_VIP,
                        agentIds.toArray(String[]::new)
                );
            }
            log.info("VIP客服技能缓存重建完成，客服数量={}", agentIds.size());
        } catch (DataAccessException exception) {
            log.warn(
                    "VIP客服技能缓存未重建，请先执行chat_agent_skill建表脚本",
                    exception
            );
        }
    }
}
