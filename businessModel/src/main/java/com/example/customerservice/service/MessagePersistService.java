package com.example.customerservice.service;

import com.example.customerservice.domain.ChatMessage;

public interface MessagePersistService {

    void persistMessageAsync(ChatMessage message);

    void retryFailedMessages();

}
