package com.example.customerservice.controller;

import com.example.customerservice.constant.SessionParticipantType;
import com.example.customerservice.common.Result;
import com.example.customerservice.dto.ChatSessionMetadataUpdateDTO;
import com.example.customerservice.dto.ChatSessionMetadataVO;
import com.example.customerservice.dto.ChatSessionListItemVO;
import com.example.customerservice.dto.ChatHistoryPage;
import com.example.customerservice.dto.HistoryRequest;
import com.example.customerservice.dto.PageResult;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.service.ChatSessionQueryService;
import com.example.customerservice.service.IAuthenticationService;
import com.example.customerservice.service.ChatAgentOperations;
import com.example.customerservice.service.ChatMessageOperations;
import com.example.customerservice.service.ChatPresenceOperations;
import com.example.customerservice.service.ChatRoutingOperations;
import com.example.customerservice.service.ChatSessionOperations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock private ChatRoutingOperations chatRoutingOperations;
    @Mock private ChatAgentOperations chatAgentOperations;
    @Mock private ChatMessageOperations chatMessageOperations;
    @Mock private ChatSessionOperations chatSessionOperations;
    @Mock private ChatPresenceOperations chatPresenceOperations;
    @Mock private IAuthenticationService authenticationService;
    @Mock private CurrentUser currentUser;
    @Mock private ChatSessionQueryService chatSessionQueryService;
    @InjectMocks private ChatController controller;

    @Test
    void ordinaryUserCanStartConsultationExplicitly() {
        Principal principal = () -> "U001";
        when(authenticationService.findRoleCodesByUserId("U001"))
                .thenReturn(Set.of("USER"));

        controller.startConsultation(principal);

        verify(authenticationService).requirePermission(
                "U001",
                "chat:user:access"
        );
        verify(chatRoutingOperations).onUserConnected("U001");
    }

    @Test
    void historyResponseEchoesTheClientRequestId() {
        HistoryRequest request = new HistoryRequest();
        request.setSessionId("S001");
        request.setRequestId("history-1");
        request.setPageSize(20);
        when(chatMessageOperations.getHistory("S001", "U001", null, 20))
                .thenReturn(new ChatHistoryPage(List.of(), 0, 20, null, false, 0));

        Map<String, Object> response = controller.getHistory(request, () -> "U001");

        assertEquals("history-1", response.get("requestId"));
    }

    @Test
    void agentCannotUseUserConsultationEntry() {
        Principal principal = () -> "A001";
        when(authenticationService.findRoleCodesByUserId("A001"))
                .thenReturn(Set.of("AGENT"));

        assertThrows(
                IllegalArgumentException.class,
                () -> controller.startConsultation(principal)
        );

        verify(chatRoutingOperations, never()).onUserConnected("A001");
    }

    @Test
    void userSessionEndpointUsesUserScope() {
        PageResult<ChatSessionListItemVO> page = new PageResult<>(1, 20, 0, 0, List.of());
        when(currentUser.getRoleCodes()).thenReturn(Set.of("USER"));
        when(currentUser.getUserId()).thenReturn("U001");
        when(chatSessionQueryService.findMySessions(
                "U001",
                SessionParticipantType.USER,
                "OPEN",
                "NONE",
                1,
                20
        )).thenReturn(page);

        Result<PageResult<ChatSessionListItemVO>> result =
                controller.findMySessions("OPEN", "NONE", 1, 20);

        assertEquals(page, result.getData());
        verify(currentUser).requirePermission("chat:session:view-own");
        verify(chatSessionQueryService).findMySessions(
                "U001",
                SessionParticipantType.USER,
                "OPEN",
                "NONE",
                1,
                20
        );
    }

    @Test
    void agentSessionEndpointUsesAgentScope() {
        PageResult<ChatSessionListItemVO> page = new PageResult<>(1, 20, 0, 0, List.of());
        when(currentUser.getRoleCodes()).thenReturn(Set.of("AGENT"));
        when(currentUser.getUserId()).thenReturn("A001");
        when(chatSessionQueryService.findMySessions(
                "A001",
                SessionParticipantType.AGENT,
                null,
                null,
                1,
                20
        )).thenReturn(page);

        Result<PageResult<ChatSessionListItemVO>> result =
                controller.findMySessions(null, null, 1, 20);

        assertEquals(page, result.getData());
        verify(currentUser).requirePermission("chat:session:view-own");
        verify(chatSessionQueryService).findMySessions(
                "A001",
                SessionParticipantType.AGENT,
                null,
                null,
                1,
                20
        );
    }

    @Test
    void dualRoleSessionEndpointPrefersAgentScope() {
        PageResult<ChatSessionListItemVO> page = new PageResult<>(1, 20, 0, 0, List.of());
        when(currentUser.getRoleCodes()).thenReturn(Set.of("USER", "AGENT"));
        when(currentUser.getUserId()).thenReturn("UA001");
        when(chatSessionQueryService.findMySessions(
                "UA001",
                SessionParticipantType.AGENT,
                null,
                null,
                1,
                20
        )).thenReturn(page);

        controller.findMySessions(null, null, 1, 20);

        verify(chatSessionQueryService).findMySessions(
                "UA001",
                SessionParticipantType.AGENT,
                null,
                null,
                1,
                20
        );
    }

    @Test
    void adminCannotUseParticipantSessionEndpoint() {
        when(currentUser.getRoleCodes()).thenReturn(Set.of("ADMIN"));
        when(currentUser.getUserId()).thenReturn("ADMIN001");

        assertThrows(
                IllegalArgumentException.class,
                () -> controller.findMySessions(null, null, 1, 20)
        );

        verify(currentUser).requirePermission("chat:session:view-own");
        verifyNoInteractions(chatSessionQueryService);
    }

    @Test
    void adminCanReadSessionMetadataViaAuditScope() {
        ChatSessionMetadataVO metadata = metadata("S001");
        when(currentUser.getRoleCodes()).thenReturn(Set.of("ADMIN"));
        when(currentUser.getUserId()).thenReturn("ADMIN001");
        when(chatSessionQueryService.getSessionMetadata("ADMIN001", true, "S001"))
                .thenReturn(metadata);

        Result<ChatSessionMetadataVO> result =
                controller.getSessionMetadata("S001");

        assertEquals(metadata, result.getData());
        verify(currentUser).requireRole("ADMIN");
        verify(currentUser).requirePermission("chat:session:audit:view");
        verify(chatSessionQueryService).getSessionMetadata("ADMIN001", true, "S001");
    }

    @Test
    void participantCanReadSessionMetadataViaOwnScope() {
        ChatSessionMetadataVO metadata = metadata("S001");
        when(currentUser.getRoleCodes()).thenReturn(Set.of("AGENT"));
        when(currentUser.getUserId()).thenReturn("A001");
        when(chatSessionQueryService.getSessionMetadata("A001", false, "S001"))
                .thenReturn(metadata);

        Result<ChatSessionMetadataVO> result =
                controller.getSessionMetadata("S001");

        assertEquals(metadata, result.getData());
        verify(currentUser).requirePermission("chat:session:view-own");
        verify(chatSessionQueryService).getSessionMetadata("A001", false, "S001");
    }

    @Test
    void unsupportedRoleCannotReadSessionMetadata() {
        when(currentUser.getRoleCodes()).thenReturn(Set.of("SUPERVISOR"));

        assertThrows(
                IllegalArgumentException.class,
                () -> controller.getSessionMetadata("S001")
        );

        verifyNoInteractions(chatSessionQueryService);
    }

    @Test
    void agentCanUpdateSessionMetadata() {
        ChatSessionMetadataUpdateDTO request = metadataUpdateRequest();
        ChatSessionMetadataVO metadata = metadata("S001");
        when(currentUser.getUserId()).thenReturn("A001");
        when(chatSessionQueryService.updateSessionMetadata("A001", "S001", request))
                .thenReturn(metadata);

        Result<ChatSessionMetadataVO> result =
                controller.updateSessionMetadata("S001", request);

        assertEquals(metadata, result.getData());
        verify(currentUser).requireRole("AGENT");
        verify(currentUser).requirePermission("chat:session:metadata:update");
        verify(chatSessionQueryService).updateSessionMetadata("A001", "S001", request);
    }

    @Test
    void adminCannotUpdateSessionMetadata() {
        ChatSessionMetadataUpdateDTO request = metadataUpdateRequest();
        when(currentUser.getRoleCodes()).thenReturn(Set.of("ADMIN"));

        assertThrows(
                IllegalArgumentException.class,
                () -> controller.updateSessionMetadata("S001", request)
        );

        verifyNoInteractions(chatSessionQueryService);
    }

    private static ChatSessionMetadataVO metadata(String sessionId) {
        ChatSessionMetadataVO metadata = new ChatSessionMetadataVO();
        metadata.setSessionId(sessionId);
        metadata.setTitle("Priority case");
        metadata.setPriority("HIGH");
        metadata.setCategory("PAYMENT");
        metadata.setTags(List.of("vip"));
        return metadata;
    }

    private static ChatSessionMetadataUpdateDTO metadataUpdateRequest() {
        ChatSessionMetadataUpdateDTO request = new ChatSessionMetadataUpdateDTO();
        request.setTitle("Updated title");
        request.setPriority("URGENT");
        request.setCategory("TECHNICAL");
        request.setTags(List.of("vip", "billing"));
        return request;
    }
}
