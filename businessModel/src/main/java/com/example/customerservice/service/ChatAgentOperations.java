package com.example.customerservice.service;

import java.util.Set;

/** 客服上下线、会话恢复和技能组操作。 */
public interface ChatAgentOperations {

    void agentOnline(String agentId);

    void agentOffline(String agentId);

    void setAgentVipSkill(String agentId, boolean enabled);

    void setAgentVipSkillByLoginNumber(String loginNumber, boolean enabled);

    Set<String> findVipSkillAgentIds();

    Set<String> findVipSkillAgentLoginNumbers();

    void rebuildVipSkillCache();
}
