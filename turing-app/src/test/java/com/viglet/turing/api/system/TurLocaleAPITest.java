package com.viglet.turing.api.system;

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
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.persistence.mapper.system.TurLocaleMapper;
import com.viglet.turing.persistence.model.system.TurLocale;
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;

@ExtendWith(MockitoExtension.class)
class TurLocaleAPITest {

    private MockMvc mockMvc;

    @Mock
    private TurLocaleRepository turLocaleRepository;

    @Spy
    private TurLocaleMapper turLocaleMapper = Mappers.getMapper(TurLocaleMapper.class);

    @InjectMocks
    private TurLocaleAPI api;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(api).build();
    }

    @Test
    void testTurLocaleList() throws Exception {
        TurLocale locale = new TurLocale(Locale.US, "English", "Inglês");

        when(turLocaleRepository.findAll()).thenReturn(Collections.singletonList(locale));

        mockMvc.perform(get("/api/locale"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].en").value("English"));
    }

    @Test
    void testTurLocaleGet_Found() throws Exception {
        TurLocale locale = new TurLocale(Locale.US, "English", "Inglês");

        when(turLocaleRepository.findById(Locale.US)).thenReturn(Optional.of(locale));

        mockMvc.perform(get("/api/locale/en_US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.en").value("English"));
    }

    @Test
    void testTurLocaleGet_NotFound() throws Exception {
        when(turLocaleRepository.findById(any(Locale.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/locale/en_US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.en").doesNotExist());
    }

    @Test
    void testTurLocaleUpdate_Found() throws Exception {
        TurLocale existing = new TurLocale(Locale.US, "English", "Inglês");

        TurLocale updated = new TurLocale();
        updated.setEn("English (updated)");
        updated.setPt("Inglês atualizado");

        when(turLocaleRepository.findById(Locale.US)).thenReturn(Optional.of(existing));

        mockMvc.perform(put("/api/locale/en_US")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.en").value("English (updated)"));

        verify(turLocaleRepository, times(1)).save(existing);
    }

    @Test
    void testTurLocaleUpdate_NotFound() throws Exception {
        TurLocale updated = new TurLocale();
        updated.setEn("English");

        when(turLocaleRepository.findById(any(Locale.class))).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/locale/en_US")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.en").doesNotExist());

        verify(turLocaleRepository, never()).save(any());
    }

    @Test
    void testTurLocaleDelete() throws Exception {
        mockMvc.perform(delete("/api/locale/en_US"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(turLocaleRepository, times(1)).delete("en_US");
    }

    @Test
    void testTurLocaleAdd() throws Exception {
        TurLocale newLocale = new TurLocale();
        newLocale.setEn("English");

        mockMvc.perform(post("/api/locale")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newLocale)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.en").value("English"));

        verify(turLocaleRepository, times(1)).save(any(TurLocale.class));
    }
}
