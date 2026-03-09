package com.viglet.turing.api.store;

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

import java.util.Collections;
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
import com.viglet.turing.persistence.mapper.store.TurStoreVendorMapper;
import com.viglet.turing.persistence.model.store.TurStoreVendor;
import com.viglet.turing.persistence.repository.store.TurStoreVendorRepository;

@ExtendWith(MockitoExtension.class)
class TurStoreVendorAPITest {

    private MockMvc mockMvc;

    @Mock
    private TurStoreVendorRepository turStoreVendorRepository;

    @Spy
    private TurStoreVendorMapper turStoreVendorMapper = Mappers.getMapper(TurStoreVendorMapper.class);

    @InjectMocks
    private TurStoreVendorAPI api;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(api).build();
    }

    @Test
    void testTurStoreVendorList() throws Exception {
        TurStoreVendor vendor = new TurStoreVendor();
        vendor.setId("vendor1");
        vendor.setTitle("Vendor 1");

        when(turStoreVendorRepository.findAll(any(Sort.class)))
                .thenReturn(Collections.singletonList(vendor));

        mockMvc.perform(get("/api/store/vendor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("vendor1"))
                .andExpect(jsonPath("$[0].title").value("Vendor 1"));
    }

    @Test
    void testTurStoreVendorGet_Found() throws Exception {
        TurStoreVendor vendor = new TurStoreVendor();
        vendor.setId("vendor1");

        when(turStoreVendorRepository.findById("vendor1")).thenReturn(Optional.of(vendor));

        mockMvc.perform(get("/api/store/vendor/vendor1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("vendor1"));
    }

    @Test
    void testTurStoreVendorGet_NotFound() throws Exception {
        when(turStoreVendorRepository.findById("vendor1")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/store/vendor/vendor1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void testTurStoreVendorUpdate_Found() throws Exception {
        TurStoreVendor existing = new TurStoreVendor();
        existing.setId("vendor1");

        TurStoreVendor updated = new TurStoreVendor();
        updated.setTitle("New Title");
        updated.setDescription("New Desc");
        updated.setPlugin("plugin.class");
        updated.setWebsite("http://example.com");

        when(turStoreVendorRepository.findById("vendor1")).thenReturn(Optional.of(existing));

        mockMvc.perform(put("/api/store/vendor/vendor1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Title"));

        verify(turStoreVendorRepository, times(1)).save(existing);
    }

    @Test
    void testTurStoreVendorUpdate_NotFound() throws Exception {
        TurStoreVendor updated = new TurStoreVendor();
        updated.setTitle("New Title");

        when(turStoreVendorRepository.findById("vendor1")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/store/vendor/vendor1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist());

        verify(turStoreVendorRepository, never()).save(any());
    }

    @Test
    void testTurStoreVendorDelete() throws Exception {
        mockMvc.perform(delete("/api/store/vendor/vendor1"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(turStoreVendorRepository, times(1)).delete("vendor1");
    }

    @Test
    void testTurStoreVendorAdd() throws Exception {
        TurStoreVendor newVendor = new TurStoreVendor();
        newVendor.setTitle("New Vendor");

        mockMvc.perform(post("/api/store/vendor")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newVendor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Vendor"));

        verify(turStoreVendorRepository, times(1)).save(any(TurStoreVendor.class));
    }
}
