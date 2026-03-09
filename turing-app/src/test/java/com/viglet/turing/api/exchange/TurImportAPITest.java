package com.viglet.turing.api.exchange;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.viglet.turing.exchange.TurExchange;
import com.viglet.turing.exchange.TurImportExchange;

@ExtendWith(MockitoExtension.class)
class TurImportAPITest {

    private MockMvc mockMvc;

    @Mock
    private TurImportExchange turImportExchange;

    @InjectMocks
    private TurImportAPI turImportAPI;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(turImportAPI).build();
    }

    @Test
    void testTurImport() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.zip",
                "application/zip",
                "dummy content".getBytes());

        TurExchange turExchange = new TurExchange();
        when(turImportExchange.importFromMultipartFile(any())).thenReturn(turExchange);

        mockMvc.perform(multipart("/api/import").file(file))
                .andExpect(status().isOk());

        verify(turImportExchange, times(1)).importFromMultipartFile(any());
    }
}
