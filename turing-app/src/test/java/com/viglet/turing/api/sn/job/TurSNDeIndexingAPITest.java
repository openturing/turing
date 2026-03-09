package com.viglet.turing.api.sn.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.client.sn.job.TurSNJobItems;

@ExtendWith(MockitoExtension.class)
class TurSNDeIndexingAPITest {

    private MockMvc mockMvc;

    @Mock
    private TurSNImportAPI turSNImportAPI;

    @InjectMocks
    private TurSNDeIndexingAPI api;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(api).build();
    }

    @Test
    void testTurSNDesIndexingBroker() throws Exception {
        TurSNJobItems jobItems = new TurSNJobItems();

        mockMvc.perform(post("/api/sn/deindex")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(jobItems)))
                .andExpect(status().isOk())
                .andExpect(content().string("Ok"));

        verify(turSNImportAPI, times(1)).send(any(TurSNJobItems.class));
    }
}
