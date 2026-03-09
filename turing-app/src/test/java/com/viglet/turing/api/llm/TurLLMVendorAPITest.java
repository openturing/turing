package com.viglet.turing.api.llm;

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
import com.viglet.turing.persistence.mapper.llm.TurLLMVendorMapper;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;

@ExtendWith(MockitoExtension.class)
class TurLLMVendorAPITest {

    private MockMvc mockMvc;

    @Mock
    private TurLLMVendorRepository turLLMVendorRepository;

    @Spy
    private TurLLMVendorMapper turLLMVendorMapper = Mappers.getMapper(TurLLMVendorMapper.class);

    @InjectMocks
    private TurLLMVendorAPI turLLMVendorAPI;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(turLLMVendorAPI).build();
    }

    @Test
    void testTurLLMVendorList() throws Exception {
        TurLLMVendor vendor1 = new TurLLMVendor();
        vendor1.setId("1");
        vendor1.setTitle("Vendor 1");

        TurLLMVendor vendor2 = new TurLLMVendor();
        vendor2.setId("2");
        vendor2.setTitle("Vendor 2");

        List<TurLLMVendor> vendors = Arrays.asList(vendor1, vendor2);
        when(turLLMVendorRepository.findAll(any(Sort.class))).thenReturn(vendors);

        mockMvc.perform(get("/api/llm/vendor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[0].title").value("Vendor 1"))
                .andExpect(jsonPath("$[1].id").value("2"))
                .andExpect(jsonPath("$[1].title").value("Vendor 2"));

        verify(turLLMVendorRepository, times(1)).findAll(any(Sort.class));
    }

    @Test
    void testTurLLMVendorGet_Found() throws Exception {
        TurLLMVendor vendor = new TurLLMVendor();
        vendor.setId("1");
        vendor.setTitle("Vendor 1");

        when(turLLMVendorRepository.findById("1")).thenReturn(Optional.of(vendor));

        mockMvc.perform(get("/api/llm/vendor/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.title").value("Vendor 1"));

        verify(turLLMVendorRepository, times(1)).findById("1");
    }

    @Test
    void testTurLLMVendorGet_NotFound() throws Exception {
        when(turLLMVendorRepository.findById("1")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/llm/vendor/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist());

        verify(turLLMVendorRepository, times(1)).findById("1");
    }

    @Test
    void testTurLLMVendorUpdate_Found() throws Exception {
        TurLLMVendor existingVendor = new TurLLMVendor();
        existingVendor.setId("1");
        existingVendor.setTitle("Old Title");

        TurLLMVendor updatedVendor = new TurLLMVendor();
        updatedVendor.setTitle("New Title");
        updatedVendor.setDescription("New Desc");
        updatedVendor.setPlugin("New Plugin");
        updatedVendor.setWebsite("http://newwebsite");

        when(turLLMVendorRepository.findById("1")).thenReturn(Optional.of(existingVendor));

        mockMvc.perform(put("/api/llm/vendor/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatedVendor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Title"))
                .andExpect(jsonPath("$.description").value("New Desc"))
                .andExpect(jsonPath("$.plugin").value("New Plugin"))
                .andExpect(jsonPath("$.website").value("http://newwebsite"));

        verify(turLLMVendorRepository, times(1)).findById("1");
        verify(turLLMVendorRepository, times(1)).save(any(TurLLMVendor.class));
    }

    @Test
    void testTurLLMVendorUpdate_NotFound() throws Exception {
        TurLLMVendor updatedVendor = new TurLLMVendor();
        updatedVendor.setTitle("New Title");

        when(turLLMVendorRepository.findById("1")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/llm/vendor/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatedVendor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist());

        verify(turLLMVendorRepository, times(1)).findById("1");
        verify(turLLMVendorRepository, never()).save(any(TurLLMVendor.class));
    }

    @Test
    void testTurLLMVendorDelete() throws Exception {
        mockMvc.perform(delete("/api/llm/vendor/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(turLLMVendorRepository, times(1)).delete("1");
    }

    @Test
    void testTurLLMVendorAdd() throws Exception {
        TurLLMVendor newVendor = new TurLLMVendor();
        newVendor.setTitle("New Vendor");

        mockMvc.perform(post("/api/llm/vendor")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newVendor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Vendor"));

        verify(turLLMVendorRepository, times(1)).save(any(TurLLMVendor.class));
    }
}
