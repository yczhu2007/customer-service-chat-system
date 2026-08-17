package com.example.customerservice.service;

import com.example.customerservice.dto.*;

/** 会话查询、评价和用户侧栏的只读/评价服务。 */
public interface ChatSessionQueryService {
    /** 参与者查看自己的会话历史列表（用户或客服视角）。 */
    PageResult<ChatSessionListItemVO> findMySessions(
            String participantId, String statusFilter, long pageNo, long pageSize);

    /** 用户对已结束的会话提交满意度评价，每会话仅一次。 */
    SessionRatingVO rateSession(String userId, String sessionId, SessionRatingDTO request);

    /** 查询指定会话的评价（若已评价）。 */
    SessionRatingVO getSessionRating(String sessionId);

    /** 客服查看当前会话中用户的基本信息侧栏。 */
    UserProfileSidebarVO getUserProfileSidebar(String agentId, String sessionId);

    /** 查询当前排队状态（在线客服数、队列大小、我的位置、预估等待时间）。 */
    QueueStatusVO getQueueStatus(String userId);
}
