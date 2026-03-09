package com.viglet.turing.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TurAPITest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TurAPI turAPI = new TurAPI();
        mockMvc = MockMvcBuilders.standaloneSetup(turAPI).build();
    }

    @Test
    void testInfo() throws Exception {
        mockMvc.perform(get("/api/v2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
