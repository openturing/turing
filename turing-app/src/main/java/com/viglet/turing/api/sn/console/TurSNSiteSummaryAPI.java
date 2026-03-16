package com.viglet.turing.api.sn.console;

import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
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
import com.viglet.turing.sn.TurSNSiteDataCollectorService;
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
    private final TurGlobalSettingsService globalSettingsService;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurGenAiLlmProviderFactory llmProviderFactory;
    private final TurSecretCryptoService secretCryptoService;
    private final TurSNSiteDataCollectorService siteDataCollectorService;

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
            String dataSummary = siteDataCollectorService.collectSiteData(site);

            var provider = llmProviderFactory.getProvider(llmInstance);
            String decryptedApiKey = secretCryptoService.decrypt(llmInstance.getApiKeyEncrypted());
            ChatModel chatModel = provider.createChatModel(llmInstance, decryptedApiKey);

            String sitePrompt = site.getTurSNSiteGenAi() != null
                    ? site.getTurSNSiteGenAi().getSitePrompt()
                    : null;

            String analysisInstructions = """
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

            String systemPromptText = StringUtils.hasText(sitePrompt)
                    ? sitePrompt + "\n\n" + analysisInstructions
                    : "You are an enterprise search expert analyzing a Semantic Navigation site "
                      + "from the Turing platform. " + analysisInstructions;

            List<Message> messages = List.of(
                    new SystemMessage(systemPromptText),
                    new UserMessage(dataSummary + "\nPlease analyze all this data and provide a comprehensive summary with suggestions."));

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

    public record SummaryAvailableResponse(boolean available) {
    }

    public record SummaryResponse(boolean success, String error, String content) {
    }
}
