package com.viglet.turing.api.agent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.persistence.dto.agent.TurAIAgentDto;
import com.viglet.turing.persistence.mapper.agent.TurAIAgentMapper;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.mcp.TurMcpServerRepository;
import com.viglet.turing.persistence.repository.sn.genai.TurSNSiteGenAiRepository;

/**
 * Tests for TurAIAgentAPI.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurAIAgentAPITest {

    private MockMvc mockMvc;

    @Mock
    private TurAIAgentRepository turAIAgentRepository;

    @Spy
    private TurAIAgentMapper turAIAgentMapper = Mappers.getMapper(TurAIAgentMapper.class);

    @Mock
    private TurLLMInstanceRepository turLLMInstanceRepository;

    @Mock
    private TurMcpServerRepository turMcpServerRepository;

    @Mock
    private TurSNSiteGenAiRepository turSNSiteGenAiRepository;

    @InjectMocks
    private TurAIAgentAPI turAIAgentAPI;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(turAIAgentAPI).build();
    }

    @Test
    void testTurAIAgentList() throws Exception {
        TurAIAgent agent1 = new TurAIAgent();
        agent1.setId("1");
        agent1.setTitle("Agent 1");

        TurAIAgent agent2 = new TurAIAgent();
        agent2.setId("2");
        agent2.setTitle("Agent 2");

        when(turAIAgentRepository.findAll(any(Sort.class))).thenReturn(Arrays.asList(agent1, agent2));

        mockMvc.perform(get("/api/ai-agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[0].title").value("Agent 1"))
                .andExpect(jsonPath("$[1].id").value("2"))
                .andExpect(jsonPath("$[1].title").value("Agent 2"));

        verify(turAIAgentRepository, times(1)).findAll(any(Sort.class));
    }

    @Test
    void testTurAIAgentStructure() throws Exception {
        mockMvc.perform(get("/api/ai-agent/structure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void testTurAIAgentGetFound() throws Exception {
        TurAIAgent agent = new TurAIAgent();
        agent.setId("1");
        agent.setTitle("Test Agent");
        agent.setEnabled(1);

        when(turAIAgentRepository.findById("1")).thenReturn(Optional.of(agent));

        mockMvc.perform(get("/api/ai-agent/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.title").value("Test Agent"))
                .andExpect(jsonPath("$.enabled").value(1));

        verify(turAIAgentRepository, times(1)).findById("1");
    }

    @Test
    void testTurAIAgentGetNotFound() throws Exception {
        when(turAIAgentRepository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/ai-agent/missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist());

        verify(turAIAgentRepository, times(1)).findById("missing");
    }

    @Test
    void testTurAIAgentUpdateFound() throws Exception {
        TurAIAgent existingAgent = new TurAIAgent();
        existingAgent.setId("1");
        existingAgent.setTitle("Old Title");

        TurAIAgentDto updatedDto = new TurAIAgentDto();
        updatedDto.setTitle("New Title");
        updatedDto.setDescription("New Description");
        updatedDto.setEnabled(1);

        when(turAIAgentRepository.findById("1")).thenReturn(Optional.of(existingAgent));

        mockMvc.perform(put("/api/ai-agent/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatedDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Title"))
                .andExpect(jsonPath("$.description").value("New Description"));

        verify(turAIAgentRepository, times(1)).save(any(TurAIAgent.class));
    }

    @Test
    void testTurAIAgentUpdateNotFound() throws Exception {
        TurAIAgentDto dto = new TurAIAgentDto();
        dto.setTitle("New Title");

        when(turAIAgentRepository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/ai-agent/missing")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void testTurAIAgentDelete() throws Exception {
        mockMvc.perform(delete("/api/ai-agent/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(turAIAgentRepository, times(1)).delete("1");
    }

    @Test
    void testTurAIAgentAdd() throws Exception {
        TurAIAgentDto newAgent = new TurAIAgentDto();
        newAgent.setTitle("New Agent");
        newAgent.setEnabled(1);

        mockMvc.perform(post("/api/ai-agent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newAgent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Agent"));

        verify(turAIAgentRepository, times(1)).save(any(TurAIAgent.class));
    }
}
