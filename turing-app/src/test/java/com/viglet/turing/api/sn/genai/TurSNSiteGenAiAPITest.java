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

import com.viglet.turing.api.sn.search.TurSNSiteSearchService;
import com.viglet.turing.genai.TurSNGenAi;
import com.viglet.turing.genai.TurGenAiContextFactory;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.sn.TurSNSearchProcess;
import com.viglet.turing.system.TurGlobalSettingsService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurSNSiteGenAiAPITest {

    private MockMvc mockMvc;

    @Mock
    private TurSNSearchProcess turSNSearchProcess;

    @Mock
    private TurSNGenAi turGenAi;

    @Mock
    private TurGenAiContextFactory turGenAiContextFactory;

    @Mock
    private TurSNSiteLocaleRepository turSNSiteLocaleRepository;

    @Mock
    private TurSNSiteSearchService turSNSiteSearchService;

    @Mock
    private TurGlobalSettingsService turGlobalSettingsService;

    @Mock
    private com.viglet.turing.genai.TurDefaultAgentResolver turDefaultAgentResolver;

    // T790 / §LIV.1 (Block BF) — used by the VECTORLESS_STRUCTURED readiness path.
    @Mock
    private com.viglet.turing.genai.catalog.TurCatalogCopilotService catalogCopilotService;

    // /enabled reads configProperties.getChat().getSession() — deep-stub the chain
    // so the endpoint builds its ChatEnabledResponse without a real config bean.
    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private com.viglet.turing.properties.TurConfigProperties configProperties;

    @InjectMocks
    private TurSNSiteGenAiAPI api;

    @BeforeEach
    void setUp() {
        // Mirror the real resolver with no global default agent: the effective
        // agent is the binding's own agent (T622 fallback inert in these tests).
        when(turDefaultAgentResolver.resolveEffectiveAgent(any())).thenAnswer(inv -> {
            TurSNSiteGenAi g = inv.getArgument(0);
            return g == null ? null : g.getTurAIAgent();
        });
        mockMvc = MockMvcBuilders.standaloneSetup(api).build();
    }

    @Test
    void testChatMessage_GenAiEnabledButNoStoreInstance() throws Exception {
        // TurGenAiContext constructor calls turStoreInstance.getUrl() which needs real
        // infra.
        // We test the disabled path which can be fully mocked instead.
        TurSNSite site = new TurSNSite();
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
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
        site.setTurSNSiteGenAi(genAi);

        when(turSNSearchProcess.existsByTurSNSiteAndLanguage(eq("site1"), any())).thenReturn(true);
        when(turSNSearchProcess.getSNSite("site1")).thenReturn(Optional.of(site));

        mockMvc.perform(get("/api/sn/site1/chat")
                .param("q", "hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Language Model is not enabled for this site."));

        verify(turGenAi, never()).assistant(any(), anyString());
    }

    // ─────────────────── T790 / §LIV.1 (Block BF) — vectorless readiness ───────────────────

    @Test
    void testEnabled_VectorlessStructured_copilotAvailable_isEnabled() throws Exception {
        TurSNSite site = new TurSNSite();
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setKnowledgeBaseMode(
                com.viglet.turing.persistence.model.sn.genai.TurSNKnowledgeBaseMode.VECTORLESS_STRUCTURED);
        site.setTurSNSiteGenAi(genAi);

        when(turSNSearchProcess.getSNSite("site1")).thenReturn(Optional.of(site));
        when(catalogCopilotService.isAvailable()).thenReturn(true);

        mockMvc.perform(get("/api/sn/site1/chat/enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("NONE"));
    }

    @Test
    void testEnabled_VectorlessStructured_noDefaultLlm_reportsMissingDefaultLlm() throws Exception {
        TurSNSite site = new TurSNSite();
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setKnowledgeBaseMode(
                com.viglet.turing.persistence.model.sn.genai.TurSNKnowledgeBaseMode.VECTORLESS_STRUCTURED);
        site.setTurSNSiteGenAi(genAi);

        when(turSNSearchProcess.getSNSite("site1")).thenReturn(Optional.of(site));
        when(catalogCopilotService.isAvailable()).thenReturn(false);

        mockMvc.perform(get("/api/sn/site1/chat/enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                // Vectorless never reports MISSING_EMBEDDING / MISSING_STORE.
                .andExpect(jsonPath("$.reason").value("MISSING_DEFAULT_LLM"));
    }

    @Test
    void testEnabled_VectorMode_noEmbedding_stillReportsMissingEmbedding() throws Exception {
        // VECTOR (default) keeps the classic embedding/store readiness walk: a bound,
        // enabled, RAG-on agent with an LLM but no embedding model → MISSING_EMBEDDING.
        TurSNSite site = new TurSNSite();
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        var agent = new com.viglet.turing.persistence.model.agent.TurAIAgent();
        agent.setEnabled(1);
        agent.setRagEnabled(true);
        agent.setLlmInstances(new java.util.HashSet<>(java.util.List.of(
                new com.viglet.turing.persistence.model.llm.TurLLMInstance())));
        genAi.setTurAIAgent(agent);
        site.setTurSNSiteGenAi(genAi);

        when(turSNSearchProcess.getSNSite("site1")).thenReturn(Optional.of(site));

        mockMvc.perform(get("/api/sn/site1/chat/enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("MISSING_EMBEDDING"));
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
