package com.example.customerservice.controller;

import com.example.customerservice.dto.AckRequest;
import com.example.customerservice.dto.ChatMessageDTO;
import com.example.customerservice.dto.EndSessionRequest;
import com.example.customerservice.dto.HistoryRequest;
import com.example.customerservice.dto.MessageMutationResult;
import com.example.customerservice.dto.MessageReadResult;
import com.example.customerservice.dto.ReadMessagesRequest;
import com.example.customerservice.dto.RecallMessageRequest;
import com.example.customerservice.dto.TransferSessionRequest;
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

    @Autowired private ChatController chatHandler;

    @MessageMapping("/chat.start")
    public void startConsultation(Principal principal) {
        chatHandler.startConsultation(principal);
    }

    @MessageMapping("/chat.typing")
    public void handleTyping(
            @org.springframework.messaging.handler.annotation.Payload Map<String, Object> request,
            Principal principal
    ) {
        chatHandler.handleTyping(request, principal);
    }

    @MessageMapping("/chat.send")
    public void handleSend(@Valid ChatMessageDTO request, Principal principal) {
        chatHandler.handleSend(request, principal);
    }

    @MessageMapping("/chat.end")
    public void handleEndSession(@Valid EndSessionRequest request, Principal principal) {
        chatHandler.handleEndSession(request, principal);
    }

    @MessageMapping("/chat.transfer")
    public void handleTransferSession(@Valid TransferSessionRequest request, Principal principal) {
        chatHandler.handleTransferSession(request, principal);
    }

    @MessageMapping("/chat.history")
    @SendToUser("/queue/chat")
    public Map<String, Object> getHistory(@Valid HistoryRequest request, Principal principal) {
        return chatHandler.getHistory(request, principal);
    }

    @MessageMapping("/chat.offline.pull")
    public void pullOfflineMessages(Principal principal) {
        chatHandler.pullOfflineMessages(principal);
    }

    @MessageMapping("/chat.ack")
    public void handleAck(@Valid AckRequest request, Principal principal) {
        chatHandler.handleAck(request, principal);
    }

    @MessageMapping("/chat.read")
    @SendToUser("/queue/messages")
    public MessageReadResult markMessagesRead(@Valid ReadMessagesRequest request, Principal principal) {
        return chatHandler.markMessagesRead(request, principal);
    }

    @MessageMapping("/chat.message.recall")
    @SendToUser("/queue/messages")
    public MessageMutationResult recallMessage(@Valid RecallMessageRequest request, Principal principal) {
        return chatHandler.recallMessage(request, principal);
    }

    @MessageMapping("/chat.heartbeat")
    public void handleHeartbeat(Principal principal, @Header("simpSessionId") String wsSessionId) {
        chatHandler.handleHeartbeat(principal, wsSessionId);
    }
}
