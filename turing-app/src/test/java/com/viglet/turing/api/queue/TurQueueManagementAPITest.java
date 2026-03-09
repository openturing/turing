package com.viglet.turing.api.queue;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TurQueueManagementAPITest {

    private MockMvc mockMvc;

    @Mock
    private TurQueueManagementService queueManagementService;

    @InjectMocks
    private TurQueueManagementAPI turQueueManagementAPI;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(turQueueManagementAPI).build();
    }

    @Test
    void testListQueues() throws Exception {
        TurQueueInfo queueInfo = new TurQueueInfo();
        queueInfo.setName("testQueue");

        when(queueManagementService.getAllQueues()).thenReturn(Collections.singletonList(queueInfo));

        mockMvc.perform(get("/api/artemis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("testQueue"));

        verify(queueManagementService, times(1)).getAllQueues();
    }

    @Test
    void testListQueuesException() throws Exception {
        when(queueManagementService.getAllQueues()).thenThrow(new RuntimeException("Error"));

        mockMvc.perform(get("/api/artemis"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testGetQueueMessages() throws Exception {
        TurQueueMessage message = new TurQueueMessage();
        message.setMessageId("1");
        message.setContent("content");

        when(queueManagementService.getQueueMessages(eq("testQueue"), anyInt()))
                .thenReturn(Collections.singletonList(message));

        mockMvc.perform(get("/api/artemis/testQueue/messages").param("maxMessages", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].messageId").value("1"))
                .andExpect(jsonPath("$[0].content").value("content"));

        verify(queueManagementService, times(1)).getQueueMessages("testQueue", 10);
    }

    @Test
    void testGetQueueMessagesException() throws Exception {
        when(queueManagementService.getQueueMessages(anyString(), anyInt()))
                .thenThrow(new RuntimeException("Error"));

        mockMvc.perform(get("/api/artemis/testQueue/messages"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testPauseQueue_Success() throws Exception {
        when(queueManagementService.pauseQueue("testQueue")).thenReturn(true);

        mockMvc.perform(post("/api/artemis/testQueue/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.queueName").value("testQueue"));
    }

    @Test
    void testPauseQueue_Failure() throws Exception {
        when(queueManagementService.pauseQueue("testQueue")).thenReturn(false);

        mockMvc.perform(post("/api/artemis/testQueue/pause"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testResumeQueue_Success() throws Exception {
        when(queueManagementService.resumeQueue("testQueue")).thenReturn(true);

        mockMvc.perform(post("/api/artemis/testQueue/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testResumeQueue_Failure() throws Exception {
        when(queueManagementService.resumeQueue("testQueue")).thenReturn(false);

        mockMvc.perform(post("/api/artemis/testQueue/resume"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testStartQueue_Success() throws Exception {
        // start is an alias to resume
        when(queueManagementService.resumeQueue("testQueue")).thenReturn(true);

        mockMvc.perform(post("/api/artemis/testQueue/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testStopQueue_Success() throws Exception {
        // stop is an alias to pause
        when(queueManagementService.pauseQueue("testQueue")).thenReturn(true);

        mockMvc.perform(post("/api/artemis/testQueue/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testClearQueue_Success() throws Exception {
        when(queueManagementService.clearQueue("testQueue")).thenReturn(true);

        mockMvc.perform(delete("/api/artemis/testQueue/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testClearQueue_Failure() throws Exception {
        when(queueManagementService.clearQueue("testQueue")).thenReturn(false);

        mockMvc.perform(delete("/api/artemis/testQueue/messages"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testGetQueueInfo_Found() throws Exception {
        TurQueueInfo queueInfo = new TurQueueInfo();
        queueInfo.setName("testQueue");

        when(queueManagementService.getAllQueues()).thenReturn(Collections.singletonList(queueInfo));

        mockMvc.perform(get("/api/artemis/testQueue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("testQueue"));
    }

    @Test
    void testGetQueueInfo_NotFound() throws Exception {
        when(queueManagementService.getAllQueues()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/artemis/testQueue"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetQueueInfo_Exception() throws Exception {
        when(queueManagementService.getAllQueues()).thenThrow(new RuntimeException("Error"));

        mockMvc.perform(get("/api/artemis/testQueue"))
                .andExpect(status().isInternalServerError());
    }
}
