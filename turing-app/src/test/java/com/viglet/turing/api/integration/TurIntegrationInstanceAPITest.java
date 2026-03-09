package com.viglet.turing.api.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import java.util.List;
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
import com.viglet.turing.persistence.mapper.integration.TurIntegrationInstanceMapper;
import com.viglet.turing.persistence.model.integration.TurIntegrationInstance;
import com.viglet.turing.persistence.repository.integration.TurIntegrationInstanceRepository;

@ExtendWith(MockitoExtension.class)
class TurIntegrationInstanceAPITest {

    private MockMvc mockMvc;

    @Mock
    private TurIntegrationInstanceRepository turIntegrationInstanceRepository;

    @Spy
    private TurIntegrationInstanceMapper turIntegrationInstanceMapper = Mappers
            .getMapper(TurIntegrationInstanceMapper.class);

    @InjectMocks
    private TurIntegrationInstanceAPI turIntegrationInstanceAPI;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(turIntegrationInstanceAPI).build();
    }

    @Test
    void testTurIntegrationInstanceList() throws Exception {
        TurIntegrationInstance instance1 = new TurIntegrationInstance();
        instance1.setId("1");
        instance1.setTitle("Instance 1");

        TurIntegrationInstance instance2 = new TurIntegrationInstance();
        instance2.setId("2");
        instance2.setTitle("Instance 2");

        List<TurIntegrationInstance> instances = Arrays.asList(instance1, instance2);
        when(turIntegrationInstanceRepository.findAll(any(Sort.class))).thenReturn(instances);

        mockMvc.perform(get("/api/integration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[0].title").value("Instance 1"))
                .andExpect(jsonPath("$[1].id").value("2"))
                .andExpect(jsonPath("$[1].title").value("Instance 2"));

        verify(turIntegrationInstanceRepository, times(1)).findAll(any(Sort.class));
    }

    @Test
    void testTurIntegrationInstanceStructure() throws Exception {
        mockMvc.perform(get("/api/integration/structure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void testTurIntegrationInstanceGet_Found() throws Exception {
        TurIntegrationInstance instance = new TurIntegrationInstance();
        instance.setId("1");
        instance.setTitle("Instance 1");

        when(turIntegrationInstanceRepository.findById("1")).thenReturn(Optional.of(instance));

        mockMvc.perform(get("/api/integration/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.title").value("Instance 1"));

        verify(turIntegrationInstanceRepository, times(1)).findById("1");
    }

    @Test
    void testTurIntegrationInstanceGet_NotFound() throws Exception {
        when(turIntegrationInstanceRepository.findById("1")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/integration/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist());

        verify(turIntegrationInstanceRepository, times(1)).findById("1");
    }

    @Test
    void testTurIntegrationInstanceUpdate_Found() throws Exception {
        TurIntegrationInstance existingInstance = new TurIntegrationInstance();
        existingInstance.setId("1");
        existingInstance.setTitle("Old Title");

        TurIntegrationInstance updatedInstance = new TurIntegrationInstance();
        updatedInstance.setTitle("New Title");
        updatedInstance.setDescription("New Desc");
        updatedInstance.setVendor("New Vendor");
        updatedInstance.setEndpoint("http://newendpoint");
        updatedInstance.setEnabled(1);

        when(turIntegrationInstanceRepository.findById("1")).thenReturn(Optional.of(existingInstance));

        mockMvc.perform(put("/api/integration/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatedInstance)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Title"))
                .andExpect(jsonPath("$.description").value("New Desc"))
                .andExpect(jsonPath("$.vendor").value("New Vendor"))
                .andExpect(jsonPath("$.endpoint").value("http://newendpoint"))
                .andExpect(jsonPath("$.enabled").value(1));

        verify(turIntegrationInstanceRepository, times(1)).findById("1");
        verify(turIntegrationInstanceRepository, times(1)).save(any(TurIntegrationInstance.class));
    }

    @Test
    void testTurIntegrationInstanceUpdate_NotFound() throws Exception {
        TurIntegrationInstance updatedInstance = new TurIntegrationInstance();
        updatedInstance.setTitle("New Title");

        when(turIntegrationInstanceRepository.findById("1")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/integration/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatedInstance)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist());

        verify(turIntegrationInstanceRepository, times(1)).findById("1");
        verify(turIntegrationInstanceRepository, never()).save(any(TurIntegrationInstance.class));
    }

    @Test
    void testTurIntegrationInstanceDelete() throws Exception {
        mockMvc.perform(delete("/api/integration/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(turIntegrationInstanceRepository, times(1)).delete("1");
    }

    @Test
    void testTurIntegrationInstanceAdd() throws Exception {
        TurIntegrationInstance newInstance = new TurIntegrationInstance();
        newInstance.setTitle("New Instance");

        // Assuming save returns nothing or the argument but in controller code it
        // returns parameter directly.
        mockMvc.perform(post("/api/integration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newInstance)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Instance"));

        verify(turIntegrationInstanceRepository, times(1)).save(any(TurIntegrationInstance.class));
    }
}
