package com.example.customerservice.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.customerservice.domain.ChatQuickReply;
import com.example.customerservice.dto.QuickReplySaveDTO;
import com.example.customerservice.dto.QuickReplyVO;
import com.example.customerservice.exception.NotFoundException;
import com.example.customerservice.mapper.ChatQuickReplyMapper;
import com.example.customerservice.service.QuickReplyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class QuickReplyServiceImpl implements QuickReplyService {
    private final ChatQuickReplyMapper mapper;
    public QuickReplyServiceImpl(ChatQuickReplyMapper mapper) { this.mapper = mapper; }

    @Override
    public List<QuickReplyVO> findMine(String agentId) {
        return mapper.selectList(Wrappers.<ChatQuickReply>lambdaQuery()
                        .eq(ChatQuickReply::getAgentId, agentId)
                        .orderByAsc(ChatQuickReply::getSortOrder)
                        .orderByDesc(ChatQuickReply::getUpdateTime)
                        .orderByAsc(ChatQuickReply::getId)
                        .last("LIMIT 200"))
                .stream().map(this::toVO).toList();
    }

    @Override
    @Transactional
    public QuickReplyVO create(String agentId, QuickReplySaveDTO request) {
        ChatQuickReply reply = new ChatQuickReply();
        reply.setId(UUID.randomUUID().toString().replace("-", ""));
        reply.setAgentId(agentId);
        apply(reply, request);
        if (mapper.insert(reply) != 1) throw new IllegalStateException("快捷回复创建失败");
        return toVO(mapper.selectById(reply.getId()));
    }

    @Override
    @Transactional
    public QuickReplyVO update(String agentId, String id, QuickReplySaveDTO request) {
        ChatQuickReply reply = requireOwned(agentId, id);
        apply(reply, request);
        if (mapper.updateById(reply) != 1) throw new IllegalStateException("快捷回复修改失败");
        return toVO(mapper.selectById(id));
    }

    @Override
    @Transactional
    public void delete(String agentId, String id) {
        requireOwned(agentId, id);
        if (mapper.deleteById(id) != 1) throw new IllegalStateException("快捷回复删除失败");
    }

    private ChatQuickReply requireOwned(String agentId, String id) {
        ChatQuickReply reply = mapper.selectById(id);
        if (reply == null || !agentId.equals(reply.getAgentId())) throw new NotFoundException("快捷回复不存在");
        return reply;
    }
    private void apply(ChatQuickReply reply, QuickReplySaveDTO request) {
        reply.setTitle(request.getTitle() == null ? "" : request.getTitle().trim());
        reply.setContent(request.getContent() == null ? "" : request.getContent().trim());
        reply.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
    }
    private QuickReplyVO toVO(ChatQuickReply reply) {
        QuickReplyVO vo = new QuickReplyVO();
        vo.setId(reply.getId()); vo.setTitle(reply.getTitle()); vo.setContent(reply.getContent());
        vo.setSortOrder(reply.getSortOrder()); vo.setCreateTime(reply.getCreateTime()); vo.setUpdateTime(reply.getUpdateTime());
        return vo;
    }
}
