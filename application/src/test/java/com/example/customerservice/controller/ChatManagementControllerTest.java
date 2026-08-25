package com.example.customerservice.controller;

import com.example.customerservice.common.Result;
import com.example.customerservice.constant.AgentSessionView;
import com.example.customerservice.dto.AgentSessionViewCountVO;
import com.example.customerservice.dto.ChatSessionListItemVO;
import com.example.customerservice.dto.PageResult;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.service.ChatManagementQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatManagementControllerTest {

    @Mock private ChatManagementQueryService managementQueryService;
    @Mock private CurrentUser currentUser;
    @InjectMocks private ChatManagementController controller;

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
                2,
                50
        )).thenReturn(page);

        Result<PageResult<ChatSessionListItemVO>> result =
                controller.findAgentViewSessions("MY_UNREAD", 2, 50);

        assertEquals(page, result.getData());
        verify(currentUser).requireRole("AGENT");
        verify(currentUser).requirePermission("chat:session:view-own");
        verify(managementQueryService).findAgentViewSessions(
                "A001",
                AgentSessionView.MY_UNREAD,
                2,
                50
        );
    }

    @Test
    void invalidViewCodeIsRejectedBeforeServiceCall() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.findAgentViewSessions("queue_all", 1, 20)
        );

        assertEquals(
                "坐席会话视图只支持 MY_ACTIVE、MY_UNREAD、MY_HIGH_PRIORITY、MY_UNARCHIVED、MY_RECENT_CLOSED、MY_ARCHIVED_COMPLETED、MY_ARCHIVED_PENDING、MY_ARCHIVED_ON_HOLD、MY_ARCHIVED_OTHER",
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
                long.class,
                long.class
        );

        RequestParam pageNo = (RequestParam) method.getParameters()[1]
                .getAnnotation(RequestParam.class);
        RequestParam pageSize = (RequestParam) method.getParameters()[2]
                .getAnnotation(RequestParam.class);

        assertEquals("1", pageNo.defaultValue());
        assertEquals("20", pageSize.defaultValue());
    }
}
