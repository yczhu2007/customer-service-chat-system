package com.example.customerservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FrontendForwardingControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new FrontendForwardingController())
            .build();

    @Test
    void forwardsFrontendLoginRouteToVueEntryPage() throws Exception {
        mockMvc.perform(get("/frontend/login"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/frontend/index.html"));
    }
}
