package com.viglet.turing.api.sn.console;

import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.sn.TurSNSiteDataCollectorService;
import com.viglet.turing.system.TurLlmSummaryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sn/{snSiteId}/summary")
@Tag(name = "Semantic Navigation Site Summary", description = "AI-powered site summary")
@RequiredArgsConstructor
public class TurSNSiteSummaryAPI {

    private static final String ANALYSIS_INSTRUCTIONS = """
            Analyze the data provided and generate a comprehensive summary in Markdown format. \
            Include these sections:
            ## Overview
            Brief summary of the site configuration and health.
            ## Search Activity
            Analysis of search metrics and top terms.
            ## Configuration Review
            Review of fields, locales, facets, and behavior settings.
            ## Suggestions
            Actionable recommendations to improve search quality and user experience.

            Be concise but insightful. Use bullet points where appropriate. \
            Highlight any potential issues or misconfigurations.""";

    private final TurSNSiteRepository snSiteRepository;
    private final TurSNSiteDataCollectorService siteDataCollectorService;
    private final TurLlmSummaryService llmSummaryService;

    @Operation(summary = "Check if AI summary is available")
    @GetMapping("/available")
    public ResponseEntity<SummaryAvailableResponse> isAvailable() {
        return ResponseEntity.ok(new SummaryAvailableResponse(llmSummaryService.isAvailable()));
    }

    @Operation(summary = "Generate AI summary for a Semantic Navigation Site")
    @GetMapping
    public ResponseEntity<TurLlmSummaryService.SummaryResult> generateSummary(
            @PathVariable String snSiteId,
            @RequestParam(defaultValue = "false") boolean regenerate) {

        TurSNSite site = snSiteRepository.findByIdNoCache(snSiteId).orElse(null);
        if (site == null) {
            return ResponseEntity.notFound().build();
        }

        String dataSummary = siteDataCollectorService.collectSiteData(site);

        String sitePrompt = site.getTurSNSiteGenAi() != null
                ? site.getTurSNSiteGenAi().getSitePrompt()
                : null;

        String systemPrompt = StringUtils.hasText(sitePrompt)
                ? sitePrompt + "\n\n" + ANALYSIS_INSTRUCTIONS
                : "You are an enterprise search expert analyzing a Semantic Navigation site "
                  + "from the Turing platform. " + ANALYSIS_INSTRUCTIONS;

        String userData = dataSummary
                + "\nPlease analyze all this data and provide a comprehensive summary with suggestions.";

        TurLlmSummaryService.SummaryResult result = llmSummaryService.generate(
                snSiteId, userData, systemPrompt, regenerate);

        if (!result.success() && result.content() == null && result.error() != null
                && result.error().startsWith("No default LLM")) {
            return ResponseEntity.badRequest().body(result);
        }

        return ResponseEntity.ok(result);
    }

    public record SummaryAvailableResponse(boolean available) {
    }
}
