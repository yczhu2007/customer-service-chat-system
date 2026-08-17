package com.example.customerservice.service;

import com.example.customerservice.dto.QuickReplySaveDTO;
import com.example.customerservice.dto.QuickReplyVO;

import java.util.List;

public interface QuickReplyService {
    List<QuickReplyVO> findMine(String agentId);
    QuickReplyVO create(String agentId, QuickReplySaveDTO request);
    QuickReplyVO update(String agentId, String id, QuickReplySaveDTO request);
    void delete(String agentId, String id);
}
