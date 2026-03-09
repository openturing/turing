package com.viglet.turing.api.dev.token;

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
import com.viglet.turing.persistence.mapper.dev.token.TurDevTokenMapper;
import com.viglet.turing.persistence.model.dev.token.TurDevToken;
import com.viglet.turing.persistence.repository.dev.token.TurDevTokenRepository;

@ExtendWith(MockitoExtension.class)
class TurDevTokenAPITest {

    private MockMvc mockMvc;

    @Mock
    private TurDevTokenRepository turDevTokenRepository;

    @Spy
    private TurDevTokenMapper turDevTokenMapper = Mappers.getMapper(TurDevTokenMapper.class);

    @InjectMocks
    private TurDevTokenAPI turDevTokenAPI;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(turDevTokenAPI).build();
    }

    @Test
    void testTurDevTokenList() throws Exception {
        TurDevToken token1 = new TurDevToken();
        token1.setId("1");
        token1.setTitle("Token 1");

        TurDevToken token2 = new TurDevToken();
        token2.setId("2");
        token2.setTitle("Token 2");

        List<TurDevToken> tokens = Arrays.asList(token1, token2);
        when(turDevTokenRepository.findAll(any(Sort.class))).thenReturn(tokens);

        mockMvc.perform(get("/api/dev/token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[0].title").value("Token 1"))
                .andExpect(jsonPath("$[1].id").value("2"))
                .andExpect(jsonPath("$[1].title").value("Token 2"));

        verify(turDevTokenRepository, times(1)).findAll(any(Sort.class));
    }

    @Test
    void testTurDevTokenGet_Found() throws Exception {
        TurDevToken token = new TurDevToken();
        token.setId("1");
        token.setTitle("Token 1");

        when(turDevTokenRepository.findById("1")).thenReturn(Optional.of(token));

        mockMvc.perform(get("/api/dev/token/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.title").value("Token 1"));

        verify(turDevTokenRepository, times(1)).findById("1");
    }

    @Test
    void testTurDevTokenGet_NotFound() throws Exception {
        when(turDevTokenRepository.findById("1")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/dev/token/1"))
                .andExpect(status().isOk()) // The API returns a new TurDevToken, not 404
                .andExpect(jsonPath("$.id").doesNotExist());

        verify(turDevTokenRepository, times(1)).findById("1");
    }

    @Test
    void testTurDevTokenUpdate_Found() throws Exception {
        TurDevToken existingToken = new TurDevToken();
        existingToken.setId("1");
        existingToken.setTitle("Old Title");
        existingToken.setDescription("Old Desc");

        TurDevToken updatedToken = new TurDevToken();
        updatedToken.setTitle("New Title");
        updatedToken.setDescription("New Desc");

        when(turDevTokenRepository.findById("1")).thenReturn(Optional.of(existingToken));
        when(turDevTokenRepository.save(any(TurDevToken.class))).thenReturn(existingToken);

        mockMvc.perform(put("/api/dev/token/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatedToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Title"))
                .andExpect(jsonPath("$.description").value("New Desc"));

        verify(turDevTokenRepository, times(1)).findById("1");
        verify(turDevTokenRepository, times(1)).save(any(TurDevToken.class));
    }

    @Test
    void testTurDevTokenUpdate_NotFound() throws Exception {
        TurDevToken updatedToken = new TurDevToken();
        updatedToken.setTitle("New Title");

        when(turDevTokenRepository.findById("1")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/dev/token/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatedToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist());

        verify(turDevTokenRepository, times(1)).findById("1");
        verify(turDevTokenRepository, never()).save(any(TurDevToken.class));
    }

    @Test
    void testTurDevTokenDelete() throws Exception {
        mockMvc.perform(delete("/api/dev/token/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(turDevTokenRepository, times(1)).deleteById("1");
    }

    @Test
    void testTurDevTokenAdd() throws Exception {
        TurDevToken newToken = new TurDevToken();
        newToken.setTitle("New Token");

        when(turDevTokenRepository.save(any(TurDevToken.class))).thenReturn(newToken);

        mockMvc.perform(post("/api/dev/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Token"))
                .andExpect(jsonPath("$.token").exists());

        verify(turDevTokenRepository, times(1)).save(any(TurDevToken.class));
    }
}
