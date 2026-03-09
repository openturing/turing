package com.viglet.turing.api.se;

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
import com.viglet.turing.persistence.mapper.se.TurSEVendorMapper;
import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.repository.se.TurSEVendorRepository;

@ExtendWith(MockitoExtension.class)
class TurSEVendorAPITest {

    private MockMvc mockMvc;

    @Mock
    private TurSEVendorRepository turSEVendorRepository;

    @Spy
    private TurSEVendorMapper turSEVendorMapper = Mappers.getMapper(TurSEVendorMapper.class);

    @InjectMocks
    private TurSEVendorAPI turSEVendorAPI;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(turSEVendorAPI).build();
    }

    @Test
    void testTurSEVendorList() throws Exception {
        TurSEVendor vendor1 = new TurSEVendor();
        vendor1.setId("1");
        vendor1.setTitle("Vendor 1");

        TurSEVendor vendor2 = new TurSEVendor();
        vendor2.setId("2");
        vendor2.setTitle("Vendor 2");

        List<TurSEVendor> vendors = Arrays.asList(vendor1, vendor2);
        when(turSEVendorRepository.findAll(any(Sort.class))).thenReturn(vendors);

        mockMvc.perform(get("/api/se/vendor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[0].title").value("Vendor 1"))
                .andExpect(jsonPath("$[1].id").value("2"))
                .andExpect(jsonPath("$[1].title").value("Vendor 2"));

        verify(turSEVendorRepository, times(1)).findAll(any(Sort.class));
    }

    @Test
    void testTurSEVendorGet_Found() throws Exception {
        TurSEVendor vendor = new TurSEVendor();
        vendor.setId("1");
        vendor.setTitle("Vendor 1");

        when(turSEVendorRepository.findById("1")).thenReturn(Optional.of(vendor));

        mockMvc.perform(get("/api/se/vendor/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.title").value("Vendor 1"));

        verify(turSEVendorRepository, times(1)).findById("1");
    }

    @Test
    void testTurSEVendorGet_NotFound() throws Exception {
        when(turSEVendorRepository.findById("1")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/se/vendor/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist());

        verify(turSEVendorRepository, times(1)).findById("1");
    }

    @Test
    void testTurSEVendorUpdate_Found() throws Exception {
        TurSEVendor existingVendor = new TurSEVendor();
        existingVendor.setId("1");
        existingVendor.setTitle("Old Title");

        TurSEVendor updatedVendor = new TurSEVendor();
        updatedVendor.setTitle("New Title");
        updatedVendor.setDescription("New Desc");
        updatedVendor.setPlugin("New Plugin");
        updatedVendor.setWebsite("http://newwebsite");

        when(turSEVendorRepository.findById("1")).thenReturn(Optional.of(existingVendor));

        mockMvc.perform(put("/api/se/vendor/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatedVendor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Title"))
                .andExpect(jsonPath("$.description").value("New Desc"))
                .andExpect(jsonPath("$.plugin").value("New Plugin"))
                .andExpect(jsonPath("$.website").value("http://newwebsite"));

        verify(turSEVendorRepository, times(1)).findById("1");
        verify(turSEVendorRepository, times(1)).save(any(TurSEVendor.class));
    }

    @Test
    void testTurSEVendorUpdate_NotFound() throws Exception {
        TurSEVendor updatedVendor = new TurSEVendor();
        updatedVendor.setTitle("New Title");

        when(turSEVendorRepository.findById("1")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/se/vendor/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatedVendor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist());

        verify(turSEVendorRepository, times(1)).findById("1");
        verify(turSEVendorRepository, never()).save(any());
    }

    @Test
    void testTurSEVendorDelete() throws Exception {
        mockMvc.perform(delete("/api/se/vendor/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(turSEVendorRepository, times(1)).delete("1");
    }

    @Test
    void testTurSEVendorAdd() throws Exception {
        TurSEVendor newVendor = new TurSEVendor();
        newVendor.setTitle("New Vendor");

        mockMvc.perform(post("/api/se/vendor")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newVendor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Vendor"));

        verify(turSEVendorRepository, times(1)).save(any(TurSEVendor.class));
    }
}
