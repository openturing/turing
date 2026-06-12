package com.viglet.turing.api.system;

import com.viglet.turing.genai.rag.rerank.TurRagRerankStrategyType;
import com.viglet.turing.genai.tool.TurCodeInterpreterExecutionMode;
import com.viglet.turing.system.TurEmailProviderType;
import com.viglet.turing.system.TurGlobalDecimalSeparator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurGlobalSettingsBean {
    private TurGlobalDecimalSeparator decimalSeparator;
    private String pythonExecutable;
    /**
     * Optional {@code requirements.txt}-style text declaring Python
     * packages the Code Interpreter sandbox should auto-install before
     * every Custom Tool that calls {@code code.executePython(...)}. One
     * package per line, optionally version-pinned. Blank = no
     * auto-install (operator manages the env manually).
     */
    private String pythonRequirements;
    /**
     * T80 — Code Interpreter execution mode: {@code NATIVE} (host
     * subprocess, legacy default) or {@code DOCKER} (each execution runs
     * in a throwaway hardened container). Operator choice exposed in the
     * Global Settings UI.
     *
     * @since 2026.3.1
     */
    private TurCodeInterpreterExecutionMode codeInterpreterExecutionMode;
    /**
     * T80 — Docker image used when {@link #codeInterpreterExecutionMode}
     * is {@code DOCKER}. Blank → server default ({@code python:3.12-slim}).
     *
     * @since 2026.3.1
     */
    private String codeInterpreterDockerImage;
    /**
     * T321 — Docker image for the Anthropic-compatible skill sandbox session
     * ({@code bash}-driven, skill folder mounted). Should bundle python + node
     * + bash. Blank → server default ({@code python:3.12-slim}). Used only in
     * {@code DOCKER} mode.
     *
     * @since 2026.3.1
     */
    private String codeInterpreterSkillImage;
    private String defaultLlmId;
    private boolean llmCacheEnabled;
    private long llmCacheTtlMs;
    private boolean llmCacheRegenerate;
    private TurEmailProviderType emailProvider;
    private String emailApiKey;
    private String senderEmail;
    private String senderName;
    private String recipientEmail;
    private boolean ragEnabled;
    private String defaultEmbeddingModelId;
    private String defaultEmbeddingStoreId;
    private String defaultAiAgentId;
    /**
     * T61 — retention TTL in hours for {@code pii_*} prefixed slots.
     * Hourly cleanup job wipes PII slot values older than this. {@code 0}
     * disables enforcement (slots are still encrypted at rest and
     * redacted in logs). Clamped server-side to {@code [0, 8760]}.
     *
     * @since 2026.3.1
     */
    private int piiSlotTtlHours;
    /** T328 — whether the SN RAG reranker runs (default off). */
    private boolean ragSnRerankEnabled;
    /** T328 — chunks kept after the reranker narrows the candidate pool. */
    private int ragSnRerankTopN;
    /**
     * T337 — reranker backend: {@code LLM} (legacy default) | {@code
     * CROSS_ENCODER} | {@code COHERE}.
     *
     * @since 2026.3.1
     */
    private TurRagRerankStrategyType ragSnRerankStrategy;
    /** T338 — self-hosted cross-encoder {@code /rerank} endpoint URL. */
    private String ragSnRerankEndpoint;
    /** T338 — rerank model name (cross-encoder / Cohere); blank = backend default. */
    private String ragSnRerankModel;
    /**
     * T339 — Cohere API key, <b>write-only</b>: accepted on update, never echoed
     * back on read (always blank in GET responses). A blank value on update
     * leaves the stored key unchanged.
     *
     * @since 2026.3.1
     */
    private String ragSnRerankApiKey;
    /** T339 — read-only status: whether a Cohere API key is configured. */
    private boolean ragSnRerankApiKeySet;
}
