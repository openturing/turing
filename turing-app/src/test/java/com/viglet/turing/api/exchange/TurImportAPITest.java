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

import com.viglet.turing.exchange.TurImportExchange;
import com.viglet.turing.exchange.TurImportExchange.ImportResult;
import com.viglet.turing.exchange.sn.TurSNSiteContentExchangeService;
import com.viglet.turing.exchange.sn.TurSNSiteExportValidator;

@ExtendWith(MockitoExtension.class)
class TurImportAPITest {

    private MockMvc mockMvc;

    @Mock
    private TurImportExchange turImportExchange;

    @Mock
    private TurSNSiteExportValidator turSNSiteExportValidator;

    @Mock
    private TurSNSiteContentExchangeService contentExchangeService;

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

        ImportResult importResult = new ImportResult(true, "test", 0, 0, false, null, 0, null,
                java.util.List.of(), true, null, java.util.List.of());
        when(turImportExchange.importFromMultipartFile(any(), any(boolean.class), any(boolean.class), any(), any(boolean.class))).thenReturn(importResult);

        mockMvc.perform(multipart("/api/import").file(file))
                .andExpect(status().isOk());

        verify(turImportExchange, times(1)).importFromMultipartFile(any(), any(boolean.class), any(boolean.class), any(), any(boolean.class));
    }
}
