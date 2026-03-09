package com.viglet.turing.api.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TurIntegrationVendorAPITest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TurIntegrationVendorAPI api = new TurIntegrationVendorAPI();
        mockMvc = MockMvcBuilders.standaloneSetup(api).build();
    }

    @Test
    void testTurIntegrationVendorList() throws Exception {
        mockMvc.perform(get("/api/integration/vendor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("AEM"))
                .andExpect(jsonPath("$[0].title").value("AEM"))
                .andExpect(jsonPath("$[1].id").value("WEB-CRAWLER"))
                .andExpect(jsonPath("$[1].title").value("Web Crawler"));
    }
}
