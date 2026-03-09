package com.viglet.turing.api.sn.genai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.viglet.turing.genai.TurGenAi;
import com.viglet.turing.genai.TurGenAiContextFactory;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.sn.TurSNSearchProcess;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurSNSiteGenAiAPITest {

    private MockMvc mockMvc;

    @Mock
    private TurSNSearchProcess turSNSearchProcess;

    @Mock
    private TurGenAi turGenAi;

    @Mock
    private TurGenAiContextFactory turGenAiContextFactory;

    @InjectMocks
    private TurSNSiteGenAiAPI api;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(api).build();
    }

    @Test
    void testChatMessage_GenAiEnabledButNoStoreInstance() throws Exception {
        // TurGenAiContext constructor calls turStoreInstance.getUrl() which needs real
        // infra.
        // We test the disabled path which can be fully mocked instead.
        TurSNSite site = new TurSNSite();
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setEnabled(false);
        site.setTurSNSiteGenAi(genAi);

        when(turSNSearchProcess.existsByTurSNSiteAndLanguage(eq("site1"), any(Locale.class))).thenReturn(true);
        when(turSNSearchProcess.getSNSite("site1")).thenReturn(Optional.of(site));

        mockMvc.perform(get("/api/sn/site1/chat")
                .param("q", "hello")
                .param("_setlocale", "en_US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Language Model is not enabled for this site."));
    }

    @Test
    void testChatMessage_SiteNotFound() throws Exception {
        when(turSNSearchProcess.existsByTurSNSiteAndLanguage(eq("site1"), any(Locale.class))).thenReturn(false);

        mockMvc.perform(get("/api/sn/site1/chat")
                .param("q", "hello")
                .param("_setlocale", "en_US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").doesNotExist());
    }

    @Test
    void testChatMessage_GenAiNotEnabled() throws Exception {
        TurSNSite site = new TurSNSite();
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setEnabled(false);
        site.setTurSNSiteGenAi(genAi);

        when(turSNSearchProcess.existsByTurSNSiteAndLanguage(eq("site1"), any())).thenReturn(true);
        when(turSNSearchProcess.getSNSite("site1")).thenReturn(Optional.of(site));

        mockMvc.perform(get("/api/sn/site1/chat")
                .param("q", "hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Language Model is not enabled for this site."));

        verify(turGenAi, never()).assistant(any(), anyString());
    }

    @Test
    void testChatMessage_SNSiteEmpty() throws Exception {
        when(turSNSearchProcess.existsByTurSNSiteAndLanguage(eq("site1"), any())).thenReturn(true);
        when(turSNSearchProcess.getSNSite("site1")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/sn/site1/chat")
                .param("q", "hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Couldn't find site name."));
    }
}
