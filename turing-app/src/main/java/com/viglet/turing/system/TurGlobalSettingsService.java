package com.viglet.turing.system;

import java.util.Locale;

import org.springframework.stereotype.Service;

import com.viglet.turing.genai.rag.rerank.TurRagRerankStrategyType;
import com.viglet.turing.onstartup.system.TurConfigVarOnStartup;
import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import com.viglet.turing.system.security.TurSecretCryptoService;

@Service
public class TurGlobalSettingsService {
    private final TurConfigVarRepository turConfigVarRepository;
    private final TurSecretCryptoService turSecretCryptoService;

    public TurGlobalSettingsService(TurConfigVarRepository turConfigVarRepository,
            TurSecretCryptoService turSecretCryptoService) {
        this.turConfigVarRepository = turConfigVarRepository;
        this.turSecretCryptoService = turSecretCryptoService;
    }

    public TurGlobalDecimalSeparator getDecimalSeparator() {
        String value = turConfigVarRepository.findById(TurConfigVarOnStartup.DECIMAL_SEPARATOR)
                .map(TurConfigVar::getValue)
                .orElse(TurConfigVarOnStartup.DECIMAL_SEPARATOR_DEFAULT_VALUE);

        try {
            return TurGlobalDecimalSeparator
                    .valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return TurGlobalDecimalSeparator
                    .valueOf(TurConfigVarOnStartup.DECIMAL_SEPARATOR_DEFAULT_VALUE);
        }
    }

