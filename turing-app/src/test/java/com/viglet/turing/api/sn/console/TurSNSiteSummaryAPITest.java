package com.viglet.turing.api.sn.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.ResponseEntity;

import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.sn.TurSNSiteDataCollectorService;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.TurLlmCacheService;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * Tests for TurSNSiteSummaryAPI.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteSummaryAPITest {

    @Mock
    private TurSNSiteRepository snSiteRepository;
    @Mock
    private TurGlobalSettingsService globalSettingsService;
    @Mock
    private TurLLMInstanceRepository llmInstanceRepository;
    @Mock
    private TurGenAiLlmProviderFactory llmProviderFactory;
    @Mock
    private TurSecretCryptoService secretCryptoService;
    @Mock
    private TurSNSiteDataCollectorService siteDataCollectorService;
    @Mock
    private TurLlmCacheService llmCacheService;

    @InjectMocks
    private TurSNSiteSummaryAPI api;

    // --- isAvailable Tests ---

    @Test
    void isAvailableShouldReturnFalseWhenNoDefaultLlm() {
        when(globalSettingsService.getDefaultLlmId()).thenReturn(null);

        ResponseEntity<TurSNSiteSummaryAPI.SummaryAvailableResponse> response = api.isAvailable();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().available()).isFalse();
    }

    @Test
    void isAvailableShouldReturnFalseWhenLlmNotFound() {
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm-1");
        when(llmInstanceRepository.findById("llm-1")).thenReturn(Optional.empty());

        ResponseEntity<TurSNSiteSummaryAPI.SummaryAvailableResponse> response = api.isAvailable();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().available()).isFalse();
    }

    @Test
    void isAvailableShouldReturnFalseWhenLlmDisabled() {
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm-1");
        TurLLMInstance instance = new TurLLMInstance();
        instance.setEnabled(0);
        when(llmInstanceRepository.findById("llm-1")).thenReturn(Optional.of(instance));

        ResponseEntity<TurSNSiteSummaryAPI.SummaryAvailableResponse> response = api.isAvailable();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().available()).isFalse();
    }

    @Test
    void isAvailableShouldReturnTrueWhenLlmEnabled() {
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm-1");
        TurLLMInstance instance = new TurLLMInstance();
        instance.setEnabled(1);
        when(llmInstanceRepository.findById("llm-1")).thenReturn(Optional.of(instance));

        ResponseEntity<TurSNSiteSummaryAPI.SummaryAvailableResponse> response = api.isAvailable();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().available()).isTrue();
    }

    // --- generateSummary Tests ---

    @Test
    void generateSummaryShouldReturnBadRequestWhenNoDefaultLlm() {
        when(globalSettingsService.getDefaultLlmId()).thenReturn(null);

        ResponseEntity<TurSNSiteSummaryAPI.SummaryResponse> response = api.generateSummary("site-1", false);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error()).contains("No default LLM");
    }

    @Test
    void generateSummaryShouldReturnBadRequestWhenLlmNotFound() {
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm-1");
        when(llmInstanceRepository.findById("llm-1")).thenReturn(Optional.empty());

        ResponseEntity<TurSNSiteSummaryAPI.SummaryResponse> response = api.generateSummary("site-1", false);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().error()).contains("Default LLM not found or disabled");
    }

    @Test
    void generateSummaryShouldReturnBadRequestWhenLlmDisabled() {
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm-1");
        TurLLMInstance instance = new TurLLMInstance();
        instance.setEnabled(0);
        when(llmInstanceRepository.findById("llm-1")).thenReturn(Optional.of(instance));

        ResponseEntity<TurSNSiteSummaryAPI.SummaryResponse> response = api.generateSummary("site-1", false);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void generateSummaryShouldReturnNotFoundWhenSiteNotFound() {
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm-1");
        TurLLMInstance instance = new TurLLMInstance();
        instance.setEnabled(1);
        when(llmInstanceRepository.findById("llm-1")).thenReturn(Optional.of(instance));
        when(snSiteRepository.findById("site-1")).thenReturn(Optional.empty());

        ResponseEntity<TurSNSiteSummaryAPI.SummaryResponse> response = api.generateSummary("site-1", false);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void generateSummaryShouldReturnCachedContent() {
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm-1");
        TurLLMInstance instance = new TurLLMInstance();
        instance.setEnabled(1);
        when(llmInstanceRepository.findById("llm-1")).thenReturn(Optional.of(instance));
        TurSNSite site = new TurSNSite();
        site.setId("site-1");
        when(snSiteRepository.findById("site-1")).thenReturn(Optional.of(site));
        when(globalSettingsService.isLlmCacheRegenerate()).thenReturn(true);
        when(llmCacheService.get("site-1")).thenReturn("Cached summary");

        ResponseEntity<TurSNSiteSummaryAPI.SummaryResponse> response = api.generateSummary("site-1", false);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().content()).isEqualTo("Cached summary");
        assertThat(response.getBody().canRegenerate()).isTrue();
    }

    @Test
    void generateSummaryShouldEvictCacheOnRegenerate() {
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm-1");
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("llm-1");
        instance.setEnabled(1);
        instance.setApiKeyEncrypted("enc");
        when(llmInstanceRepository.findById("llm-1")).thenReturn(Optional.of(instance));
        TurSNSite site = new TurSNSite();
        site.setId("site-1");
        when(snSiteRepository.findById("site-1")).thenReturn(Optional.of(site));
        when(globalSettingsService.isLlmCacheRegenerate()).thenReturn(true);

        // Set up the LLM response
        TurGenAiLlmProvider provider = mock(TurGenAiLlmProvider.class);
        when(llmProviderFactory.getProvider(instance)).thenReturn(provider);
        when(secretCryptoService.decrypt("enc")).thenReturn("key");
        ChatModel chatModel = mock(ChatModel.class);
        when(provider.createChatModel(instance, "key")).thenReturn(chatModel);
        when(siteDataCollectorService.collectSiteData(site)).thenReturn("Site data");

        AssistantMessage assistantMsg = new AssistantMessage("Generated summary");
        Generation generation = new Generation(assistantMsg);
        ChatResponse chatResponse = new ChatResponse(java.util.List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        ResponseEntity<TurSNSiteSummaryAPI.SummaryResponse> response = api.generateSummary("site-1", true);

        verify(llmCacheService).evict("site-1");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().content()).isEqualTo("Generated summary");
    }

    @Test
    void generateSummaryShouldReturnErrorOnException() {
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm-1");
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("llm-1");
        instance.setEnabled(1);
        instance.setApiKeyEncrypted("enc");
        when(llmInstanceRepository.findById("llm-1")).thenReturn(Optional.of(instance));
        TurSNSite site = new TurSNSite();
        site.setId("site-1");
        when(snSiteRepository.findById("site-1")).thenReturn(Optional.of(site));
        when(globalSettingsService.isLlmCacheRegenerate()).thenReturn(false);
        when(llmCacheService.get("site-1")).thenReturn(null);
        when(siteDataCollectorService.collectSiteData(site))
                .thenThrow(new RuntimeException("Data collection failed"));

        ResponseEntity<TurSNSiteSummaryAPI.SummaryResponse> response = api.generateSummary("site-1", false);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error()).contains("Data collection failed");
    }

    // --- Record Tests ---

    @Test
    void summaryAvailableResponseRecord() {
        var response = new TurSNSiteSummaryAPI.SummaryAvailableResponse(true);
        assertThat(response.available()).isTrue();
    }

    @Test
    void summaryResponseRecord() {
        var response = new TurSNSiteSummaryAPI.SummaryResponse(true, null, "content", true);
        assertThat(response.success()).isTrue();
        assertThat(response.error()).isNull();
        assertThat(response.content()).isEqualTo("content");
        assertThat(response.canRegenerate()).isTrue();
    }
}
