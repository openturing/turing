package com.viglet.turing.api.sn.console;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.sn.TurSNFieldProcess;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/sn/{snSiteId}/summary")
@Tag(name = "Semantic Navigation Site Summary", description = "AI-powered site summary")
@RequiredArgsConstructor
public class TurSNSiteSummaryAPI {

    private final TurSNSiteRepository snSiteRepository;
    private final TurSNSiteLocaleRepository snSiteLocaleRepository;
    private final TurSNSiteMetricAccessRepository metricAccessRepository;
    private final TurSearchEnginePluginFactory pluginFactory;
    private final TurGlobalSettingsService globalSettingsService;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurGenAiLlmProviderFactory llmProviderFactory;
    private final TurSecretCryptoService secretCryptoService;
    private final TurSNFieldProcess snFieldProcess;

    @Operation(summary = "Check if AI summary is available")
    @GetMapping("/available")
    public ResponseEntity<SummaryAvailableResponse> isAvailable() {
        String llmId = globalSettingsService.getDefaultLlmId();
        if (!StringUtils.hasText(llmId)) {
            return ResponseEntity.ok(new SummaryAvailableResponse(false));
        }
        boolean exists = llmInstanceRepository.findById(llmId)
                .map(instance -> instance.getEnabled() == 1)
                .orElse(false);
        return ResponseEntity.ok(new SummaryAvailableResponse(exists));
    }

    @Operation(summary = "Generate AI summary for a Semantic Navigation Site")
    @GetMapping
    public ResponseEntity<SummaryResponse> generateSummary(@PathVariable String snSiteId) {
        String llmId = globalSettingsService.getDefaultLlmId();
        if (!StringUtils.hasText(llmId)) {
            return ResponseEntity.badRequest()
                    .body(new SummaryResponse(false, "No default LLM configured in Global Settings.", null));
        }

        TurLLMInstance llmInstance = llmInstanceRepository.findById(llmId).orElse(null);
        if (llmInstance == null || llmInstance.getEnabled() != 1) {
            return ResponseEntity.badRequest()
                    .body(new SummaryResponse(false, "Default LLM not found or disabled.", null));
        }

        TurSNSite site = snSiteRepository.findById(snSiteId).orElse(null);
        if (site == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            String dataSummary = collectSiteData(site);

            var provider = llmProviderFactory.getProvider(llmInstance);
            String decryptedApiKey = secretCryptoService.decrypt(llmInstance.getApiKeyEncrypted());
            ChatModel chatModel = provider.createChatModel(llmInstance, decryptedApiKey);

            List<Message> messages = List.of(
                    new SystemMessage(
                            "You are an enterprise search expert analyzing a Semantic Navigation site from the Turing platform. "
                            + "Based on the site data provided, generate a comprehensive summary in Markdown format. "
                            + "Include these sections:\n"
                            + "## Overview\nBrief summary of the site configuration and health.\n"
                            + "## Search Activity\nAnalysis of search metrics and top terms.\n"
                            + "## Configuration Review\nReview of fields, locales, facets, and behavior settings.\n"
                            + "## Suggestions\nActionable recommendations to improve search quality and user experience.\n\n"
                            + "Be concise but insightful. Use bullet points where appropriate. "
                            + "Highlight any potential issues or misconfigurations."),
                    new UserMessage(dataSummary));

            Prompt prompt = new Prompt(messages);
            var response = chatModel.call(prompt);
            String content = response.getResult().getOutput().getText();

            return ResponseEntity.ok(new SummaryResponse(true, null, content));
        } catch (Exception e) {
            log.error("Failed to generate AI summary for site {}", snSiteId, e);
            return ResponseEntity.internalServerError()
                    .body(new SummaryResponse(false, "Failed to generate summary: " + e.getMessage(), null));
        }
    }

