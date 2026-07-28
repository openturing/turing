package com.viglet.turing.system;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.domain.llm.TurLLMInstanceDomain;
import com.viglet.turing.domain.llm.TurLLMInstanceRepositoryPort;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.service.llm.tokenusage.TurLLMTokenUsageService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TurLlmSummaryService {

    private final TurGlobalSettingsService globalSettingsService;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurLLMInstanceRepositoryPort llmInstanceRepositoryPort;
    private final TurLlmModelFactory llmModelFactory;
    private final TurSecretCryptoService secretCryptoService;
    private final TurLlmCacheService llmCacheService;
    private final TurLLMTokenUsageService tokenUsageService;
    private final TurStructuredCompletionPort structuredCompletion;

    public record SummaryResult(boolean success, String error, String content, boolean canRegenerate) {
    }

    /**
     * T829 — what a schema-bound generation produced, plus <em>how</em>. The
     * {@code source} is what lets a caller distinguish "no LLM is configured" from
     * "the model answered and we could not read it".
     */
    public record StructuredResult(SummaryResult summary, TurVerdictSource source) {
        public boolean success() {
            return summary.success();
        }

        public String content() {
            return summary.content();
        }

        public String error() {
            return summary.error();
        }

        public boolean canRegenerate() {
            return summary.canRegenerate();
        }
    }

    /**
     * T829 — the schema a structured generation binds the reply to.
     *
     * @param schemaName        stable name (providers log it; Anthropic uses it as
     *                          the forced tool's name)
     * @param jsonSchema        the JSON Schema of the expected object
     * @param formatInstruction appended to the system prompt <em>only</em> on the
     *                          plain-chat fallback, where nothing enforces the shape
     */
    public record StructuredSpec(String schemaName, Map<String, Object> jsonSchema,
            String formatInstruction) {
    }

    public SummaryResult generate(String cacheKey, String data, String systemPrompt, boolean regenerate) {
        return generate(cacheKey, data, systemPrompt, regenerate, null);
    }

    /**
     * T728 — synthesis-stage overload: when {@code llmInstanceIdOverride} names an
     * existing, enabled instance it drives this summary instead of the default LLM
     * (so a research study can synthesize on a different model than it interviews
     * on); a blank/missing/disabled override fails open to the platform default.
     * The caller is responsible for folding the resolved instance into its
     * {@code cacheKey} so a lane change misses the cache.
     */
    public SummaryResult generate(String cacheKey, String data, String systemPrompt,
            boolean regenerate, String llmInstanceIdOverride) {
        String llmId = resolveUsableLlmId(llmInstanceIdOverride);
        if (!StringUtils.hasText(llmId)) {
            return new SummaryResult(false, "No default LLM configured in Global Settings.", null, false);
        }

        TurLLMInstance llmInstance = llmInstanceRepository.findById(llmId).orElse(null);
        if (llmInstance == null || llmInstance.getEnabled() != 1) {
            return new SummaryResult(false, "Default LLM not found or disabled.", null, false);
        }

        boolean canRegenerate = globalSettingsService.isLlmCacheRegenerate();

        if (!regenerate) {
            String cached = llmCacheService.get(cacheKey);
            if (cached != null) {
                return new SummaryResult(true, null, cached, canRegenerate);
            }
        } else {
            llmCacheService.evict(cacheKey);
        }

        try {
            String decryptedApiKey = secretCryptoService.decrypt(llmInstance.getApiKeyEncrypted());
            ChatModel chatModel = llmModelFactory.createChatModel(llmInstance, decryptedApiKey);

            List<Message> messages = List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(data));

            Prompt prompt = new Prompt(messages);
            var response = chatModel.call(prompt);

            tokenUsageService.recordUsage(llmInstance, response, resolveUsername(),
                    null, com.viglet.turing.observability.TurMeterNames.STAGE_CHAT_BACKGROUND);

            String content = response.getResult().getOutput().getText();

            llmCacheService.put(cacheKey, content);

            return new SummaryResult(true, null, content, canRegenerate);
        } catch (Exception e) {
            log.error("Failed to generate AI summary for key {}", cacheKey, e);
            return new SummaryResult(false, "Failed to generate summary: " + e.getMessage(), null, canRegenerate);
        }
    }

    /**
     * T829 / §LXI.5 (Block BN) — a generation whose reply is bound to a
     * <strong>provider-enforced schema</strong> instead of a JSON contract written
     * into the prompt and hoped for.
     *
     * <p>Order of attempts, each falling through to the next:
     * <ol>
     *   <li>the provider's own structured surface via
     *   {@link TurStructuredCompletionPort} (OpenAI {@code json_schema}, Anthropic
     *   forced tool) → {@link TurVerdictSource#PROVIDER_SCHEMA};</li>
     *   <li>a plain chat call with {@code spec.formatInstruction()} appended, i.e.
     *   exactly what {@link #generate} has always done → {@link
     *   TurVerdictSource#PROMPT_JSON};</li>
     *   <li>nothing usable → {@link TurVerdictSource#UNAVAILABLE}.</li>
     * </ol>
     *
     * <p>The cache, the T728 instance override and the token accounting behave
     * exactly as in {@link #generate} — a cache hit reports
     * {@link TurVerdictSource#CACHE} because how it was originally produced is not
     * recorded. Callers still own their cache key (fold the instance into it).
     */
    public StructuredResult generateStructured(String cacheKey, String data, String systemPrompt,
            boolean regenerate, String llmInstanceIdOverride, StructuredSpec spec) {
        String llmId = resolveUsableLlmId(llmInstanceIdOverride);
        if (!StringUtils.hasText(llmId)) {
            return unavailable("No default LLM configured in Global Settings.", false);
        }
        TurLLMInstance llmInstance = llmInstanceRepository.findById(llmId).orElse(null);
        if (llmInstance == null || llmInstance.getEnabled() != 1) {
            return unavailable("Default LLM not found or disabled.", false);
        }

        boolean canRegenerate = globalSettingsService.isLlmCacheRegenerate();
        if (!regenerate) {
            String cached = llmCacheService.get(cacheKey);
            if (cached != null) {
                return new StructuredResult(new SummaryResult(true, null, cached, canRegenerate),
                        TurVerdictSource.CACHE);
            }
        }

        Optional<String> schemaReply = providerSchemaReply(llmInstance, data, systemPrompt, spec);
        if (schemaReply.isPresent()) {
            llmCacheService.put(cacheKey, schemaReply.get());
            return new StructuredResult(
                    new SummaryResult(true, null, schemaReply.get(), canRegenerate),
                    TurVerdictSource.PROVIDER_SCHEMA);
        }

        // No structured surface (or it failed): the legacy prompt-JSON path. Passing
        // regenerate=true only skips the cache read we already did above.
        String prompt = spec != null && StringUtils.hasText(spec.formatInstruction())
                ? systemPrompt + "\n\n" + spec.formatInstruction()
                : systemPrompt;
        SummaryResult plain = generate(cacheKey, data, prompt, true, llmInstanceIdOverride);
        TurVerdictSource source = plain.success() && StringUtils.hasText(plain.content())
                ? TurVerdictSource.PROMPT_JSON
                : TurVerdictSource.UNAVAILABLE;
        return new StructuredResult(plain, source);
    }

    /** The provider-enforced reply, or empty when this instance has no such surface. */
    private Optional<String> providerSchemaReply(TurLLMInstance llmInstance, String data,
            String systemPrompt, StructuredSpec spec) {
        if (spec == null || spec.jsonSchema() == null || spec.jsonSchema().isEmpty()) {
            return Optional.empty();
        }
        return structuredCompletion
                .complete(llmInstance, systemPrompt, data, spec.schemaName(), spec.jsonSchema())
                .filter(StringUtils::hasText);
    }

    private StructuredResult unavailable(String error, boolean canRegenerate) {
        return new StructuredResult(new SummaryResult(false, error, null, canRegenerate),
                TurVerdictSource.UNAVAILABLE);
    }

    /**
     * The override id when it names an existing enabled instance, otherwise the
     * platform default LLM id (possibly blank).
     */
    private String resolveUsableLlmId(String llmInstanceIdOverride) {
        if (StringUtils.hasText(llmInstanceIdOverride)) {
            boolean usable = llmInstanceRepository.findById(llmInstanceIdOverride.trim())
                    .filter(instance -> instance.getEnabled() == 1)
                    .isPresent();
            if (usable) {
                return llmInstanceIdOverride.trim();
            }
        }
        return globalSettingsService.getDefaultLlmId();
    }

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    public boolean isAvailable() {
        String llmId = globalSettingsService.getDefaultLlmId();
        if (!StringUtils.hasText(llmId)) {
            return false;
        }
        return llmInstanceRepositoryPort.findById(llmId)
                .map(TurLLMInstanceDomain::isEnabled)
                .orElse(false);
    }
}
