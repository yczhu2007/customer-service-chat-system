package com.example.customerservice.controller;

import com.example.customerservice.common.Result;
import com.example.customerservice.domain.SysUser;
import com.example.customerservice.dto.DeadLetterMessageVO;
import com.example.customerservice.dto.PageResult;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.service.ChatAgentOperations;
import com.example.customerservice.service.ChatRoutingOperations;
import com.example.customerservice.service.MessagePersistService;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.mapper.SysUserRoleMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/chat")
@Slf4j
@Validated
public class ChatQueueAgentController {

    @Autowired private ChatRoutingOperations chatRoutingOperations;
    @Autowired private ChatAgentOperations chatAgentOperations;
    @Autowired private MessagePersistService messagePersistService;
    @Autowired private CurrentUser currentUser;
    @Autowired private ChatRedisRepository chatRedisRepository;
    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private SysUserMapper sysUserMapper;
    @Autowired private SysUserRoleMapper sysUserRoleMapper;

    @PostMapping("/queue/cancel")
    public Result<Void> cancelQueue() {
        currentUser.requireRole("USER");
        currentUser.requirePermission("chat:user:access");
        String userId = currentUser.getUserId();
        Long removed = chatRedisRepository.cancelQueueEntry(userId);
        boolean cancelled = removed != null && removed > 0;
        try {
            chatRoutingOperations.refreshWaitingPositions();
        } catch (RuntimeException exception) {
            log.warn("刷新排队位置通知失败，取消操作已完成，userId={}", userId, exception);
        }
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("event", "QUEUE_CANCELLED");
            messagingTemplate.convertAndSendToUser(userId, "/queue/chat", event);
        } catch (RuntimeException exception) {
            log.warn("发送取消排队通知失败，取消操作已完成，userId={}", userId, exception);
        }
        return Result.successMessage(cancelled ? "已取消排队" : "当前未在等待队列中");
    }

    @PostMapping("/agent/online")
    public Result<Void> agentOnline() {
        currentUser.requireRole("AGENT");
        currentUser.requirePermission("chat:agent:online");
        chatAgentOperations.agentOnline(currentUser.getUserId());
        return Result.successMessage("上线成功");
    }

    @PostMapping("/agent/offline")
    public Result<Void> agentOffline() {
        currentUser.requireRole("AGENT");
        currentUser.requirePermission("chat:agent:offline");
        chatAgentOperations.agentOffline(currentUser.getUserId());
        return Result.successMessage("下线成功");
    }

    @PutMapping("/agents/{agentLoginNumber}/vip-skill")
    public Result<Void> enableAgentVipSkill(
            @PathVariable @NotBlank(message = "客服登录编号不能为空")
            @Size(max = 64, message = "客服登录编号长度不能超过64个字符") String agentLoginNumber
    ) {
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("chat:agent:vip-skill:manage");
        chatAgentOperations.setAgentVipSkill(requireEnabledAgentId(agentLoginNumber), true);
        return Result.successMessage("已加入VIP坐席技能组");
    }

    @DeleteMapping("/agents/{agentLoginNumber}/vip-skill")
    public Result<Void> disableAgentVipSkill(
            @PathVariable @NotBlank(message = "客服登录编号不能为空")
            @Size(max = 64, message = "客服登录编号长度不能超过64个字符") String agentLoginNumber
    ) {
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("chat:agent:vip-skill:manage");
        chatAgentOperations.setAgentVipSkill(requireEnabledAgentId(agentLoginNumber), false);
        return Result.successMessage("已移出VIP坐席技能组");
    }

    @GetMapping("/agents/vip-skill")
    public Result<Set<String>> findVipSkillAgents() {
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("chat:agent:vip-skill:manage");
        return Result.success(chatAgentOperations.findVipSkillAgentIds().stream()
                .map(sysUserMapper::selectById)
                .filter(user -> user != null && user.getUsername() != null && !user.getUsername().isBlank())
                .map(SysUser::getUsername)
                .collect(Collectors.toSet()));
    }

    @GetMapping("/admin/deadletters")
    public Result<PageResult<DeadLetterMessageVO>> findDeadLetters(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize
    ) {
        requireDeadLetterManagementPermission();
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageNo必须大于0，pageSize必须在1到100之间");
        }
        return Result.success(messagePersistService.findDeadLetters(pageNo, pageSize));
    }

    @PostMapping("/admin/deadletters/{messageId}/replay")
    public Result<Void> replayDeadLetter(
            @PathVariable @NotBlank(message = "消息ID不能为空")
            @Size(max = 64, message = "消息ID长度不能超过64个字符") String messageId
    ) {
        requireDeadLetterManagementPermission();
        messagePersistService.replayDeadLetter(messageId);
        return Result.successMessage("死信消息已提交重放");
    }

    @DeleteMapping("/admin/deadletters/{messageId}")
    public Result<Void> deleteDeadLetter(
            @PathVariable @NotBlank(message = "消息ID不能为空")
            @Size(max = 64, message = "消息ID长度不能超过64个字符") String messageId
    ) {
        requireDeadLetterManagementPermission();
        messagePersistService.deleteDeadLetter(messageId);
        return Result.successMessage("死信消息已删除");
    }

    private String requireEnabledAgentId(String loginNumber) {
        SysUser agent = sysUserMapper.findByUsername(loginNumber.trim());
        if (agent == null || !"ENABLED".equals(agent.getStatus())) {
            throw new IllegalArgumentException("客服登录编号不存在或账号已禁用");
        }
        Set<String> roles = sysUserRoleMapper.findRoleCodesByUserId(agent.getId());
        if (roles == null || !roles.contains("AGENT")) {
            throw new IllegalArgumentException("该登录编号不是客服账号");
        }
        return agent.getId();
    }

    private void requireDeadLetterManagementPermission() {
        currentUser.requireRole("ADMIN");
        currentUser.requirePermission("chat:message:deadletter:manage");
    }
}
