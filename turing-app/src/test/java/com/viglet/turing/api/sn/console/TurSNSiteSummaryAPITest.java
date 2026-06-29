package com.viglet.turing.api.sn.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.sn.TurSNSiteInsightsPromptBuilder;
import com.viglet.turing.system.TurLlmSummaryService;

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
    private TurSNSiteInsightsPromptBuilder insightsPromptBuilder;
    @Mock
    private TurLlmSummaryService llmSummaryService;

    @InjectMocks
    private TurSNSiteSummaryAPI api;

    // --- isAvailable Tests ---

    @Test
    void isAvailableShouldReturnFalseWhenNotAvailable() {
        when(llmSummaryService.isAvailable()).thenReturn(false);

        ResponseEntity<TurSNSiteSummaryAPI.SummaryAvailableResponse> response = api.isAvailable();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().available()).isFalse();
    }

    @Test
    void isAvailableShouldReturnTrueWhenAvailable() {
        when(llmSummaryService.isAvailable()).thenReturn(true);

        ResponseEntity<TurSNSiteSummaryAPI.SummaryAvailableResponse> response = api.isAvailable();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().available()).isTrue();
    }

    // --- generateSummary Tests ---

    @Test
    void generateSummaryShouldReturnNotFoundWhenSiteNotFound() {
        ResponseEntity<TurLlmSummaryService.SummaryResult> response = api.generateSummary("site-1", false);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void generateSummaryShouldReturnBadRequestWhenNoDefaultLlm() {
        TurSNSite site = new TurSNSite();
        site.setId("site-1");
        when(snSiteRepository.findByIdWithGenAi("site-1")).thenReturn(Optional.of(site));
        when(insightsPromptBuilder.build(site)).thenReturn(
                new TurSNSiteInsightsPromptBuilder.InsightsPrompt("site-1", "System prompt", "Site data"));
        when(llmSummaryService.generate(eq("site-1"), anyString(), anyString(), eq(false)))
                .thenReturn(new TurLlmSummaryService.SummaryResult(
                        false, "No default LLM configured in Global Settings.", null, false));

        ResponseEntity<TurLlmSummaryService.SummaryResult> response = api.generateSummary("site-1", false);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).contains("No default LLM");
    }

    @Test
    void generateSummaryShouldReturnSuccessWithContent() {
        TurSNSite site = new TurSNSite();
        site.setId("site-1");
        when(snSiteRepository.findByIdWithGenAi("site-1")).thenReturn(Optional.of(site));
        when(insightsPromptBuilder.build(site)).thenReturn(
                new TurSNSiteInsightsPromptBuilder.InsightsPrompt("site-1", "System prompt", "Site data"));
        when(llmSummaryService.generate(eq("site-1"), anyString(), anyString(), eq(false)))
                .thenReturn(new TurLlmSummaryService.SummaryResult(true, null, "AI summary", true));

        ResponseEntity<TurLlmSummaryService.SummaryResult> response = api.generateSummary("site-1", false);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().content()).isEqualTo("AI summary");
        assertThat(response.getBody().canRegenerate()).isTrue();
    }

    @Test
    void generateSummaryShouldPassRegenerateFlag() {
        TurSNSite site = new TurSNSite();
        site.setId("site-1");
        when(snSiteRepository.findByIdWithGenAi("site-1")).thenReturn(Optional.of(site));
        when(insightsPromptBuilder.build(site)).thenReturn(
                new TurSNSiteInsightsPromptBuilder.InsightsPrompt("site-1", "System prompt", "Site data"));
        when(llmSummaryService.generate(eq("site-1"), anyString(), anyString(), eq(true)))
                .thenReturn(new TurLlmSummaryService.SummaryResult(true, null, "Regenerated", true));

        ResponseEntity<TurLlmSummaryService.SummaryResult> response = api.generateSummary("site-1", true);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().content()).isEqualTo("Regenerated");
    }

    @Test
    void generateSummaryShouldReturnErrorOnFailure() {
        TurSNSite site = new TurSNSite();
        site.setId("site-1");
        when(snSiteRepository.findByIdWithGenAi("site-1")).thenReturn(Optional.of(site));
        when(insightsPromptBuilder.build(site)).thenReturn(
                new TurSNSiteInsightsPromptBuilder.InsightsPrompt("site-1", "System prompt", "Site data"));
        when(llmSummaryService.generate(eq("site-1"), anyString(), anyString(), eq(false)))
                .thenReturn(new TurLlmSummaryService.SummaryResult(
                        false, "Failed to generate summary: timeout", null, false));

        ResponseEntity<TurLlmSummaryService.SummaryResult> response = api.generateSummary("site-1", false);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error()).contains("timeout");
    }

    // --- Record Tests ---

    @Test
    void summaryAvailableResponseRecord() {
        var response = new TurSNSiteSummaryAPI.SummaryAvailableResponse(true);
        assertThat(response.available()).isTrue();
    }

    @Test
    void summaryResultRecord() {
        var result = new TurLlmSummaryService.SummaryResult(true, null, "content", true);
        assertThat(result.success()).isTrue();
        assertThat(result.error()).isNull();
        assertThat(result.content()).isEqualTo("content");
        assertThat(result.canRegenerate()).isTrue();
    }
}
