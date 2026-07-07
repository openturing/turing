package com.viglet.turing.api.sn.console;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.sn.TurSNSiteInsightsPromptBuilder;
import com.viglet.turing.system.TurLlmSummaryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sn/{snSiteId}/summary")
@Tag(name = "Semantic Navigation Site Summary", description = "AI-powered site summary")
@RequiredArgsConstructor
public class TurSNSiteSummaryAPI {

    private final TurSNSiteRepository snSiteRepository;
    private final TurSNSiteInsightsPromptBuilder insightsPromptBuilder;
    private final TurLlmSummaryService llmSummaryService;

    @Operation(summary = "Check if AI summary is available")
    @GetMapping("/available")
    public ResponseEntity<SummaryAvailableResponse> isAvailable() {
        return ResponseEntity.ok(new SummaryAvailableResponse(llmSummaryService.isAvailable()));
    }

    @Operation(summary = "Generate AI summary for a Semantic Navigation Site")
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<TurLlmSummaryService.SummaryResult> generateSummary(
            @PathVariable String snSiteId,
            @RequestParam(defaultValue = "false") boolean regenerate) {

        // T488 / §XXVIII.3 — read inside a transaction so the insights prompt
        // builder can dereference the site's lazy associations (GenAI graph,
        // fieldExts, locales) through the open session.
        TurSNSite site = snSiteRepository.findByIdWithGenAi(snSiteId).orElse(null);
        if (site == null) {
            return ResponseEntity.notFound().build();
        }

        TurSNSiteInsightsPromptBuilder.InsightsPrompt prompt = insightsPromptBuilder.build(site);

        TurLlmSummaryService.SummaryResult result = llmSummaryService.generate(
                prompt.cacheKey(), prompt.userData(), prompt.systemPrompt(), regenerate);

        if (!result.success() && result.content() == null && result.error() != null
                && result.error().startsWith("No default LLM")) {
            return ResponseEntity.badRequest().body(result);
        }

        return ResponseEntity.ok(result);
    }

    public record SummaryAvailableResponse(boolean available) {
    }
}
