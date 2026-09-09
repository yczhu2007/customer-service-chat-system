package com.example.customerservice.service;

import com.example.customerservice.domain.ChatMessage;
import com.example.customerservice.dto.DeadLetterMessageVO;
import com.example.customerservice.dto.PageResult;

public interface MessagePersistService {

    void retryAndCheckBacklog(long alertThreshold);

    void markPending(ChatMessage message);

    void persistMessageAsync(ChatMessage message);

    void retryFailedMessages();

    PageResult<DeadLetterMessageVO> findDeadLetters(long pageNo, long pageSize);

    void replayDeadLetter(String messageId);

    void deleteDeadLetter(String messageId);

    int cleanupExpiredDeadLetters(long cutoffEpochMillis, int batchSize);

}
