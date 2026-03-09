package com.viglet.turing.api.sn.queue;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.viglet.turing.sn.TurSNQueue;

@ExtendWith(MockitoExtension.class)
class TurSNMonitoringQueueTest {

    private MockMvc mockMvc;

    @Mock
    private TurSNQueue turSNQueue;

    @InjectMocks
    private TurSNMonitoringQueue turSNMonitoringQueue;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(turSNMonitoringQueue).build();
    }

    @Test
    void testTurMonitoringQueue() throws Exception {
        when(turSNQueue.getQueueSize()).thenReturn(10);

        mockMvc.perform(get("/api/queue"))
                .andExpect(status().isOk())
                .andExpect(content().string("Total 10 elements waiting in queue"));
    }
}
