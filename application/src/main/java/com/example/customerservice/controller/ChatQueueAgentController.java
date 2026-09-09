package com.example.customerservice.controller;

import com.example.customerservice.common.Result;
import com.example.customerservice.constant.PermissionCodes;
import com.example.customerservice.constant.RoleCodes;
import com.example.customerservice.dto.DeadLetterMessageVO;
import com.example.customerservice.dto.PageResult;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.service.ChatAgentOperations;
import com.example.customerservice.service.ChatRoutingOperations;
import com.example.customerservice.service.MessagePersistService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/chat")
@Validated
public class ChatQueueAgentController {

    @Autowired private ChatRoutingOperations chatRoutingOperations;
    @Autowired private ChatAgentOperations chatAgentOperations;
    @Autowired private MessagePersistService messagePersistService;
    @Autowired private CurrentUser currentUser;

    @PostMapping("/queue/cancel")
    public Result<Void> cancelQueue() {
        currentUser.requireRole(RoleCodes.USER);
        currentUser.requirePermission(PermissionCodes.CHAT_USER_ACCESS);
        String userId = currentUser.getUserId();
        boolean cancelled = chatRoutingOperations.cancelWaitingUser(userId);
        return Result.successMessage(cancelled ? "已取消排队" : "当前未在等待队列中");
    }

    @PostMapping("/agent/online")
    public Result<Void> agentOnline() {
        currentUser.requireRole(RoleCodes.AGENT);
        currentUser.requirePermission(PermissionCodes.CHAT_AGENT_ONLINE);
        chatAgentOperations.agentOnline(currentUser.getUserId());
        return Result.successMessage("上线成功");
    }

    @PostMapping("/agent/offline")
    public Result<Void> agentOffline() {
        currentUser.requireRole(RoleCodes.AGENT);
        currentUser.requirePermission(PermissionCodes.CHAT_AGENT_OFFLINE);
        chatAgentOperations.agentOffline(currentUser.getUserId());
        return Result.successMessage("下线成功");
    }

    @PutMapping("/agents/{agentLoginNumber}/vip-skill")
    public Result<Void> enableAgentVipSkill(
            @PathVariable @NotBlank(message = "客服登录编号不能为空")
            @Size(max = 64, message = "客服登录编号长度不能超过64个字符") String agentLoginNumber
    ) {
        currentUser.requireRole(RoleCodes.ADMIN);
        currentUser.requirePermission(PermissionCodes.CHAT_AGENT_VIP_SKILL_MANAGE);
        chatAgentOperations.setAgentVipSkillByLoginNumber(agentLoginNumber, true);
        return Result.successMessage("已加入VIP坐席技能组");
    }

    @DeleteMapping("/agents/{agentLoginNumber}/vip-skill")
    public Result<Void> disableAgentVipSkill(
            @PathVariable @NotBlank(message = "客服登录编号不能为空")
            @Size(max = 64, message = "客服登录编号长度不能超过64个字符") String agentLoginNumber
    ) {
        currentUser.requireRole(RoleCodes.ADMIN);
        currentUser.requirePermission(PermissionCodes.CHAT_AGENT_VIP_SKILL_MANAGE);
        chatAgentOperations.setAgentVipSkillByLoginNumber(agentLoginNumber, false);
        return Result.successMessage("已移出VIP坐席技能组");
    }

    @GetMapping("/agents/vip-skill")
    public Result<Set<String>> findVipSkillAgents() {
        currentUser.requireRole(RoleCodes.ADMIN);
        currentUser.requirePermission(PermissionCodes.CHAT_AGENT_VIP_SKILL_MANAGE);
        return Result.success(chatAgentOperations.findVipSkillAgentLoginNumbers());
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

    private void requireDeadLetterManagementPermission() {
        currentUser.requireRole(RoleCodes.ADMIN);
        currentUser.requirePermission(PermissionCodes.CHAT_MESSAGE_DEADLETTER_MANAGE);
    }
}
