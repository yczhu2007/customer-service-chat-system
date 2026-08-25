package com.example.customerservice.service;

import com.example.customerservice.constant.SessionParticipantType;
import com.example.customerservice.dto.*;

/** 会话查询、评价和用户侧栏的只读/评价服务。 */
public interface ChatSessionQueryService {
    /** 参与者查看自己的会话历史列表（用户或客服视角），支持归档状态筛选。 */
    PageResult<ChatSessionListItemVO> findMySessions(
            String participantId,
            SessionParticipantType participantType,
            String statusFilter,
            String archiveStatusFilter, long pageNo, long pageSize);

    /** 用户对已结束的会话提交满意度评价，每会话仅一次。 */
    SessionRatingVO rateSession(String userId, String sessionId, SessionRatingDTO request);

    /** 查询指定会话的评价（若已评价）。 */
    SessionRatingVO getSessionRating(String participantId, String sessionId);

    /** 客服查看当前会话中用户的基本信息侧栏。 */
    UserProfileSidebarVO getUserProfileSidebar(String agentId, String sessionId);

    /** 查看会话元数据。 */
    ChatSessionMetadataVO getSessionMetadata(String actorId, boolean administrator, String sessionId);

    /** 更新会话元数据。 */
    ChatSessionMetadataVO updateSessionMetadata(String agentId, String sessionId, ChatSessionMetadataUpdateDTO request);

    /** 管理员更新任意会话元数据。 */
    ChatSessionMetadataVO updateSessionMetadataAsAdmin(String sessionId, ChatSessionMetadataUpdateDTO request);

    /** 查询当前排队状态（在线客服数、队列大小、我的位置、预估等待时间）。 */
    QueueStatusVO getQueueStatus(String userId);

    /** 客服对已结束会话设置/更新归档状态。 */
    void setArchiveStatus(String agentId, String sessionId, SessionArchiveDTO request);

    /** 客服独立保存已结束会话的单条归档备注。 */
    void saveArchiveRemark(String agentId, String sessionId, SessionArchiveRemarkDTO request);

    /** 管理员查询归档统计概览。 */
    ArchiveStatsVO findArchiveStats();
}
