package com.viglet.turing.genai.provider.llm;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * Spring AI provider for the embedded Jlama engine — pure-Java in-process
 * inference, no external service required. Registered under plugin type
 * {@code "embedded-jlama"} so the {@link TurGenAiLlmProviderFactory} resolves
 * any {@link TurLLMInstance} whose vendor is {@code EMBEDDED_JLAMA} to this
 * implementation.
 *
 * <p><b>Provider options contract</b> — read from
 * {@link TurLLMInstance#providerOptionsJson}:
 * <ul>
 *   <li>{@code modelAssetKey} — path to the SafeTensors model directory in
 *   {@code TurStorageService} (required).</li>
 *   <li>{@code role} — {@code "chat"} or {@code "embedding"}. Determines which
 *   of {@link #createChatModel} / {@link #createEmbeddingModel} the instance
 *   accepts. Jlama loads encoder-only vs decoder-only weights differently, so
 *   a single instance serves exactly one role.</li>
 *   <li>{@code workingQuant} — runtime quantization, default {@code F32}.</li>
 *   <li>{@code modelQuant} — on-disk weight quantization, auto-detected from
 *   {@code config.json}; default {@code I8}.</li>
 *   <li>{@code contextLength} — context window cap, default = config's
 *   {@code max_position_embeddings}.</li>
 * </ul>
 *
 * <p><b>Status (T193 scaffold only)</b>: this class establishes the provider
 * registration, the options contract, and role validation. The actual
 * {@code AbstractModel} cache (T194), the storage path resolver (T195), and
 * the Spring AI {@code ChatModel} / {@code EmbeddingModel} adapters (T197 /
 * T198) ship in follow-up tasks. Until those land,
 * {@link #createChatModel(TurLLMInstance, String)} and
 * {@link #createEmbeddingModel(TurLLMInstance, String)} reject with a
 * descriptive {@link UnsupportedOperationException}.
 *
 * <p><b>Engine selection still open (2026-05-25)</b>: T193 reserves the
 * {@code embedded-jlama} slot in {@link TurGenAiLlmProviderFactory} but does
 * <i>not</i> add {@code com.github.tjake:jlama-core} to the Maven build yet.
 * Upstream Jlama has been in maintenance mode since the {@code v0.8.4}
 * release (2025-01-01), with no {@code 1.x} cut and the default branch
 * stalled since 2025-10. The follow-up tasks (T194/T197/T198) must either
 * (a) commit to the stale Jlama version with eyes open, or (b) pivot the
 * embedded engine to a maintained alternative (kherud/llama-cpp JNI,
 * langchain4j-llama-cpp, or simply doubling down on Ollama-as-sidecar). The
 * scaffold's plugin type stays {@code embedded-jlama} for now so IMPROVEMENTS
 * §XI cross-references remain stable; rename is a single search-replace once
 * the engine is picked.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class TurEmbeddedLlmProvider implements TurGenAiLlmProvider {

    static final String PLUGIN_TYPE = "embedded-jlama";

    static final String OPTION_MODEL_ASSET_KEY = "modelAssetKey";
    static final String OPTION_ROLE = "role";
    static final String OPTION_WORKING_QUANT = "workingQuant";
    static final String OPTION_MODEL_QUANT = "modelQuant";
    static final String OPTION_CONTEXT_LENGTH = "contextLength";

    static final String ROLE_CHAT = "chat";
    static final String ROLE_EMBEDDING = "embedding";
    private static final Set<String> SUPPORTED_ROLES = Set.of(ROLE_CHAT, ROLE_EMBEDDING);

    private static final String DEFAULT_WORKING_QUANT = "F32";
    private static final String DEFAULT_MODEL_QUANT = "I8";

    private final TurProviderOptionsParser optionsParser;

    public TurEmbeddedLlmProvider(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    @Override
    public String getPluginType() {
        return PLUGIN_TYPE;
    }

    @Override
    public ChatModel createChatModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        EmbeddedConfig config = readConfig(turLLMInstance);
        requireRole(config, ROLE_CHAT, turLLMInstance);
        throw new UnsupportedOperationException(
                "Embedded Jlama chat model creation is not yet wired — pending T194"
                        + " (TurEmbeddedModelCache) and T197 (TurEmbeddedChatModel)."
                        + " Instance: " + turLLMInstance.getId()
                        + ", modelAssetKey: " + config.modelAssetKey());
    }

    @Override
    public EmbeddingModel createEmbeddingModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        EmbeddedConfig config = readConfig(turLLMInstance);
        requireRole(config, ROLE_EMBEDDING, turLLMInstance);
        throw new UnsupportedOperationException(
                "Embedded Jlama embedding model creation is not yet wired — pending T194"
                        + " (TurEmbeddedModelCache) and T198 (TurEmbeddedEmbeddingModel)."
                        + " Instance: " + turLLMInstance.getId()
                        + ", modelAssetKey: " + config.modelAssetKey());
    }

    EmbeddedConfig readConfig(TurLLMInstance turLLMInstance) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());

        String modelAssetKey = optionsParser.stringValue(options, OPTION_MODEL_ASSET_KEY);
        if (!StringUtils.hasText(modelAssetKey)) {
            throw new IllegalStateException(
                    "Missing 'modelAssetKey' in providerOptionsJson for embedded Jlama instance: "
                            + turLLMInstance.getId());
        }

        String role = normalizeRole(optionsParser.stringValue(options, OPTION_ROLE));
        if (!SUPPORTED_ROLES.contains(role)) {
            throw new IllegalStateException(
                    "Invalid 'role' for embedded Jlama instance " + turLLMInstance.getId()
                            + ": expected one of " + SUPPORTED_ROLES + ", got " + role);
        }

        String workingQuant = firstNonBlank(
                optionsParser.stringValue(options, OPTION_WORKING_QUANT),
                DEFAULT_WORKING_QUANT);
        String modelQuant = firstNonBlank(
                optionsParser.stringValue(options, OPTION_MODEL_QUANT),
                DEFAULT_MODEL_QUANT);

        Integer contextLength = firstNonNull(
                optionsParser.intValue(options, OPTION_CONTEXT_LENGTH),
                turLLMInstance.getContextWindow());

        return new EmbeddedConfig(modelAssetKey, role, workingQuant, modelQuant, contextLength);
    }

    private void requireRole(EmbeddedConfig config, String expected, TurLLMInstance instance) {
        if (!expected.equals(config.role())) {
            throw new IllegalStateException(
                    "Embedded Jlama instance " + instance.getId()
                            + " has role='" + config.role()
                            + "' and cannot be used as a '" + expected + "' model."
                            + " Create a separate instance for the '" + expected + "' role.");
        }
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "";
        }
        return role.trim().toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * Parsed view of the embedded provider options. Package-private so T194 /
     * T197 / T198 can reuse the same parsing once they're implemented.
     */
    record EmbeddedConfig(
            String modelAssetKey,
            String role,
            String workingQuant,
            String modelQuant,
            Integer contextLength) {
    }
}
