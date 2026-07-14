package com.viglet.turing.system;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import com.viglet.core.settings.VigletConfigVarService;
import com.viglet.turing.genai.rag.rerank.TurRagRerankStrategyType;
import com.viglet.turing.genai.tool.TurCodeInterpreterExecutionMode;
import com.viglet.turing.genai.transcription.TurTranscriptionProviderType;
import com.viglet.turing.onstartup.system.TurConfigVarOnStartup;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * Typed accessors for Turing's global settings, backed by the {@code viglet-core}
 * {@link VigletConfigVarService} (T376 / Block Q). The generic parse/trim/clamp/
 * enum-resolution boilerplate now lives in core; this service maps Turing's
 * named settings to their config-var ids and owns the Turing-specific concerns
 * (secret encryption, custom enum parsers, URL-signing-secret minting).
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Service
public class TurGlobalSettingsService {

    private final VigletConfigVarService configVars;
    private final TurSecretCryptoService turSecretCryptoService;

    public TurGlobalSettingsService(TurConfigVarRepository turConfigVarRepository,
            TurSecretCryptoService turSecretCryptoService) {
        this.configVars = new VigletConfigVarService(new TurConfigVarStore(turConfigVarRepository),
                TurConfigVarOnStartup.GLOBAL_PATH);
        this.turSecretCryptoService = turSecretCryptoService;
    }

    public TurGlobalDecimalSeparator getDecimalSeparator() {
        return configVars.getEnum(TurGlobalDecimalSeparator.class, TurConfigVarOnStartup.DECIMAL_SEPARATOR,
                TurGlobalDecimalSeparator.valueOf(TurConfigVarOnStartup.DECIMAL_SEPARATOR_DEFAULT_VALUE));
    }

    public TurGlobalDecimalSeparator updateDecimalSeparator(TurGlobalDecimalSeparator decimalSeparator) {
        return configVars.setEnum(TurConfigVarOnStartup.DECIMAL_SEPARATOR, decimalSeparator,
                TurGlobalDecimalSeparator.valueOf(TurConfigVarOnStartup.DECIMAL_SEPARATOR_DEFAULT_VALUE));
    }

    public String getPythonExecutable() {
        return configVars.getString(TurConfigVarOnStartup.PYTHON_EXECUTABLE, "");
    }

    public String updatePythonExecutable(String pythonExecutable) {
        return configVars.setString(TurConfigVarOnStartup.PYTHON_EXECUTABLE, pythonExecutable);
    }

    /**
     * Returns the global {@code requirements.txt}-style declaration of
     * Python packages the Code Interpreter sandbox should make available
     * to every Custom Tool that calls {@code code.executePython(...)}.
     * One package per line, optionally pinned with a version constraint
     * (e.g. {@code reportlab==4.0.7}, {@code qrcode>=7.4}). Blank lines
     * and {@code #} comments are tolerated.
     *
     * <p>Empty string disables auto-install — operators who manage the
     * Python env manually keep working as before.
     *
     * <p>Single global setting (vs. per-tool or per-agent) by design:
     * the Code Interpreter session dir is shared across every script
     * invocation, so a single env applied uniformly is simpler to reason
     * about and to operate. Configured via Console → Settings → Code
     * Interpreter.
     *
     * @since 2026.2.7
     */
    public String getPythonRequirements() {
        return configVars.getString(TurConfigVarOnStartup.PYTHON_REQUIREMENTS, "");
    }

    public String updatePythonRequirements(String requirements) {
        return configVars.setString(TurConfigVarOnStartup.PYTHON_REQUIREMENTS, requirements);
    }

    /**
     * Returns the server-side HMAC secret used to sign / verify the
     * {@code ?exp&sig} query string on {@code /api/v2/code-interpreter/}
     * URLs. Auto-generated on first boot (see
     * {@link TurConfigVarOnStartup#CODE_INTERPRETER_URL_SIGNING_SECRET})
     * so a deployment without manual configuration still gets unguessable
     * signed URLs.
     *
     * <p>Rotate by deleting the row and restarting — the on-startup hook
     * mints a fresh value. Rotation invalidates every previously-emitted
     * URL, which is the desired outcome (an operator-initiated rotation
     * is almost always in response to a leak).
     *
     * <p>Not exposed via the admin API: a secret that round-trips through
     * the UI is one screenshot away from being compromised. Kept inside
     * the service layer only.
     *
     * @since 2026.2.7
     */
    public String getCodeInterpreterUrlSigningSecret() {
        return configVars.getString(TurConfigVarOnStartup.CODE_INTERPRETER_URL_SIGNING_SECRET, "");
    }

