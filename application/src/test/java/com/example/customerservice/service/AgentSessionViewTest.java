package com.example.customerservice.service;

import com.example.customerservice.constant.AgentSessionView;
import com.example.customerservice.dto.AgentSessionViewCountVO;
import com.example.customerservice.dto.AgentSessionViewVO;
import com.example.customerservice.dto.ChatSessionListItemVO;
import com.example.customerservice.dto.PageResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentSessionViewTest {

    @Test
    void fixedViewsExposeStableCodesAndDisplayLabels() {
        assertEquals(
                List.of(
                        "MY_ACTIVE",
                        "MY_UNREAD",
                        "MY_HIGH_PRIORITY",
                        "MY_UNARCHIVED",
                        "MY_RECENT_CLOSED"
                ),
                List.of(
                        AgentSessionView.MY_ACTIVE.getCode(),
                        AgentSessionView.MY_UNREAD.getCode(),
                        AgentSessionView.MY_HIGH_PRIORITY.getCode(),
                        AgentSessionView.MY_UNARCHIVED.getCode(),
                        AgentSessionView.MY_RECENT_CLOSED.getCode()
                )
        );
        assertEquals("我的处理中", AgentSessionView.MY_ACTIVE.getLabel());
        assertEquals("我的未读", AgentSessionView.MY_UNREAD.getLabel());
        assertEquals("我的高优先级", AgentSessionView.MY_HIGH_PRIORITY.getLabel());
        assertEquals("我的未归档", AgentSessionView.MY_UNARCHIVED.getLabel());
        assertEquals("我最近关闭", AgentSessionView.MY_RECENT_CLOSED.getLabel());
    }

    @Test
    void fromCodeAcceptsTrimmedCaseInsensitiveInput() {
        assertEquals(
                AgentSessionView.MY_UNREAD,
                AgentSessionView.fromCode("  my_unread ")
        );
    }

    @Test
    void fromCodeRejectsUnknownValue() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> AgentSessionView.fromCode("QUEUE_ALL")
        );

        assertEquals(
                "坐席会话视图只支持 MY_ACTIVE、MY_UNREAD、MY_HIGH_PRIORITY、MY_UNARCHIVED、MY_RECENT_CLOSED",
                error.getMessage()
        );
    }

    @Test
    void serviceContractExposesFixedViewQueries() throws NoSuchMethodException {
        Method findViews = ChatManagementQueryService.class.getMethod(
                "findAgentSessionViews",
                String.class
        );
        assertEquals(List.class, findViews.getReturnType());
        assertInstanceOf(ParameterizedType.class, findViews.getGenericReturnType());
        ParameterizedType findViewsReturnType =
                (ParameterizedType) findViews.getGenericReturnType();
        assertEquals(
                AgentSessionViewCountVO.class,
                findViewsReturnType.getActualTypeArguments()[0]
        );

        Method findViewSessions = ChatManagementQueryService.class.getMethod(
                "findAgentViewSessions",
                String.class,
                AgentSessionView.class,
                long.class,
                long.class
        );
        assertEquals(PageResult.class, findViewSessions.getReturnType());
        assertInstanceOf(ParameterizedType.class, findViewSessions.getGenericReturnType());
        ParameterizedType findViewSessionsReturnType =
                (ParameterizedType) findViewSessions.getGenericReturnType();
        assertEquals(
                ChatSessionListItemVO.class,
                findViewSessionsReturnType.getActualTypeArguments()[0]
        );
    }

    @Test
    void viewDtosPreserveCodeLabelAndCountFields() {
        AgentSessionViewVO view = new AgentSessionViewVO("MY_ACTIVE", "我的处理中");
        AgentSessionViewCountVO countView = new AgentSessionViewCountVO(
                "MY_UNREAD",
                "我的未读",
                3L
        );

        assertEquals("MY_ACTIVE", view.code());
        assertEquals("我的处理中", view.label());
        assertEquals("MY_UNREAD", countView.code());
        assertEquals("我的未读", countView.label());
        assertEquals(3L, countView.count());
    }
}
