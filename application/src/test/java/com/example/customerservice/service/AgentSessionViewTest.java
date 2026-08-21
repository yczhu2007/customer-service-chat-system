package com.example.customerservice.service;

import com.example.customerservice.constant.AgentSessionView;
import com.example.customerservice.domain.ChatSessionTag;
import com.example.customerservice.dto.AgentSessionViewCountVO;
import com.example.customerservice.dto.AgentSessionViewVO;
import com.example.customerservice.dto.ChatSessionListItemVO;
import com.example.customerservice.dto.PageResult;
import com.example.customerservice.mapper.ChatManagementMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.ChatSessionTagMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.impl.ChatManagementQueryServiceImpl;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentSessionViewTest {

    @Mock private ChatManagementMapper managementMapper;
    @Mock private ChatSessionMapper sessionMapper;
    @Mock private ChatRedisRepository redisRepository;
    @Mock private ChatSessionTagMapper tagMapper;

    private ChatManagementQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChatManagementQueryServiceImpl(
                managementMapper,
                sessionMapper,
                redisRepository,
                tagMapper
        );
    }

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

    @Test
    void viewCountsBindAgentAndFollowEnumOrderWithMissingCountsAsZero() {
        when(managementMapper.countAgentSessionViews("A001"))
                .thenReturn(List.of(
                        count(AgentSessionView.MY_RECENT_CLOSED, 1L),
                        count(AgentSessionView.MY_UNREAD, 3L),
                        count(AgentSessionView.MY_ACTIVE, 2L)
                ));

        List<AgentSessionViewCountVO> result = service.findAgentSessionViews("A001");

        assertEquals(
                List.of(
                        "MY_ACTIVE",
                        "MY_UNREAD",
                        "MY_HIGH_PRIORITY",
                        "MY_UNARCHIVED",
                        "MY_RECENT_CLOSED"
                ),
                result.stream().map(AgentSessionViewCountVO::code).toList()
        );
        assertEquals(
                List.of(2L, 3L, 0L, 0L, 1L),
                result.stream().map(AgentSessionViewCountVO::count).toList()
        );
        verify(managementMapper).countAgentSessionViews("A001");
    }

    @Test
    void viewListBoundsPaginationAndLoadsTagsInOneBatch() {
        ChatSessionListItemVO record = new ChatSessionListItemVO();
        record.setSessionId("S001");
        record.setTitle("支付失败");
        record.setPriority("HIGH");
        record.setCategory("PAYMENT");
        record.setMetadataUpdatedAt(LocalDateTime.of(2026, 8, 21, 10, 0));
        when(managementMapper.countAgentViewSessions("A001", "MY_ACTIVE"))
                .thenReturn(101L);
        when(managementMapper.findAgentViewSessions(
                "A001", "MY_ACTIVE", 0L, 100L
        )).thenReturn(List.of(record));
        ChatSessionTag tag = new ChatSessionTag();
        tag.setSessionId("S001");
        tag.setTag("payment");
        when(tagMapper.selectBySessionIds(List.of("S001"))).thenReturn(List.of(tag));

        PageResult<ChatSessionListItemVO> result = service.findAgentViewSessions(
                "A001", AgentSessionView.MY_ACTIVE, 0L, 500L
        );

        assertEquals(1L, result.getPageNo());
        assertEquals(100L, result.getPageSize());
        assertEquals(101L, result.getTotal());
        assertEquals(2L, result.getPages());
        assertEquals(List.of("payment"), result.getRecords().get(0).getTags());
        verify(managementMapper).findAgentViewSessions(
                "A001", "MY_ACTIVE", 0L, 100L
        );
        verify(tagMapper).selectBySessionIds(List.of("S001"));
    }

    @Test
    void emptyViewPageReturnsEmptyRecordsWithoutListOrTagQueries() {
        when(managementMapper.countAgentViewSessions("A001", "MY_UNREAD"))
                .thenReturn(0L);

        PageResult<ChatSessionListItemVO> result = service.findAgentViewSessions(
                "A001", AgentSessionView.MY_UNREAD, 2L, 20L
        );

        assertEquals(2L, result.getPageNo());
        assertEquals(20L, result.getPageSize());
        assertEquals(0L, result.getTotal());
        assertEquals(0L, result.getPages());
        assertTrue(result.getRecords().isEmpty());
        verify(managementMapper, never()).findAgentViewSessions(
                "A001", "MY_UNREAD", 20L, 20L
        );
        verifyNoInteractions(tagMapper);
    }

    @Test
    void invalidAgentOrViewIsRejectedBeforeMapperAccess() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findAgentSessionViews("  ")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findAgentViewSessions("  ", AgentSessionView.MY_ACTIVE, 1L, 20L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findAgentViewSessions("A001", null, 1L, 20L)
        );

        verifyNoInteractions(managementMapper, tagMapper);
    }

    @Test
    void legacyConstructorFailsExplicitlyWhenViewTagsAreNeeded() {
        ChatManagementQueryServiceImpl legacyService =
                new ChatManagementQueryServiceImpl(
                        managementMapper,
                        sessionMapper,
                        redisRepository
                );
        ChatSessionListItemVO record = new ChatSessionListItemVO();
        record.setSessionId("S001");
        when(managementMapper.countAgentViewSessions("A001", "MY_ACTIVE"))
                .thenReturn(1L);
        when(managementMapper.findAgentViewSessions(
                "A001", "MY_ACTIVE", 0L, 20L
        )).thenReturn(List.of(record));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> legacyService.findAgentViewSessions(
                        "A001", AgentSessionView.MY_ACTIVE, 1L, 20L
                )
        );

        assertEquals(
                "Agent session views require the ChatSessionTagMapper dependency",
                error.getMessage()
        );
    }

    @ParameterizedTest
    @MethodSource("viewPredicates")
    void mapperSqlAppliesExactFixedPredicate(
            AgentSessionView view,
            String expectedPredicate
    ) throws IOException {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("agentId", "A001");
        parameters.put("viewCode", view.getCode());

        BoundSql boundSql = mappedSql("countAgentViewSessions", parameters);
        String sql = normalizeSql(boundSql.getSql());

        assertTrue(sql.contains("SESSION.AGENT_ID = ?"));
        assertTrue(sql.contains(expectedPredicate), sql);
        assertEquals(
                List.of("agentId", "agentId"),
                boundSql.getParameterMappings().stream()
                        .map(mapping -> mapping.getProperty())
                        .toList()
        );
    }

    @Test
    void listMapperUsesSetBasedUnreadAndLastMessageQueriesWithEnrichedFields()
            throws IOException {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("agentId", "A001");
        parameters.put("viewCode", "MY_ACTIVE");
        parameters.put("offset", 0L);
        parameters.put("pageSize", 20L);

        BoundSql boundSql = mappedSql("findAgentViewSessions", parameters);
        String sql = normalizeSql(boundSql.getSql());

        assertTrue(sql.contains("ROW_NUMBER() OVER (PARTITION BY MESSAGE.SESSION_ID"));
        assertTrue(sql.contains("LEFT JOIN UNREAD_BY_SESSION UNREAD"));
        assertTrue(sql.contains("SESSION.TITLE"));
        assertTrue(sql.contains("SESSION.PRIORITY"));
        assertTrue(sql.contains("SESSION.CATEGORY"));
        assertTrue(sql.contains("SESSION.METADATA_UPDATED_AT"));
        assertTrue(sql.contains("SESSION.ARCHIVE_STATUS"));
        assertTrue(sql.contains("SESSION.ARCHIVE_REMARK"));
        assertTrue(sql.contains("SESSION.ARCHIVED_AT"));
        assertTrue(sql.contains("LATEST.SENDER_ID AS LAST_MESSAGE_SENDER_ID"));
        assertTrue(sql.contains("COALESCE(UNREAD.UNREAD_COUNT, 0) AS UNREAD_COUNT"));
    }

    private static Stream<Arguments> viewPredicates() {
        return Stream.of(
                Arguments.of(
                        AgentSessionView.MY_ACTIVE,
                        "SESSION.STATUS = 'ACTIVE'"
                ),
                Arguments.of(
                        AgentSessionView.MY_UNREAD,
                        "COALESCE(UNREAD.UNREAD_COUNT, 0) > 0"
                ),
                Arguments.of(
                        AgentSessionView.MY_HIGH_PRIORITY,
                        "SESSION.STATUS = 'ACTIVE' AND SESSION.PRIORITY IN ('HIGH', 'URGENT')"
                ),
                Arguments.of(
                        AgentSessionView.MY_UNARCHIVED,
                        "SESSION.STATUS = 'CLOSED' AND SESSION.ARCHIVE_STATUS IS NULL"
                ),
                Arguments.of(
                        AgentSessionView.MY_RECENT_CLOSED,
                        "SESSION.STATUS = 'CLOSED' AND SESSION.END_TIME >= NOW() - INTERVAL 7 DAY"
                )
        );
    }

    private static AgentSessionViewCountVO count(
            AgentSessionView view,
            long count
    ) {
        return new AgentSessionViewCountVO(
                view.getCode(),
                view.getLabel(),
                count
        );
    }

    private static BoundSql mappedSql(
            String statementName,
            Map<String, Object> parameters
    ) throws IOException {
        Configuration configuration = new Configuration();
        String resource = "mapper/ChatManagementMapper.xml";
        try (Reader reader = Resources.getResourceAsReader(resource)) {
            new XMLMapperBuilder(
                    reader,
                    configuration,
                    resource,
                    configuration.getSqlFragments()
            ).parse();
        }
        return configuration.getMappedStatement(
                "com.example.customerservice.mapper.ChatManagementMapper."
                        + statementName
        ).getBoundSql(parameters);
    }

    private static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ")
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}