    /**
     * Returns whether the URL signing secret is currently populated. Used
     * by the admin Global Settings page to show a "Set" / "Not set" status
     * without ever sending the actual secret to the client. A screenshot
     * of the admin UI must never leak the secret.
     *
     * @since 2026.2.7
     */
    public boolean isCodeInterpreterUrlSigningSecretSet() {
        String value = getCodeInterpreterUrlSigningSecret();
        return value != null && !value.isBlank();
    }

    /**
     * Mints a fresh URL signing secret and persists it, returning a short
     * preview (first 8 chars) for the admin UI to confirm the rotation
     * happened. The full secret never crosses the wire — only the preview
     * + a "regenerated" flag. Calling this invalidates EVERY previously
     * emitted signed URL across the fleet, which is the intended outcome
     * of an operator-initiated rotation (usually a response to a leak).
     *
     * @return preview of the new secret (first 8 chars of the Base64 form)
     * @since 2026.2.7
     */
    public String regenerateCodeInterpreterUrlSigningSecret() {
        String fresh = TurConfigVarOnStartup.generateUrlSigningSecret();
        configVars.setRaw(TurConfigVarOnStartup.CODE_INTERPRETER_URL_SIGNING_SECRET, fresh);
        return fresh.length() >= 8 ? fresh.substring(0, 8) : fresh;
    }

    /**
     * T61 — PII slot retention TTL in hours. Returns 24 as the default
     * (matches the documented out-of-the-box compliance posture). Values
     * &lt;= 0 disable the cleanup job; the UI exposes that as the
     * "Never expire" toggle.
     *
     * @since 2026.3.1
     */
    public int getPiiSlotTtlHours() {
        return configVars.getInt(TurConfigVarOnStartup.PII_SLOT_TTL_HOURS,
                Integer.parseInt(TurConfigVarOnStartup.PII_SLOT_TTL_HOURS_DEFAULT_VALUE));
    }

    /**
     * Persists the PII retention TTL. Clamped to {@code [0, 8760]} (one
     * year) so a typo in the admin form can't accidentally turn retention
     * effectively-off (e.g. 100 years) and the UI sees a stable bound to
     * validate against.
     *
     * @since 2026.3.1
     */
    public int updatePiiSlotTtlHours(int ttlHours) {
        return configVars.setIntClamped(TurConfigVarOnStartup.PII_SLOT_TTL_HOURS, ttlHours, 0, 8760);
    }

    /**
     * T80 — current Code Interpreter execution mode. {@code NATIVE}
     * (default) runs Python as a host subprocess; {@code DOCKER} runs each
     * execution inside a throwaway hardened container. Falls back to
     * {@link TurCodeInterpreterExecutionMode#DEFAULT}
     * when the row is missing or holds an unparseable value.
     *
     * @since 2026.3.1
     */
    public TurCodeInterpreterExecutionMode getCodeInterpreterExecutionMode() {
        return TurCodeInterpreterExecutionMode.fromString(
                configVars.getString(TurConfigVarOnStartup.CODE_INTERPRETER_EXECUTION_MODE,
                        TurConfigVarOnStartup.CODE_INTERPRETER_EXECUTION_MODE_DEFAULT_VALUE));
    }

    public TurCodeInterpreterExecutionMode updateCodeInterpreterExecutionMode(
            TurCodeInterpreterExecutionMode mode) {
        TurCodeInterpreterExecutionMode safe = mode == null ? TurCodeInterpreterExecutionMode.DEFAULT : mode;
        configVars.setRaw(TurConfigVarOnStartup.CODE_INTERPRETER_EXECUTION_MODE, safe.name());
        return safe;
    }

    /**
     * T80 — Docker image used when the execution mode is {@code DOCKER}.
     * Blank falls back to the documented default
     * ({@code python:3.12-slim}); operators usually publish an image with
     * the agents' Python packages baked in.
     *
     * @since 2026.3.1
     */
    public String getCodeInterpreterDockerImage() {
        return configVars.getStringNonBlank(TurConfigVarOnStartup.CODE_INTERPRETER_DOCKER_IMAGE,
                TurConfigVarOnStartup.CODE_INTERPRETER_DOCKER_IMAGE_DEFAULT_VALUE);
    }

