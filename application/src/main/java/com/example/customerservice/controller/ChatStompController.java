package com.example.customerservice.controller;

import static com.example.customerservice.constant.ChatDestinations.USER_CHAT_QUEUE;

import com.example.customerservice.dto.AckRequest;
import com.example.customerservice.dto.ChatMessageDTO;
import com.example.customerservice.dto.EndSessionRequest;
import com.example.customerservice.dto.HistoryRequest;
import com.example.customerservice.dto.MessageMutationResult;
import com.example.customerservice.dto.MessageReadResult;
import com.example.customerservice.dto.ReadMessagesRequest;
import com.example.customerservice.dto.RecallMessageRequest;
import com.example.customerservice.dto.TransferSessionRequest;
import com.example.customerservice.dto.TypingRequest;
import com.example.customerservice.service.ChatStompApplicationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.security.Principal;
import java.util.Map;

@Controller
@Validated
public class ChatStompController {

    @Autowired private ChatStompApplicationService chatApplicationService;

    @MessageMapping("/chat.start")
    public void startConsultation(Principal principal) {
        chatApplicationService.startConsultation(principal);
    }

    @MessageMapping("/chat.typing")
    public void handleTyping(
            @Valid TypingRequest request,
            Principal principal
    ) {
        chatApplicationService.handleTyping(request, principal);
    }

    @MessageMapping("/chat.send")
    public void handleSend(@Valid ChatMessageDTO request, Principal principal) {
        chatApplicationService.handleSend(request, principal);
    }

    @MessageMapping("/chat.end")
    public void handleEndSession(@Valid EndSessionRequest request, Principal principal) {
        chatApplicationService.handleEndSession(request, principal);
    }

    @MessageMapping("/chat.transfer")
    public void handleTransferSession(@Valid TransferSessionRequest request, Principal principal) {
        chatApplicationService.handleTransferSession(request, principal);
    }

    @MessageMapping("/chat.history")
    @SendToUser(USER_CHAT_QUEUE)
    public Map<String, Object> getHistory(@Valid HistoryRequest request, Principal principal) {
        return chatApplicationService.getHistory(request, principal);
    }

    @MessageMapping("/chat.offline.pull")
    public void pullOfflineMessages(Principal principal) {
        chatApplicationService.pullOfflineMessages(principal);
    }

    @MessageMapping("/chat.ack")
    public void handleAck(@Valid AckRequest request, Principal principal) {
        chatApplicationService.handleAck(request, principal);
    }

    @MessageMapping("/chat.read")
    @SendToUser("/queue/messages")
    public MessageReadResult markMessagesRead(@Valid ReadMessagesRequest request, Principal principal) {
        return chatApplicationService.markMessagesRead(request, principal);
    }

    @MessageMapping("/chat.message.recall")
    @SendToUser("/queue/messages")
    public MessageMutationResult recallMessage(@Valid RecallMessageRequest request, Principal principal) {
        return chatApplicationService.recallMessage(request, principal);
    }

    @MessageMapping("/chat.heartbeat")
    public void handleHeartbeat(Principal principal, @Header("simpSessionId") String wsSessionId) {
        chatApplicationService.handleHeartbeat(principal, wsSessionId);
    }
}
