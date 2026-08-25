package com.example.customerservice.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.customerservice.constant.ChatConstants;
import com.example.customerservice.constant.SessionParticipantType;
import com.example.customerservice.domain.ChatSession;
import com.example.customerservice.domain.ChatSessionTag;
import com.example.customerservice.dto.ChatSessionListItemVO;
import com.example.customerservice.dto.ChatSessionMetadataUpdateDTO;
import com.example.customerservice.dto.ChatSessionMetadataVO;
import com.example.customerservice.dto.PageResult;
import com.example.customerservice.dto.SessionArchiveDTO;
import com.example.customerservice.dto.SessionArchiveRemarkDTO;
import com.example.customerservice.exception.NotFoundException;
import com.example.customerservice.mapper.ChatMessageReadMapper;
import com.example.customerservice.mapper.ChatSessionMapper;
import com.example.customerservice.mapper.ChatSessionRatingMapper;
import com.example.customerservice.mapper.ChatSessionTagMapper;
import com.example.customerservice.mapper.SysUserMapper;
import com.example.customerservice.repository.ChatRedisRepository;
import com.example.customerservice.service.impl.ChatSessionQueryServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionMetadataTest {

    @Mock private ChatSessionMapper sessionMapper;
    @Mock private ChatSessionRatingMapper ratingMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private ChatMessageReadMapper messageReadMapper;
    @Mock private ChatRedisRepository chatRedisRepository;
    @Mock private ChatSessionTagMapper tagMapper;

    private ChatSessionQueryServiceImpl service;

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(
                        new MybatisConfiguration(),
                        "ChatSessionMetadataTest"
                ),
                ChatSession.class
        );
    }

    @BeforeEach
    void setUp() {
        service = new ChatSessionQueryServiceImpl(
                sessionMapper,
                ratingMapper,
                userMapper,
                messageReadMapper,
                chatRedisRepository,
                tagMapper,
                300L
        );
    }

    @Test
    void sessionUserAssignedAgentAndAdministratorCanReadMetadata() {
        ChatSession session = session("S001", "U001", "A001");
        session.setTitle("Payment question");
        session.setPriority(ChatConstants.PRIORITY_HIGH);
        session.setCategory(ChatConstants.CATEGORY_PAYMENT);
        session.setMetadataUpdatedAt(LocalDateTime.of(2026, 8, 21, 9, 30));
        when(sessionMapper.selectById("S001")).thenReturn(session);
        when(tagMapper.selectBySessionId("S001"))
                .thenReturn(List.of(tag("S001", "billing"), tag("S001", "vip")));

        ChatSessionMetadataVO userView = service.getSessionMetadata("U001", false, "S001");
        ChatSessionMetadataVO agentView = service.getSessionMetadata("A001", false, "S001");
        ChatSessionMetadataVO administratorView = service.getSessionMetadata("ADMIN001", true, "S001");

        assertMetadata(userView, session, List.of("billing", "vip"));
        assertMetadata(agentView, session, List.of("billing", "vip"));
        assertMetadata(administratorView, session, List.of("billing", "vip"));
    }

    @Test
    void missingOrUnrelatedSessionCannotBeRead() {
        when(sessionMapper.selectById("missing")).thenReturn(null);
        when(sessionMapper.selectById("S001")).thenReturn(session("S001", "U001", "A001"));

        assertThrows(
                NotFoundException.class,
                () -> service.getSessionMetadata("U001", false, "missing")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getSessionMetadata("U999", false, "S001")
        );

        verifyNoInteractions(tagMapper);
    }

    @Test
    void assignedAgentUpdateNormalizesAndReplacesTagsAndSetsTimestamp() {
        ChatSession session = session("S001", "U001", "A001");
        when(sessionMapper.selectById("S001")).thenReturn(session);
        when(sessionMapper.updateById(session)).thenReturn(1);
        when(tagMapper.insert(any(ChatSessionTag.class))).thenReturn(1);
        ChatSessionMetadataUpdateDTO request = request(
                "Updated title",
                ChatConstants.PRIORITY_URGENT,
                ChatConstants.CATEGORY_TECHNICAL,
                List.of(" VIP ", "vip", " I ")
        );
        Locale previousLocale = Locale.getDefault();
        LocalDateTime beforeUpdate = LocalDateTime.now();

        ChatSessionMetadataVO result;
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            result = service.updateSessionMetadata("A001", "S001", request);
        } finally {
            Locale.setDefault(previousLocale);
        }

        assertEquals("Updated title", session.getTitle());
        assertEquals(ChatConstants.PRIORITY_URGENT, session.getPriority());
        assertEquals(ChatConstants.CATEGORY_TECHNICAL, session.getCategory());
        assertNotNull(session.getMetadataUpdatedAt());
        assertFalse(session.getMetadataUpdatedAt().isBefore(beforeUpdate));
        assertMetadata(result, session, List.of("vip", "i"));

        InOrder replacementOrder = inOrder(tagMapper);
        replacementOrder.verify(tagMapper).deleteBySessionId("S001");
        ArgumentCaptor<ChatSessionTag> insertedTags = ArgumentCaptor.forClass(ChatSessionTag.class);
        replacementOrder.verify(tagMapper, org.mockito.Mockito.times(2)).insert(insertedTags.capture());
        assertEquals(List.of("vip", "i"), insertedTags.getAllValues().stream()
                .map(ChatSessionTag::getTag)
                .toList());
    }

    @Test
    void nonOwnerCannotUpdateMetadata() {
        when(sessionMapper.selectById("S001")).thenReturn(session("S001", "U001", "A001"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateSessionMetadata(
                        "A999",
                        "S001",
                        request("Title", ChatConstants.PRIORITY_NORMAL, null, List.of("tag"))
                )
        );

        verify(sessionMapper, never()).updateById(any(ChatSession.class));
        verifyNoInteractions(tagMapper);
    }

    @Test
    void updateRejectsInvalidMetadataAndNormalizedTagLimits() {
        when(sessionMapper.selectById("S001")).thenReturn(session("S001", "U001", "A001"));

        assertThrows(IllegalArgumentException.class, () -> service.updateSessionMetadata(
                "A001", "S001", request(" ", ChatConstants.PRIORITY_NORMAL, null, List.of())));
        assertThrows(IllegalArgumentException.class, () -> service.updateSessionMetadata(
                "A001", "S001", request("Title", "INVALID", null, List.of())));
        assertThrows(IllegalArgumentException.class, () -> service.updateSessionMetadata(
                "A001", "S001", request("Title", ChatConstants.PRIORITY_NORMAL, "INVALID", List.of())));
        assertThrows(IllegalArgumentException.class, () -> service.updateSessionMetadata(
                "A001", "S001", request("Title", ChatConstants.PRIORITY_NORMAL, null, List.of(" "))));
        assertThrows(IllegalArgumentException.class, () -> service.updateSessionMetadata(
                "A001", "S001", request("Title", ChatConstants.PRIORITY_NORMAL, null,
                        List.of("x".repeat(ChatConstants.SESSION_TAG_MAX_LENGTH + 1)))));
        assertThrows(IllegalArgumentException.class, () -> service.updateSessionMetadata(
                "A001", "S001", request("Title", ChatConstants.PRIORITY_NORMAL, null,
                        IntStream.rangeClosed(1, ChatConstants.SESSION_TAG_MAX_COUNT + 1)
                                .mapToObj(i -> "tag-" + i)
                                .toList())));

        verify(sessionMapper, never()).updateById(any(ChatSession.class));
        verifyNoInteractions(tagMapper);
    }

    @Test
    void participantListMapsMetadataWithOneSetBasedTagQuery() {
        ChatSession first = session("S001", "U001", "A001");
        first.setTitle("First");
        first.setPriority(ChatConstants.PRIORITY_HIGH);
        first.setCategory(ChatConstants.CATEGORY_ACCOUNT);
        first.setMetadataUpdatedAt(LocalDateTime.of(2026, 8, 21, 10, 0));
        ChatSession second = session("S002", "U001", "A002");
        second.setTitle("Second");
        second.setPriority(ChatConstants.PRIORITY_NORMAL);
        second.setCategory(null);
        Page<ChatSession> page = new Page<>(1, 20);
        page.setRecords(List.of(first, second));
        page.setTotal(2);
        when(sessionMapper.selectPage(any(Page.class), any())).thenReturn(page);
        when(messageReadMapper.countUnreadBySessions(List.of("S001", "S002"), "U001"))
                .thenReturn(Collections.emptyList());
        when(tagMapper.selectBySessionIds(List.of("S001", "S002")))
                .thenReturn(List.of(tag("S001", "account"), tag("S001", "vip")));

        PageResult<ChatSessionListItemVO> result = service.findMySessions(
                "U001", SessionParticipantType.USER, null, null, 1, 20);

        ChatSessionListItemVO firstItem = result.getRecords().get(0);
        assertEquals("First", firstItem.getTitle());
        assertEquals(ChatConstants.PRIORITY_HIGH, firstItem.getPriority());
        assertEquals(ChatConstants.CATEGORY_ACCOUNT, firstItem.getCategory());
        assertEquals(List.of("account", "vip"), firstItem.getTags());
        assertEquals(first.getMetadataUpdatedAt(), firstItem.getMetadataUpdatedAt());
        ChatSessionListItemVO secondItem = result.getRecords().get(1);
        assertEquals("Second", secondItem.getTitle());
        assertEquals(ChatConstants.PRIORITY_NORMAL, secondItem.getPriority());
        assertNull(secondItem.getCategory());
        assertEquals(List.of(), secondItem.getTags());
        assertNull(secondItem.getMetadataUpdatedAt());
        verify(tagMapper).selectBySessionIds(List.of("S001", "S002"));
        verify(tagMapper, never()).selectBySessionId(any());
    }

    @Test
    void emptyParticipantPageSkipsSetBasedTagLoading() {
        Page<ChatSession> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(sessionMapper.selectPage(any(Page.class), any())).thenReturn(page);

        PageResult<ChatSessionListItemVO> result = service.findMySessions(
                "U001", SessionParticipantType.USER, null, null, 1, 20);

        assertTrue(result.getRecords().isEmpty());
        verifyNoInteractions(tagMapper);
    }

    @Test
    void firstArchiveOfAnUnarchivedSessionUsesAnIsNullCondition() {
        ChatSession session = session("S001", "U001", "A001");
        session.setStatus(ChatConstants.SESSION_STATUS_CLOSED);
        when(sessionMapper.selectById("S001")).thenReturn(session);
        when(sessionMapper.update(any(ChatSession.class), any())).thenReturn(1);
        SessionArchiveDTO request = new SessionArchiveDTO();
        request.setArchiveStatus(ChatConstants.ARCHIVE_PENDING);

        service.setArchiveStatus("A001", "S001", request);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ChatSession>> wrapper =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(sessionMapper).update(org.mockito.ArgumentMatchers.any(ChatSession.class), wrapper.capture());
        assertTrue(wrapper.getValue().getSqlSegment().contains("archive_status IS NULL"));
    }

    @Test
    void assignedAgentCanReplaceTheSingleArchiveRemarkWithoutChangingArchiveStatus() {
        ChatSession session = session("S001", "U001", "A001");
        session.setStatus(ChatConstants.SESSION_STATUS_CLOSED);
        session.setArchiveStatus(ChatConstants.ARCHIVE_PENDING);
        session.setArchiveRemark("旧备注");
        when(sessionMapper.selectById("S001")).thenReturn(session);
        when(sessionMapper.update(any(ChatSession.class), any())).thenReturn(1);
        SessionArchiveRemarkDTO request = new SessionArchiveRemarkDTO();
        request.setRemark("新的唯一备注");

        service.saveArchiveRemark("A001", "S001", request);

        assertEquals("新的唯一备注", session.getArchiveRemark());
        assertEquals(ChatConstants.ARCHIVE_PENDING, session.getArchiveStatus());
        verify(sessionMapper).update(any(ChatSession.class), any());
    }

    private static ChatSession session(String id, String userId, String agentId) {
        ChatSession session = new ChatSession();
        session.setId(id);
        session.setUserId(userId);
        session.setAgentId(agentId);
        return session;
    }

    private static ChatSessionTag tag(String sessionId, String value) {
        ChatSessionTag tag = new ChatSessionTag();
        tag.setSessionId(sessionId);
        tag.setTag(value);
        return tag;
    }

    private static ChatSessionMetadataUpdateDTO request(
            String title,
            String priority,
            String category,
            List<String> tags
    ) {
        ChatSessionMetadataUpdateDTO request = new ChatSessionMetadataUpdateDTO();
        request.setTitle(title);
        request.setPriority(priority);
        request.setCategory(category);
        request.setTags(tags);
        return request;
    }

    private static void assertMetadata(
            ChatSessionMetadataVO actual,
            ChatSession expectedSession,
            List<String> expectedTags
    ) {
        assertEquals(expectedSession.getId(), actual.getSessionId());
        assertEquals(expectedSession.getTitle(), actual.getTitle());
        assertEquals(expectedSession.getPriority(), actual.getPriority());
        assertEquals(expectedSession.getCategory(), actual.getCategory());
        assertEquals(expectedTags, actual.getTags());
        assertEquals(expectedSession.getMetadataUpdatedAt(), actual.getMetadataUpdatedAt());
    }
}