    public String updateCodeInterpreterDockerImage(String image) {
        String value = (image == null || image.isBlank())
                ? TurConfigVarOnStartup.CODE_INTERPRETER_DOCKER_IMAGE_DEFAULT_VALUE
                : image.trim();
        configVars.setRaw(TurConfigVarOnStartup.CODE_INTERPRETER_DOCKER_IMAGE, value);
        return value;
    }

    /**
     * T321 — Docker image used for the Anthropic-compatible skill sandbox
     * session (a {@code bash}-driven interactive container with the skill
     * folder mounted). Should bundle python + node + bash to match Anthropic's
     * skill container; blank falls back to the documented default
     * ({@code python:3.12-slim}). Only consulted when the execution mode is
     * {@code DOCKER}.
     *
     * @since 2026.3.1
     */
    public String getCodeInterpreterSkillImage() {
        return configVars.getStringNonBlank(TurConfigVarOnStartup.CODE_INTERPRETER_SKILL_IMAGE,
                TurConfigVarOnStartup.CODE_INTERPRETER_SKILL_IMAGE_DEFAULT_VALUE);
    }

    public String updateCodeInterpreterSkillImage(String image) {
        String value = (image == null || image.isBlank())
                ? TurConfigVarOnStartup.CODE_INTERPRETER_SKILL_IMAGE_DEFAULT_VALUE
                : image.trim();
        configVars.setRaw(TurConfigVarOnStartup.CODE_INTERPRETER_SKILL_IMAGE, value);
        return value;
    }

    public String getDefaultLlmId() {
        return configVars.getString(TurConfigVarOnStartup.DEFAULT_LLM, "");
    }

    public String updateDefaultLlmId(String defaultLlmId) {
        return configVars.setString(TurConfigVarOnStartup.DEFAULT_LLM, defaultLlmId);
    }

    /**
     * T517 — the {@code TurLLMInstance} id bound to {@code lane} (blank when the
     * lane falls back to the default LLM).
     */
    public String getModelLaneInstanceId(com.viglet.turing.system.lane.TurModelLane lane) {
        return configVars.getString(modelLaneConfigVar(lane), "");
    }

    public String updateModelLaneInstanceId(com.viglet.turing.system.lane.TurModelLane lane,
            String instanceId) {
        return configVars.setString(modelLaneConfigVar(lane), instanceId == null ? "" : instanceId);
    }

    private static String modelLaneConfigVar(com.viglet.turing.system.lane.TurModelLane lane) {
        return switch (lane) {
            case FAST -> TurConfigVarOnStartup.MODEL_LANE_FAST;
            case REASONING -> TurConfigVarOnStartup.MODEL_LANE_REASONING;
            case CHEAP -> TurConfigVarOnStartup.MODEL_LANE_CHEAP;
        };
    }

