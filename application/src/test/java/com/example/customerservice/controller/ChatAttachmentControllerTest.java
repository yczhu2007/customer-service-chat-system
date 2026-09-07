package com.example.customerservice.controller;

import com.example.customerservice.domain.ChatAttachment;
import com.example.customerservice.security.CurrentUser;
import com.example.customerservice.service.ChatAttachmentService;
import com.example.customerservice.service.AttachmentPreview;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatAttachmentControllerTest {
    @Test
    void returnsAccessibleAttachmentMetadataWithoutLoadingItsContent() throws Exception {
        ChatAttachmentService service = mock(ChatAttachmentService.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        ChatAttachment attachment = new ChatAttachment();
        attachment.setId("A1");
        attachment.setOriginalName("table.xlsx");
        attachment.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        attachment.setFileSize(2048L);
        attachment.setMessageType("FILE");
        when(currentUser.getUserId()).thenReturn("U1");
        when(service.requireAccessible("U1", "A1")).thenReturn(attachment);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ChatAttachmentController(service, currentUser)).build();

        mockMvc.perform(get("/chat/attachments/A1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalName").value("table.xlsx"))
                .andExpect(jsonPath("$.data.fileSize").value(2048));
    }

    @Test
    void returnsLegacyOfficePreviewAsInlinePdf() throws Exception {
        ChatAttachmentService service = mock(ChatAttachmentService.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.getUserId()).thenReturn("U1");
        when(service.preview("U1", "A1"))
                .thenReturn(new AttachmentPreview("schedule.pdf", "%PDF-preview".getBytes()));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ChatAttachmentController(service, currentUser)).build();

        mockMvc.perform(get("/chat/attachments/A1/preview"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")))
                .andExpect(content().bytes("%PDF-preview".getBytes()));
    }
}
