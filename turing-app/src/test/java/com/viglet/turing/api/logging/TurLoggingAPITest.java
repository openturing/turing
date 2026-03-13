package com.viglet.turing.api.logging;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TurLoggingAPITest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Set enabled=false so it does not connect to Mongo
        TurLoggingAPI turLoggingAPI = new TurLoggingAPI(false, "mongodb://localhost:27017", "turingLog", "server",
                "indexing", "aem");
        mockMvc = MockMvcBuilders.standaloneSetup(turLoggingAPI).build();
    }

    private static final String EMPTY_PAGE_RESPONSE =
            "{\"content\":[],\"page\":0,\"pageSize\":100,\"totalElements\":0,\"totalPages\":0}";

    @Test
    void testServerLogging() throws Exception {
        mockMvc.perform(get("/api/logging"))
                .andExpect(status().isOk())
                .andExpect(content().json(EMPTY_PAGE_RESPONSE));
    }

    @Test
    void testIndexingLogging() throws Exception {
        mockMvc.perform(get("/api/logging/indexing"))
                .andExpect(status().isOk())
                .andExpect(content().json(EMPTY_PAGE_RESPONSE));
    }

    @Test
    void testAemLogging() throws Exception {
        mockMvc.perform(get("/api/logging/aem"))
                .andExpect(status().isOk())
                .andExpect(content().json(EMPTY_PAGE_RESPONSE));
    }
}