    private String collectSiteData(TurSNSite site) {
        StringBuilder sb = new StringBuilder();

        // Site basics
        sb.append("# Site: ").append(site.getName()).append("\n");
        if (StringUtils.hasText(site.getDescription())) {
            sb.append("Description: ").append(site.getDescription()).append("\n");
        }
        sb.append("Search Engine: ").append(site.getTurSEInstance() != null
                ? site.getTurSEInstance().getTitle() : "Not configured").append("\n");
        sb.append("Rows per page: ").append(site.getRowsPerPage()).append("\n");
        sb.append("Highlight: ").append(site.getHl() == 1 ? "enabled" : "disabled").append("\n");
        sb.append("Spell check: ").append(site.getSpellCheck() == 1 ? "enabled" : "disabled").append("\n");
        sb.append("More like this: ").append(site.getMlt() == 1 ? "enabled" : "disabled").append("\n");
        sb.append("Facets: ").append(site.getFacet() == 1 ? "enabled" : "disabled");
        if (site.getFacet() == 1) {
            sb.append(" (items per facet: ").append(site.getItemsPerFacet()).append(")");
        }
        sb.append("\n");
        sb.append("Wildcard on no results: ").append(site.getWildcardNoResults() == 1 ? "yes" : "no").append("\n");
        sb.append("Exact match: ").append(site.getExactMatch() == 1 ? "enabled" : "disabled").append("\n\n");

        // Locales
        var locales = snSiteLocaleRepository.findByTurSNSite(site);
        sb.append("## Locales (").append(locales.size()).append(")\n");
        for (var locale : locales) {
            sb.append("- ").append(locale.getLanguage()).append(" (core: ").append(locale.getCore()).append(")\n");
        }
        sb.append("\n");

        // Document counts
        sb.append("## Document Counts\n");
        for (var locale : locales) {
            try {
                var plugin = pluginFactory.getPluginForSite(site);
                long count = plugin.getDocumentTotal(locale);
                sb.append("- ").append(locale.getLanguage()).append(": ").append(count).append(" documents\n");
            } catch (Exception e) {
                sb.append("- ").append(locale.getLanguage()).append(": unavailable\n");
            }
        }
        sb.append("\n");

        // Fields
        var fields = site.getTurSNSiteFieldExts();
        if (fields != null && !fields.isEmpty()) {
            sb.append("## Fields (").append(fields.size()).append(")\n");
            int enabledCount = (int) fields.stream().filter(f -> f.getEnabled() == 1).count();
            int facetCount = (int) fields.stream().filter(f -> f.getFacet() == 1).count();
            int requiredCount = (int) fields.stream().filter(f -> f.getRequired() == 1).count();
            sb.append("- Enabled: ").append(enabledCount).append("\n");
            sb.append("- Faceted: ").append(facetCount).append("\n");
            sb.append("- Required: ").append(requiredCount).append("\n");
            sb.append("- Field names: ");
            fields.stream()
                    .filter(f -> f.getEnabled() == 1)
                    .limit(20)
                    .forEach(f -> sb.append(f.getName()).append(", "));
            sb.append("\n\n");
        }

        // Metrics - this week
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant now = Instant.now();
        int searchesThisWeek = metricAccessRepository.countTermsByPeriod(site, weekAgo, now);
        sb.append("## Search Metrics (Last 7 days)\n");
        sb.append("Total searches: ").append(searchesThisWeek).append("\n");

        List<TurSNSiteMetricAccessTerm> topTerms = metricAccessRepository
                .topTermsBetweenDates(site, weekAgo, now, PageRequest.of(0, 15));
        if (!topTerms.isEmpty()) {
            sb.append("Top search terms:\n");
            for (TurSNSiteMetricAccessTerm term : topTerms) {
                sb.append("  - \"").append(term.getTerm()).append("\" (").append(term.getTotal()).append(" searches)\n");
            }
        }
        sb.append("\n");

        // Metrics - this month
        Instant monthAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        int searchesThisMonth = metricAccessRepository.countTermsByPeriod(site, monthAgo, now);
        sb.append("## Search Metrics (Last 30 days)\n");
        sb.append("Total searches: ").append(searchesThisMonth).append("\n\n");

        // GenAI config
        var genAi = site.getTurSNSiteGenAi();
        sb.append("## Generative AI Configuration\n");
        if (genAi != null && genAi.isEnabled()) {
            sb.append("- Enabled: yes\n");
            if (genAi.getTurLLMInstance() != null) {
                sb.append("- LLM: ").append(genAi.getTurLLMInstance().getTitle()).append("\n");
            }
        } else {
            sb.append("- Enabled: no\n");
        }

        sb.append("\nPlease analyze all this data and provide a comprehensive summary with suggestions.");
        return sb.toString();
    }

    public record SummaryAvailableResponse(boolean available) {
    }

    public record SummaryResponse(boolean success, String error, String content) {
    }
}
