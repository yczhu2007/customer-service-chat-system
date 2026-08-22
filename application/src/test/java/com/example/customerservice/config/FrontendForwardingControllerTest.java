package com.example.customerservice.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FrontendForwardingControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new FrontendForwardingController())
            .build();

    @ParameterizedTest
    @ValueSource(strings = {
            "/frontend/login", "/frontend/user/tickets/123",
            "/user", "/user/tickets/123",
            "/agent", "/agent/queues/active",
            "/admin", "/admin/sessions"
    })
    void forwardsSpaRoutesToVueEntryPage(String route) throws Exception {
        mockMvc.perform(get(route))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/frontend/index.html"));
    }
}