    public TurGlobalDecimalSeparator updateDecimalSeparator(TurGlobalDecimalSeparator decimalSeparator) {
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.DECIMAL_SEPARATOR)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.DECIMAL_SEPARATOR);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(decimalSeparator.name());
        turConfigVarRepository.save(configVar);
        return decimalSeparator;
    }

    public String getPythonExecutable() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.PYTHON_EXECUTABLE)
                .map(TurConfigVar::getValue)
                .orElse("");
    }

    public String updatePythonExecutable(String pythonExecutable) {
        String value = pythonExecutable == null ? "" : pythonExecutable.trim();
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.PYTHON_EXECUTABLE)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.PYTHON_EXECUTABLE);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
        return value;
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
        return turConfigVarRepository.findById(TurConfigVarOnStartup.PYTHON_REQUIREMENTS)
                .map(TurConfigVar::getValue)
                .orElse("");
    }

    public String updatePythonRequirements(String requirements) {
        String value = requirements == null ? "" : requirements.trim();
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.PYTHON_REQUIREMENTS)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.PYTHON_REQUIREMENTS);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
        return value;
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
        return turConfigVarRepository.findById(TurConfigVarOnStartup.CODE_INTERPRETER_URL_SIGNING_SECRET)
                .map(TurConfigVar::getValue)
                .orElse("");
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
        TurConfigVar row = turConfigVarRepository
                .findById(TurConfigVarOnStartup.CODE_INTERPRETER_URL_SIGNING_SECRET)
                .orElseGet(() -> {
                    TurConfigVar v = new TurConfigVar();
                    v.setId(TurConfigVarOnStartup.CODE_INTERPRETER_URL_SIGNING_SECRET);
                    v.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
                    return v;
                });
        row.setValue(fresh);
        turConfigVarRepository.save(row);
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
        String raw = turConfigVarRepository.findById(TurConfigVarOnStartup.PII_SLOT_TTL_HOURS)
                .map(TurConfigVar::getValue)
                .orElse(TurConfigVarOnStartup.PII_SLOT_TTL_HOURS_DEFAULT_VALUE);
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return Integer.parseInt(TurConfigVarOnStartup.PII_SLOT_TTL_HOURS_DEFAULT_VALUE);
        }
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
        int clamped = Math.max(0, Math.min(ttlHours, 8760));
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.PII_SLOT_TTL_HOURS)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.PII_SLOT_TTL_HOURS);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(Integer.toString(clamped));
        turConfigVarRepository.save(configVar);
        return clamped;
    }

    /**
     * T80 — current Code Interpreter execution mode. {@code NATIVE}
     * (default) runs Python as a host subprocess; {@code DOCKER} runs each
     * execution inside a throwaway hardened container. Falls back to
     * {@link com.viglet.turing.genai.tool.TurCodeInterpreterExecutionMode#DEFAULT}
     * when the row is missing or holds an unparseable value.
     *
     * @since 2026.3.1
     */
    public com.viglet.turing.genai.tool.TurCodeInterpreterExecutionMode getCodeInterpreterExecutionMode() {
        String value = turConfigVarRepository.findById(TurConfigVarOnStartup.CODE_INTERPRETER_EXECUTION_MODE)
                .map(TurConfigVar::getValue)
                .orElse(TurConfigVarOnStartup.CODE_INTERPRETER_EXECUTION_MODE_DEFAULT_VALUE);
        return com.viglet.turing.genai.tool.TurCodeInterpreterExecutionMode.fromString(value);
    }

    public com.viglet.turing.genai.tool.TurCodeInterpreterExecutionMode updateCodeInterpreterExecutionMode(
            com.viglet.turing.genai.tool.TurCodeInterpreterExecutionMode mode) {
        com.viglet.turing.genai.tool.TurCodeInterpreterExecutionMode safe =
                mode == null ? com.viglet.turing.genai.tool.TurCodeInterpreterExecutionMode.DEFAULT : mode;
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.CODE_INTERPRETER_EXECUTION_MODE)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.CODE_INTERPRETER_EXECUTION_MODE);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(safe.name());
        turConfigVarRepository.save(configVar);
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
        String value = turConfigVarRepository.findById(TurConfigVarOnStartup.CODE_INTERPRETER_DOCKER_IMAGE)
                .map(TurConfigVar::getValue)
                .orElse(TurConfigVarOnStartup.CODE_INTERPRETER_DOCKER_IMAGE_DEFAULT_VALUE);
        return (value == null || value.isBlank())
                ? TurConfigVarOnStartup.CODE_INTERPRETER_DOCKER_IMAGE_DEFAULT_VALUE
                : value.trim();
    }

    public String updateCodeInterpreterDockerImage(String image) {
        String value = (image == null || image.isBlank())
                ? TurConfigVarOnStartup.CODE_INTERPRETER_DOCKER_IMAGE_DEFAULT_VALUE
                : image.trim();
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.CODE_INTERPRETER_DOCKER_IMAGE)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.CODE_INTERPRETER_DOCKER_IMAGE);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
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
        String value = turConfigVarRepository.findById(TurConfigVarOnStartup.CODE_INTERPRETER_SKILL_IMAGE)
                .map(TurConfigVar::getValue)
                .orElse(TurConfigVarOnStartup.CODE_INTERPRETER_SKILL_IMAGE_DEFAULT_VALUE);
        return (value == null || value.isBlank())
                ? TurConfigVarOnStartup.CODE_INTERPRETER_SKILL_IMAGE_DEFAULT_VALUE
                : value.trim();
    }

    public String updateCodeInterpreterSkillImage(String image) {
        String value = (image == null || image.isBlank())
                ? TurConfigVarOnStartup.CODE_INTERPRETER_SKILL_IMAGE_DEFAULT_VALUE
                : image.trim();
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.CODE_INTERPRETER_SKILL_IMAGE)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.CODE_INTERPRETER_SKILL_IMAGE);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
        return value;
    }

    public String getDefaultLlmId() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_LLM)
                .map(TurConfigVar::getValue)
                .orElse("");
    }

    public String updateDefaultLlmId(String defaultLlmId) {
        String value = defaultLlmId == null ? "" : defaultLlmId.trim();
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_LLM)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.DEFAULT_LLM);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
        return value;
    }

    public TurEmailProviderType getEmailProvider() {
        String value = turConfigVarRepository.findById(TurConfigVarOnStartup.EMAIL_PROVIDER)
                .map(TurConfigVar::getValue)
                .orElse(TurConfigVarOnStartup.EMAIL_PROVIDER_DEFAULT_VALUE);
        try {
            return TurEmailProviderType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return TurEmailProviderType.valueOf(TurConfigVarOnStartup.EMAIL_PROVIDER_DEFAULT_VALUE);
        }
    }

    public TurEmailProviderType updateEmailProvider(TurEmailProviderType emailProvider) {
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.EMAIL_PROVIDER)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.EMAIL_PROVIDER);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(emailProvider.name());
        turConfigVarRepository.save(configVar);
        return emailProvider;
    }

    public String getEmailApiKey() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.EMAIL_API_KEY)
                .map(TurConfigVar::getValue)
                .orElse("");
    }

    public String updateEmailApiKey(String emailApiKey) {
        String value = emailApiKey == null ? "" : emailApiKey.trim();
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.EMAIL_API_KEY)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.EMAIL_API_KEY);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
        return value;
    }

    public String getSenderEmail() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.SENDER_EMAIL)
                .map(TurConfigVar::getValue)
                .orElse("");
    }

    public String updateSenderEmail(String senderEmail) {
        String value = senderEmail == null ? "" : senderEmail.trim();
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.SENDER_EMAIL)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.SENDER_EMAIL);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
        return value;
    }

    public String getSenderName() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.SENDER_NAME)
                .map(TurConfigVar::getValue)
                .orElse("");
    }

    public String updateSenderName(String senderName) {
        String value = senderName == null ? "" : senderName.trim();
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.SENDER_NAME)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.SENDER_NAME);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
        return value;
    }

    public boolean isLlmCacheEnabled() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_ENABLED)
                .map(TurConfigVar::getValue)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    public boolean updateLlmCacheEnabled(boolean enabled) {
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_ENABLED)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.LLM_CACHE_ENABLED);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(String.valueOf(enabled));
        turConfigVarRepository.save(configVar);
        return enabled;
    }

    public long getLlmCacheTtlMs() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_TTL_MS)
                .map(TurConfigVar::getValue)
                .map(v -> {
                    try {
                        return Long.parseLong(v.trim());
                    } catch (NumberFormatException e) {
                        return 3_600_000L;
                    }
                })
                .orElse(3_600_000L);
    }

    public long updateLlmCacheTtlMs(long ttlMs) {
        long value = Math.max(0, ttlMs);
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_TTL_MS)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.LLM_CACHE_TTL_MS);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(String.valueOf(value));
        turConfigVarRepository.save(configVar);
        return value;
    }

    public boolean isLlmCacheRegenerate() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_REGENERATE)
                .map(TurConfigVar::getValue)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    public boolean updateLlmCacheRegenerate(boolean regenerate) {
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_REGENERATE)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.LLM_CACHE_REGENERATE);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(String.valueOf(regenerate));
        turConfigVarRepository.save(configVar);
        return regenerate;
    }

    public String getRecipientEmail() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.RECIPIENT_EMAIL)
                .map(TurConfigVar::getValue)
                .orElse("");
    }

    public String updateRecipientEmail(String recipientEmail) {
        String value = recipientEmail == null ? "" : recipientEmail.trim();
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.RECIPIENT_EMAIL)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.RECIPIENT_EMAIL);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
        return value;
    }

    public boolean isRagEnabled() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.RAG_ENABLED)
                .map(TurConfigVar::getValue)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    public boolean updateRagEnabled(boolean enabled) {
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.RAG_ENABLED)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.RAG_ENABLED);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(String.valueOf(enabled));
        turConfigVarRepository.save(configVar);
        return enabled;
    }

    /**
     * T328 — relevance floor applied on the SN RAG filtered retrieval path.
     * Defaults to {@code 0.0} (legacy: every filter-matching hit is stuffed);
     * clamped to {@code [0.0, 1.0]}. Invalid values fall back to the default.
     */
    public double getRagSnSimilarityThreshold() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.RAG_SN_SIMILARITY_THRESHOLD)
                .map(TurConfigVar::getValue)
                .map(v -> {
                    try {
                        double parsed = Double.parseDouble(v.trim());
                        return Math.max(0.0, Math.min(1.0, parsed));
                    } catch (NumberFormatException e) {
                        return 0.0;
                    }
                })
                .orElse(0.0);
    }

    public double updateRagSnSimilarityThreshold(double threshold) {
        double value = Math.max(0.0, Math.min(1.0, threshold));
        TurConfigVar configVar = turConfigVarRepository
                .findById(TurConfigVarOnStartup.RAG_SN_SIMILARITY_THRESHOLD)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.RAG_SN_SIMILARITY_THRESHOLD);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(String.valueOf(value));
        turConfigVarRepository.save(configVar);
        return value;
    }

    /** T328 — whether the opt-in LLM-as-reranker runs on SN RAG candidates. */
    public boolean isRagSnRerankEnabled() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.RAG_SN_RERANK_ENABLED)
                .map(TurConfigVar::getValue)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    public boolean updateRagSnRerankEnabled(boolean enabled) {
        TurConfigVar configVar = turConfigVarRepository
                .findById(TurConfigVarOnStartup.RAG_SN_RERANK_ENABLED)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.RAG_SN_RERANK_ENABLED);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(String.valueOf(enabled));
        turConfigVarRepository.save(configVar);
        return enabled;
    }

    /**
     * T328 — max chunks kept after the reranker narrows the candidate pool (the
     * stuffed top-k). Clamped to {@code [1, 100]}; invalid values fall back to
     * the default of 20.
     */
    public int getRagSnRerankTopN() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.RAG_SN_RERANK_TOP_N)
                .map(TurConfigVar::getValue)
                .map(v -> {
                    try {
                        int parsed = Integer.parseInt(v.trim());
                        return Math.max(1, Math.min(100, parsed));
                    } catch (NumberFormatException e) {
                        return 20;
                    }
                })
                .orElse(20);
    }

    public int updateRagSnRerankTopN(int topN) {
        int value = Math.max(1, Math.min(100, topN));
        TurConfigVar configVar = turConfigVarRepository
                .findById(TurConfigVarOnStartup.RAG_SN_RERANK_TOP_N)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.RAG_SN_RERANK_TOP_N);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(String.valueOf(value));
        turConfigVarRepository.save(configVar);
        return value;
    }

    /**
     * T337 — which reranker backend runs. Unknown/blank values fall back to
     * {@link TurRagRerankStrategyType#LLM} (legacy default), so a corrupt row
     * can never break reranking.
     */
    public TurRagRerankStrategyType getRagSnRerankStrategy() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.RAG_SN_RERANK_STRATEGY)
                .map(TurConfigVar::getValue)
                .map(TurRagRerankStrategyType::fromValue)
                .orElse(TurRagRerankStrategyType.LLM);
    }

    public TurRagRerankStrategyType updateRagSnRerankStrategy(TurRagRerankStrategyType strategy) {
        TurRagRerankStrategyType value = strategy == null ? TurRagRerankStrategyType.LLM : strategy;
        TurConfigVar configVar = turConfigVarRepository
                .findById(TurConfigVarOnStartup.RAG_SN_RERANK_STRATEGY)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.RAG_SN_RERANK_STRATEGY);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value.name());
        turConfigVarRepository.save(configVar);
        return value;
    }

    /** T338 — self-hosted cross-encoder {@code /rerank} endpoint URL. */
    public String getRagSnRerankEndpoint() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.RAG_SN_RERANK_ENDPOINT)
                .map(TurConfigVar::getValue)
                .orElse("");
    }

    public String updateRagSnRerankEndpoint(String endpoint) {
        String value = endpoint == null ? "" : endpoint.trim();
        TurConfigVar configVar = turConfigVarRepository
                .findById(TurConfigVarOnStartup.RAG_SN_RERANK_ENDPOINT)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.RAG_SN_RERANK_ENDPOINT);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
        return value;
    }

    /** T338 — rerank model name (cross-encoder / Cohere); blank = backend default. */
    public String getRagSnRerankModel() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.RAG_SN_RERANK_MODEL)
                .map(TurConfigVar::getValue)
                .orElse("");
    }

    public String updateRagSnRerankModel(String model) {
        String value = model == null ? "" : model.trim();
        TurConfigVar configVar = turConfigVarRepository
                .findById(TurConfigVarOnStartup.RAG_SN_RERANK_MODEL)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.RAG_SN_RERANK_MODEL);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
        return value;
    }

    /**
     * T339 — the decrypted Cohere API key, or {@code ""} when unset. Stored
     * encrypted at rest via {@link TurSecretCryptoService}; never echoed back to
     * the client (the API exposes only a "configured" boolean).
     */
    public String getRagSnRerankApiKey() {
        String stored = turConfigVarRepository.findById(TurConfigVarOnStartup.RAG_SN_RERANK_API_KEY)
                .map(TurConfigVar::getValue)
                .orElse("");
        if (stored.isBlank()) {
            return "";
        }
        String decrypted = turSecretCryptoService.decrypt(stored);
        return decrypted == null ? "" : decrypted;
    }

    /** T339 — whether a Cohere API key is configured (for the write-only UI status). */
    public boolean isRagSnRerankApiKeySet() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.RAG_SN_RERANK_API_KEY)
                .map(TurConfigVar::getValue)
                .filter(v -> !v.isBlank())
                .isPresent();
    }

    /**
     * T339 — sets the Cohere API key (encrypted at rest). A {@code null}/blank
     * value clears it. Returns the resulting "configured" status, never the key.
     */
    public boolean updateRagSnRerankApiKey(String apiKey) {
        TurConfigVar configVar = turConfigVarRepository
                .findById(TurConfigVarOnStartup.RAG_SN_RERANK_API_KEY)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.RAG_SN_RERANK_API_KEY);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        if (apiKey == null || apiKey.isBlank()) {
            configVar.setValue("");
            turConfigVarRepository.save(configVar);
            return false;
        }
        configVar.setValue(turSecretCryptoService.encrypt(apiKey.trim()));
        turConfigVarRepository.save(configVar);
        return true;
    }

    /** T331 — whether grounded follow-up questions are generated for SN answers. */
    public boolean isRagSnFollowupsEnabled() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.RAG_SN_FOLLOWUPS_ENABLED)
                .map(TurConfigVar::getValue)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    public boolean updateRagSnFollowupsEnabled(boolean enabled) {
        TurConfigVar configVar = turConfigVarRepository
                .findById(TurConfigVarOnStartup.RAG_SN_FOLLOWUPS_ENABLED)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.RAG_SN_FOLLOWUPS_ENABLED);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(String.valueOf(enabled));
        turConfigVarRepository.save(configVar);
        return enabled;
    }

    /** T330 — whether the post-generation groundedness audit runs on SN answers. */
    public boolean isRagSnGroundednessCheckEnabled() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.RAG_SN_GROUNDEDNESS_CHECK_ENABLED)
                .map(TurConfigVar::getValue)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    public boolean updateRagSnGroundednessCheckEnabled(boolean enabled) {
        TurConfigVar configVar = turConfigVarRepository
                .findById(TurConfigVarOnStartup.RAG_SN_GROUNDEDNESS_CHECK_ENABLED)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.RAG_SN_GROUNDEDNESS_CHECK_ENABLED);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(String.valueOf(enabled));
        turConfigVarRepository.save(configVar);
        return enabled;
    }

    public String getDefaultEmbeddingModelId() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_EMBEDDING_MODEL_ID)
                .map(TurConfigVar::getValue)
                .orElse("");
    }

    public String updateDefaultEmbeddingModelId(String id) {
        String value = id == null ? "" : id.trim();
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_EMBEDDING_MODEL_ID)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.DEFAULT_EMBEDDING_MODEL_ID);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
        return value;
    }

    public String getDefaultEmbeddingStoreId() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_EMBEDDING_STORE_ID)
                .map(TurConfigVar::getValue)
                .orElse("");
    }

    public String updateDefaultEmbeddingStoreId(String id) {
        String value = id == null ? "" : id.trim();
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_EMBEDDING_STORE_ID)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.DEFAULT_EMBEDDING_STORE_ID);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
        return value;
    }

    public String getDefaultAiAgentId() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_AI_AGENT_ID)
                .map(TurConfigVar::getValue)
                .orElse("");
    }

    public String updateDefaultAiAgentId(String id) {
        String value = id == null ? "" : id.trim();
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_AI_AGENT_ID)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.DEFAULT_AI_AGENT_ID);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
        return value;
    }
}
