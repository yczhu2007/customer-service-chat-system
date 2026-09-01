package com.example.customerservice.controller;

import com.example.customerservice.common.Result;
import com.example.customerservice.constant.AgentSessionView;
import com.example.customerservice.dto.AgentSessionViewCountVO;
import com.example.customerservice.dto.AdminReportOverviewVO;
import com.example.customerservice.dto.AdminReportQueryDTO;
import com.example.customerservice.dto.ChatSessionListItemVO;
import com.example.customerservice.dto.PageResult;
import com.example.customerservice.dto.SessionTransferLogVO;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.service.ChatManagementQueryService;
import com.example.customerservice.service.ChatSessionDeletionService;
import com.example.customerservice.service.ChatSessionOperations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatManagementControllerTest {

    @Mock private ChatManagementQueryService managementQueryService;
    @Mock private ChatSessionMapper sessionMapper;
    @Mock private ChatSessionDeletionService sessionDeletionService;
    @Mock private ChatSessionOperations sessionOperations;
    @Mock private CurrentUser currentUser;
    @InjectMocks private ChatManagementController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "sessionMapper", sessionMapper);
        ReflectionTestUtils.setField(controller, "sessionDeletionService", sessionDeletionService);
        ReflectionTestUtils.setField(controller, "sessionOperations", sessionOperations);
    }

    @Test
    void agentCanReadOwnSessionViews() {
        List<AgentSessionViewCountVO> views = List.of(
                new AgentSessionViewCountVO("MY_ACTIVE", "我的处理中", 2L),
                new AgentSessionViewCountVO("MY_UNREAD", "我的未读", 1L)
        );
        when(currentUser.getUserId()).thenReturn("A001");
        when(managementQueryService.findAgentSessionViews("A001"))
                .thenReturn(views);

        Result<List<AgentSessionViewCountVO>> result =
                controller.findAgentSessionViews();

        assertEquals(views, result.getData());
        verify(currentUser).requireRole("AGENT");
        verify(currentUser).requirePermission("chat:session:view-own");
        verify(managementQueryService).findAgentSessionViews("A001");
    }

    @Test
    void agentCanReadSessionsUnderFixedView() {
        PageResult<ChatSessionListItemVO> page =
                new PageResult<>(2, 50, 1, 1, List.of(new ChatSessionListItemVO()));
        when(currentUser.getUserId()).thenReturn("A001");
        when(managementQueryService.findAgentViewSessions(
                "A001",
                AgentSessionView.MY_UNREAD,
                "OPEN",
                "TK-00000125",
                2,
                50
        )).thenReturn(page);

        Result<PageResult<ChatSessionListItemVO>> result =
                controller.findAgentViewSessions("MY_UNREAD", "OPEN", "TK-00000125", 2, 50);

        assertEquals(page, result.getData());
        verify(currentUser).requireRole("AGENT");
        verify(currentUser).requirePermission("chat:session:view-own");
        verify(managementQueryService).findAgentViewSessions(
                "A001",
                AgentSessionView.MY_UNREAD,
                "OPEN",
                "TK-00000125",
                2,
                50
        );
    }

    @Test
    void invalidViewCodeIsRejectedBeforeServiceCall() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.findAgentViewSessions("queue_all", null, null, 1, 20)
        );

        assertEquals(
                "坐席会话视图不支持该类型",
                exception.getMessage()
        );
        verify(currentUser).requireRole("AGENT");
        verify(currentUser).requirePermission("chat:session:view-own");
        verifyNoInteractions(managementQueryService);
    }

    @Test
    void fixedViewSessionEndpointUsesRequiredDefaultPagination() throws NoSuchMethodException {
        Method method = ChatManagementController.class.getMethod(
                "findAgentViewSessions",
                String.class,
                String.class,
                String.class,
                long.class,
                long.class
        );

        RequestParam pageNo = (RequestParam) method.getParameters()[3]
                .getAnnotation(RequestParam.class);
        RequestParam pageSize = (RequestParam) method.getParameters()[4]
                .getAnnotation(RequestParam.class);

        assertEquals("1", pageNo.defaultValue());
        assertEquals("20", pageSize.defaultValue());
    }

    @Test
    void sessionUserCanReadTransferHistoryWithoutAgentOnlyPermission() {
        List<SessionTransferLogVO> logs = List.of();
        when(currentUser.getUserId()).thenReturn("U001");
        when(managementQueryService.findTransferLogs("U001", false, "S001")).thenReturn(logs);

        Result<List<SessionTransferLogVO>> result = controller.findTransferLogs("S001");

        assertEquals(logs, result.getData());
        verify(currentUser).requireRole("USER");
        verify(currentUser, never()).requirePermission("chat:session:transfer-log:view");
        verify(managementQueryService).findTransferLogs("U001", false, "S001");
    }

    @Test
    void adminCanReadReportOverviewWithDashboardPermission() {
        LocalDateTime from = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 9, 1, 0, 0);
        AdminReportOverviewVO overview = new AdminReportOverviewVO(
                List.of(), List.of(), null, List.of(), null, List.of()
        );
        when(managementQueryService.findAdminReportOverview(org.mockito.ArgumentMatchers.any()))
                .thenReturn(overview);

        Result<AdminReportOverviewVO> result = controller.findAdminReportOverview(from, to, "WEEK");

        assertEquals(overview, result.getData());
        verify(currentUser).requireRole("ADMIN");
        verify(currentUser).requirePermission("chat:admin:dashboard:view");
        verify(managementQueryService).findAdminReportOverview(org.mockito.ArgumentMatchers.argThat(query ->
                from.equals(query.getFrom()) && to.equals(query.getTo()) && "WEEK".equals(query.getGranularity())
        ));
    }

    @Test
    void adminEndsActiveSessionBeforePermanentlyDeletingIt() {
        ChatSession session = new ChatSession();
        session.setStatus("ACTIVE");
        session.setAgentId("A001");
        when(sessionMapper.selectById("S001")).thenReturn(session);

        controller.deleteAdminSession("S001");

        verify(sessionOperations).endSessionByAgent("S001", "A001");
        verify(sessionDeletionService).deleteSession("S001");
        verify(currentUser).requireRole("ADMIN");
        verify(currentUser).requirePermission("chat:session:audit:view");
    }
}