    /**
     * T518 — the ordered cross-provider fallback chain (LLM instance ids). Parsed
     * from the comma-separated {@code GLOBAL_LLM_FALLBACK_CHAIN}; blank entries
     * are dropped. Empty list = no chain (legacy single-model path).
     */
    public java.util.List<String> getLlmFallbackChainIds() {
        String raw = configVars.getString(TurConfigVarOnStartup.LLM_FALLBACK_CHAIN, "");
        if (raw == null || raw.isBlank()) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** T518 — persist the fallback chain as a comma-separated id list. */
    public java.util.List<String> updateLlmFallbackChainIds(java.util.List<String> ids) {
        java.util.List<String> clean = ids == null ? java.util.List.of()
                : ids.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
        configVars.setRaw(TurConfigVarOnStartup.LLM_FALLBACK_CHAIN, String.join(",", clean));
        return clean;
    }

    /** T518 — fallback routing mode; unknown/blank falls back to {@code PRIORITY}. */
    public com.viglet.turing.resilience.llm.TurLlmFallbackMode getLlmFallbackMode() {
        return configVars.find(TurConfigVarOnStartup.LLM_FALLBACK_MODE)
                .map(com.viglet.turing.resilience.llm.TurLlmFallbackMode::fromValue)
                .orElse(com.viglet.turing.resilience.llm.TurLlmFallbackMode.PRIORITY);
    }

    public com.viglet.turing.resilience.llm.TurLlmFallbackMode updateLlmFallbackMode(
            com.viglet.turing.resilience.llm.TurLlmFallbackMode mode) {
        com.viglet.turing.resilience.llm.TurLlmFallbackMode value =
                mode == null ? com.viglet.turing.resilience.llm.TurLlmFallbackMode.PRIORITY : mode;
        configVars.setRaw(TurConfigVarOnStartup.LLM_FALLBACK_MODE, value.name());
        return value;
    }

    /** T522 — whether the multi-provider "second opinion" cross-check runs. */
    public boolean isSecondOpinionEnabled() {
        return configVars.getBoolean(TurConfigVarOnStartup.SECOND_OPINION_ENABLED, false);
    }

    public boolean updateSecondOpinionEnabled(boolean enabled) {
        return configVars.setBoolean(TurConfigVarOnStartup.SECOND_OPINION_ENABLED, enabled);
    }

    /** T522 — the critic {@code TurLLMInstance} id (a different vendor than the answerer). */
    public String getSecondOpinionLlmId() {
        return configVars.getString(TurConfigVarOnStartup.SECOND_OPINION_LLM, "");
    }

    public String updateSecondOpinionLlmId(String llmId) {
        return configVars.setString(TurConfigVarOnStartup.SECOND_OPINION_LLM, llmId);
    }

    public TurEmailProviderType getEmailProvider() {
        return configVars.getEnum(TurEmailProviderType.class, TurConfigVarOnStartup.EMAIL_PROVIDER,
                TurEmailProviderType.valueOf(TurConfigVarOnStartup.EMAIL_PROVIDER_DEFAULT_VALUE));
    }

    public TurEmailProviderType updateEmailProvider(TurEmailProviderType emailProvider) {
        return configVars.setEnum(TurConfigVarOnStartup.EMAIL_PROVIDER, emailProvider,
                TurEmailProviderType.valueOf(TurConfigVarOnStartup.EMAIL_PROVIDER_DEFAULT_VALUE));
    }

    public String getEmailApiKey() {
        return configVars.getString(TurConfigVarOnStartup.EMAIL_API_KEY, "");
    }

    public String updateEmailApiKey(String emailApiKey) {
        return configVars.setString(TurConfigVarOnStartup.EMAIL_API_KEY, emailApiKey);
    }

    public String getSenderEmail() {
        return configVars.getString(TurConfigVarOnStartup.SENDER_EMAIL, "");
    }

    public String updateSenderEmail(String senderEmail) {
        return configVars.setString(TurConfigVarOnStartup.SENDER_EMAIL, senderEmail);
    }

    public String getSenderName() {
        return configVars.getString(TurConfigVarOnStartup.SENDER_NAME, "");
    }

    public String updateSenderName(String senderName) {
        return configVars.setString(TurConfigVarOnStartup.SENDER_NAME, senderName);
    }

    public boolean isLlmCacheEnabled() {
        return configVars.getBoolean(TurConfigVarOnStartup.LLM_CACHE_ENABLED, false);
    }

    public boolean updateLlmCacheEnabled(boolean enabled) {
        return configVars.setBoolean(TurConfigVarOnStartup.LLM_CACHE_ENABLED, enabled);
    }

    public long getLlmCacheTtlMs() {
        return configVars.getLong(TurConfigVarOnStartup.LLM_CACHE_TTL_MS, 3_600_000L);
    }

    public long updateLlmCacheTtlMs(long ttlMs) {
        return configVars.setLong(TurConfigVarOnStartup.LLM_CACHE_TTL_MS, Math.max(0, ttlMs));
    }

    public boolean isLlmCacheRegenerate() {
        return configVars.getBoolean(TurConfigVarOnStartup.LLM_CACHE_REGENERATE, false);
    }

    public boolean updateLlmCacheRegenerate(boolean regenerate) {
        return configVars.setBoolean(TurConfigVarOnStartup.LLM_CACHE_REGENERATE, regenerate);
    }

    public String getRecipientEmail() {
        return configVars.getString(TurConfigVarOnStartup.RECIPIENT_EMAIL, "");
    }

    public String updateRecipientEmail(String recipientEmail) {
        return configVars.setString(TurConfigVarOnStartup.RECIPIENT_EMAIL, recipientEmail);
    }

    public boolean isRagEnabled() {
        return configVars.getBoolean(TurConfigVarOnStartup.RAG_ENABLED, false);
    }

    public boolean updateRagEnabled(boolean enabled) {
        return configVars.setBoolean(TurConfigVarOnStartup.RAG_ENABLED, enabled);
    }

    /**
     * T328 — relevance floor applied on the SN RAG filtered retrieval path.
     * Defaults to {@code 0.0} (legacy: every filter-matching hit is stuffed);
     * clamped to {@code [0.0, 1.0]}. Invalid values fall back to the default.
     */
    public double getRagSnSimilarityThreshold() {
        return configVars.getDoubleClamped(TurConfigVarOnStartup.RAG_SN_SIMILARITY_THRESHOLD, 0.0, 0.0, 1.0);
    }

    public double updateRagSnSimilarityThreshold(double threshold) {
        double value = Math.clamp(threshold, 0.0, 1.0);
        configVars.setDouble(TurConfigVarOnStartup.RAG_SN_SIMILARITY_THRESHOLD, value);
        return value;
    }

    /** T328 — whether the opt-in LLM-as-reranker runs on SN RAG candidates. */
    public boolean isRagSnRerankEnabled() {
        return configVars.getBoolean(TurConfigVarOnStartup.RAG_SN_RERANK_ENABLED, false);
    }

    public boolean updateRagSnRerankEnabled(boolean enabled) {
        return configVars.setBoolean(TurConfigVarOnStartup.RAG_SN_RERANK_ENABLED, enabled);
    }

    /**
     * T328 — max chunks kept after the reranker narrows the candidate pool (the
     * stuffed top-k). Clamped to {@code [1, 100]}; invalid values fall back to
     * the default of 20.
     */
    public int getRagSnRerankTopN() {
        return configVars.getIntClamped(TurConfigVarOnStartup.RAG_SN_RERANK_TOP_N, 20, 1, 100);
    }

    public int updateRagSnRerankTopN(int topN) {
        return configVars.setIntClamped(TurConfigVarOnStartup.RAG_SN_RERANK_TOP_N, topN, 1, 100);
    }

    /**
     * T337 — which reranker backend runs. Unknown/blank values fall back to
     * {@link TurRagRerankStrategyType#LLM} (legacy default), so a corrupt row
     * can never break reranking.
     */
    public TurRagRerankStrategyType getRagSnRerankStrategy() {
        return configVars.find(TurConfigVarOnStartup.RAG_SN_RERANK_STRATEGY)
                .map(TurRagRerankStrategyType::fromValue)
                .orElse(TurRagRerankStrategyType.LLM);
    }

    @CacheEvict(cacheNames = "turRagRerankScore", allEntries = true)
    public TurRagRerankStrategyType updateRagSnRerankStrategy(TurRagRerankStrategyType strategy) {
        TurRagRerankStrategyType value = strategy == null ? TurRagRerankStrategyType.LLM : strategy;
        configVars.setRaw(TurConfigVarOnStartup.RAG_SN_RERANK_STRATEGY, value.name());
        return value;
    }

    /** T338 — self-hosted cross-encoder {@code /rerank} endpoint URL. */
    public String getRagSnRerankEndpoint() {
        return configVars.getString(TurConfigVarOnStartup.RAG_SN_RERANK_ENDPOINT, "");
    }

    @CacheEvict(cacheNames = "turRagRerankScore", allEntries = true)
    public String updateRagSnRerankEndpoint(String endpoint) {
        return configVars.setString(TurConfigVarOnStartup.RAG_SN_RERANK_ENDPOINT, endpoint);
    }

    /** T338 — rerank model name (cross-encoder / Cohere); blank = backend default. */
    public String getRagSnRerankModel() {
        return configVars.getString(TurConfigVarOnStartup.RAG_SN_RERANK_MODEL, "");
    }

    @CacheEvict(cacheNames = "turRagRerankScore", allEntries = true)
    public String updateRagSnRerankModel(String model) {
        return configVars.setString(TurConfigVarOnStartup.RAG_SN_RERANK_MODEL, model);
    }

    /** T521 — AWS region for the managed Bedrock Rerank API ({@code BEDROCK}). */
    public String getRagSnRerankRegion() {
        return configVars.getStringNonBlank(TurConfigVarOnStartup.RAG_SN_RERANK_REGION,
                TurConfigVarOnStartup.RAG_SN_RERANK_REGION_DEFAULT_VALUE);
    }

    @CacheEvict(cacheNames = "turRagRerankScore", allEntries = true)
    public String updateRagSnRerankRegion(String region) {
        return configVars.setString(TurConfigVarOnStartup.RAG_SN_RERANK_REGION, region);
    }

    /** T521 — GCP project for the managed Vertex AI Ranking API ({@code VERTEX_AI}). */
    public String getRagSnRerankVertexProject() {
        return configVars.getString(TurConfigVarOnStartup.RAG_SN_RERANK_VERTEX_PROJECT, "");
    }

    @CacheEvict(cacheNames = "turRagRerankScore", allEntries = true)
    public String updateRagSnRerankVertexProject(String project) {
        return configVars.setString(TurConfigVarOnStartup.RAG_SN_RERANK_VERTEX_PROJECT, project);
    }

    /** T521 — GCP location for Vertex AI Ranking (default {@code global}). */
    public String getRagSnRerankVertexLocation() {
        return configVars.getStringNonBlank(TurConfigVarOnStartup.RAG_SN_RERANK_VERTEX_LOCATION,
                TurConfigVarOnStartup.RAG_SN_RERANK_VERTEX_LOCATION_DEFAULT_VALUE);
    }

    @CacheEvict(cacheNames = "turRagRerankScore", allEntries = true)
    public String updateRagSnRerankVertexLocation(String location) {
        return configVars.setString(TurConfigVarOnStartup.RAG_SN_RERANK_VERTEX_LOCATION, location);
    }

    /**
     * T341 — whether reranker results are memoized in the
     * {@code turRagRerankScore} cache. Default off: a rerank ordering is
     * deterministic per {@code (strategy, model, query, candidate-set)}, so
     * caching skips repeat scoring of an identical query+pool, but the win is
     * only realized when the same query keeps hitting the same retrieval pool —
     * ship it only after profiling confirms that pattern in production.
     */
    public boolean isRagSnRerankCacheEnabled() {
        return configVars.getBoolean(TurConfigVarOnStartup.RAG_SN_RERANK_CACHE_ENABLED, false);
    }

    @CacheEvict(cacheNames = "turRagRerankScore", allEntries = true)
    public boolean updateRagSnRerankCacheEnabled(boolean enabled) {
        return configVars.setBoolean(TurConfigVarOnStartup.RAG_SN_RERANK_CACHE_ENABLED, enabled);
    }

    /**
     * T339 — the decrypted Cohere API key, or {@code ""} when unset. Stored
     * encrypted at rest via {@link TurSecretCryptoService}; never echoed back to
     * the client (the API exposes only a "configured" boolean).
     */
    public String getRagSnRerankApiKey() {
        String stored = configVars.getString(TurConfigVarOnStartup.RAG_SN_RERANK_API_KEY, "");
        if (stored.isBlank()) {
            return "";
        }
        String decrypted = turSecretCryptoService.decrypt(stored);
        return decrypted == null ? "" : decrypted;
    }

    /** T339 — whether a Cohere API key is configured (for the write-only UI status). */
    public boolean isRagSnRerankApiKeySet() {
        return configVars.find(TurConfigVarOnStartup.RAG_SN_RERANK_API_KEY)
                .filter(v -> !v.isBlank())
                .isPresent();
    }

    /**
     * T339 — sets the Cohere API key (encrypted at rest). A {@code null}/blank
     * value clears it. Returns the resulting "configured" status, never the key.
     */
    @CacheEvict(cacheNames = "turRagRerankScore", allEntries = true)
    public boolean updateRagSnRerankApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            configVars.setRaw(TurConfigVarOnStartup.RAG_SN_RERANK_API_KEY, "");
            return false;
        }
        configVars.setRaw(TurConfigVarOnStartup.RAG_SN_RERANK_API_KEY,
                turSecretCryptoService.encrypt(apiKey.trim()));
        return true;
    }

    // ---------------------------------------------------------------------
    // T687 / §XLII.1 — Transcription (speech-to-text) backend selection.
    // Config-var backed (UI-editable); a non-blank turing.transcription.* env
    // prop overrides these at resolution time (TurTranscriptionConfigResolver).
    // ---------------------------------------------------------------------

    /**
     * T687 — active transcription backend. Unknown/blank values fall back to
     * {@link TurTranscriptionProviderType#OPENAI} (the legacy default), so a
     * corrupt row can never break transcription.
     */
    public TurTranscriptionProviderType getTranscriptionStrategy() {
        return configVars.find(TurConfigVarOnStartup.TRANSCRIPTION_STRATEGY)
                .map(TurTranscriptionProviderType::fromValue)
                .orElse(TurTranscriptionProviderType.OPENAI);
    }

    public TurTranscriptionProviderType updateTranscriptionStrategy(TurTranscriptionProviderType strategy) {
        TurTranscriptionProviderType value =
                strategy == null ? TurTranscriptionProviderType.OPENAI : strategy;
        configVars.setRaw(TurConfigVarOnStartup.TRANSCRIPTION_STRATEGY, value.name());
        return value;
    }

    /** T687 — dedicated OpenAI-compatible endpoint; blank falls back to the default LLM instance URL. */
    public String getTranscriptionEndpoint() {
        return configVars.getString(TurConfigVarOnStartup.TRANSCRIPTION_ENDPOINT, "");
    }

    public String updateTranscriptionEndpoint(String endpoint) {
        return configVars.setString(TurConfigVarOnStartup.TRANSCRIPTION_ENDPOINT, endpoint);
    }

    /** T687 — transcription model name; blank uses the backend default (e.g. {@code whisper-1}). */
    public String getTranscriptionModel() {
        return configVars.getString(TurConfigVarOnStartup.TRANSCRIPTION_MODEL, "");
    }

    public String updateTranscriptionModel(String model) {
        return configVars.setString(TurConfigVarOnStartup.TRANSCRIPTION_MODEL, model);
    }

    /**
     * T687 — per-request upload limit in bytes for the active backend. Audio
     * above this is chunked (T688/T689). Defaults to 25 MiB (26,214,400); a
     * non-positive/blank value falls back to the default.
     */
    public long getTranscriptionMaxUploadBytes() {
        long value = configVars.getLong(TurConfigVarOnStartup.TRANSCRIPTION_MAX_UPLOAD_BYTES,
                Long.parseLong(TurConfigVarOnStartup.TRANSCRIPTION_MAX_UPLOAD_BYTES_DEFAULT_VALUE));
        return value > 0 ? value
                : Long.parseLong(TurConfigVarOnStartup.TRANSCRIPTION_MAX_UPLOAD_BYTES_DEFAULT_VALUE);
    }

    public long updateTranscriptionMaxUploadBytes(long maxUploadBytes) {
        return configVars.setLong(TurConfigVarOnStartup.TRANSCRIPTION_MAX_UPLOAD_BYTES, maxUploadBytes);
    }

    /**
     * T687 — the decrypted transcription API key, or {@code ""} when unset.
     * Stored encrypted at rest; never echoed back to the client.
     */
    public String getTranscriptionApiKey() {
        String stored = configVars.getString(TurConfigVarOnStartup.TRANSCRIPTION_API_KEY, "");
        if (stored.isBlank()) {
            return "";
        }
        String decrypted = turSecretCryptoService.decrypt(stored);
        return decrypted == null ? "" : decrypted;
    }

    /** T687 — whether a transcription API key is configured (write-only UI status). */
    public boolean isTranscriptionApiKeySet() {
        return configVars.find(TurConfigVarOnStartup.TRANSCRIPTION_API_KEY)
                .filter(v -> !v.isBlank())
                .isPresent();
    }

    /**
     * T687 — sets the transcription API key (encrypted at rest). A {@code null}/
     * blank value clears it. Returns the resulting "configured" status.
     */
    public boolean updateTranscriptionApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            configVars.setRaw(TurConfigVarOnStartup.TRANSCRIPTION_API_KEY, "");
            return false;
        }
        configVars.setRaw(TurConfigVarOnStartup.TRANSCRIPTION_API_KEY,
                turSecretCryptoService.encrypt(apiKey.trim()));
        return true;
    }

    // ---------------------------------------------------------------------
    // T739 / §XLVIII — URL content-fetch mode + browserless sidecar. Config-var
    // backed (UI-editable); a non-blank turing.url-fetch.* env prop overrides
    // these at resolution time (TurUrlFetchConfigResolver).
    // ---------------------------------------------------------------------

    /**
     * T739 — active URL-fetch mode. Unknown/blank values fall back to
     * {@link com.viglet.turing.genai.urlfetch.TurUrlFetchMode#SIMPLE} (the legacy
     * default), so a corrupt row can never break ingestion.
     */
    public com.viglet.turing.genai.urlfetch.TurUrlFetchMode getUrlFetchMode() {
        return com.viglet.turing.genai.urlfetch.TurUrlFetchMode.fromValue(
                configVars.getString(TurConfigVarOnStartup.URL_FETCH_MODE,
                        TurConfigVarOnStartup.URL_FETCH_MODE_DEFAULT_VALUE));
    }

    public com.viglet.turing.genai.urlfetch.TurUrlFetchMode updateUrlFetchMode(
            com.viglet.turing.genai.urlfetch.TurUrlFetchMode mode) {
        com.viglet.turing.genai.urlfetch.TurUrlFetchMode value =
                mode == null ? com.viglet.turing.genai.urlfetch.TurUrlFetchMode.DEFAULT : mode;
        configVars.setRaw(TurConfigVarOnStartup.URL_FETCH_MODE, value.name());
        return value;
    }

    /** T739 — browserless sidecar base URL; blank = no sidecar (HEADLESS/AUTO degrade to SIMPLE). */
    public String getUrlFetchBrowserlessUrl() {
        return configVars.getString(TurConfigVarOnStartup.URL_FETCH_BROWSERLESS_URL, "");
    }

    public String updateUrlFetchBrowserlessUrl(String url) {
        return configVars.setString(TurConfigVarOnStartup.URL_FETCH_BROWSERLESS_URL, url);
    }

    /**
     * T739 — the decrypted browserless token, or {@code ""} when unset. Stored
     * encrypted at rest; never echoed back to the client.
     */
    public String getUrlFetchBrowserlessToken() {
        String stored = configVars.getString(TurConfigVarOnStartup.URL_FETCH_BROWSERLESS_TOKEN, "");
        if (stored.isBlank()) {
            return "";
        }
        String decrypted = turSecretCryptoService.decrypt(stored);
        return decrypted == null ? "" : decrypted;
    }

    /** T739 — whether a browserless token is configured (write-only UI status). */
    public boolean isUrlFetchBrowserlessTokenSet() {
        return configVars.find(TurConfigVarOnStartup.URL_FETCH_BROWSERLESS_TOKEN)
                .filter(v -> !v.isBlank())
                .isPresent();
    }

    /**
     * T739 — sets the browserless token (encrypted at rest). A {@code null}/blank
     * value clears it. Returns the resulting "configured" status.
     */
    public boolean updateUrlFetchBrowserlessToken(String token) {
        if (token == null || token.isBlank()) {
            configVars.setRaw(TurConfigVarOnStartup.URL_FETCH_BROWSERLESS_TOKEN, "");
            return false;
        }
        configVars.setRaw(TurConfigVarOnStartup.URL_FETCH_BROWSERLESS_TOKEN,
                turSecretCryptoService.encrypt(token.trim()));
        return true;
    }

    /** T331 — whether grounded follow-up questions are generated for SN answers. */
    public boolean isRagSnFollowupsEnabled() {
        return configVars.getBoolean(TurConfigVarOnStartup.RAG_SN_FOLLOWUPS_ENABLED, false);
    }

    public boolean updateRagSnFollowupsEnabled(boolean enabled) {
        return configVars.setBoolean(TurConfigVarOnStartup.RAG_SN_FOLLOWUPS_ENABLED, enabled);
    }

    /** T330 — whether the post-generation groundedness audit runs on SN answers. */
    public boolean isRagSnGroundednessCheckEnabled() {
        return configVars.getBoolean(TurConfigVarOnStartup.RAG_SN_GROUNDEDNESS_CHECK_ENABLED, false);
    }

    public boolean updateRagSnGroundednessCheckEnabled(boolean enabled) {
        return configVars.setBoolean(TurConfigVarOnStartup.RAG_SN_GROUNDEDNESS_CHECK_ENABLED, enabled);
    }

    public String getDefaultEmbeddingModelId() {
        return configVars.getString(TurConfigVarOnStartup.DEFAULT_EMBEDDING_MODEL_ID, "");
    }

    public String updateDefaultEmbeddingModelId(String id) {
        return configVars.setString(TurConfigVarOnStartup.DEFAULT_EMBEDDING_MODEL_ID, id);
    }

    public String getDefaultEmbeddingStoreId() {
        return configVars.getString(TurConfigVarOnStartup.DEFAULT_EMBEDDING_STORE_ID, "");
    }

    public String updateDefaultEmbeddingStoreId(String id) {
        return configVars.setString(TurConfigVarOnStartup.DEFAULT_EMBEDDING_STORE_ID, id);
    }

    public String getDefaultAiAgentId() {
        return configVars.getString(TurConfigVarOnStartup.DEFAULT_AI_AGENT_ID, "");
    }

    public String updateDefaultAiAgentId(String id) {
        return configVars.setString(TurConfigVarOnStartup.DEFAULT_AI_AGENT_ID, id);
    }
}
