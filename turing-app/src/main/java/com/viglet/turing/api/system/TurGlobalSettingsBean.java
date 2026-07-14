package com.viglet.turing.api.system;

import com.viglet.turing.genai.rag.rerank.TurRagRerankStrategyType;
import com.viglet.turing.genai.tool.TurCodeInterpreterExecutionMode;
import com.viglet.turing.genai.transcription.TurTranscriptionProviderType;
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
    /**
     * T517 — cross-provider per-stage model "lanes". Each holds an LLM instance
     * id (blank = fall back to {@link #defaultLlmId}): {@code FAST} for
     * interactive micro-calls (autocomplete/spell/query-rewrite), {@code
     * REASONING} for router/rerank/judge, {@code CHEAP} for background work.
     *
     * @since 2026.3.4
     */
    private String modelLaneFastId;
    private String modelLaneReasoningId;
    private String modelLaneCheapId;
    /**
     * T518 — cost-aware cross-provider fallback chain (ordered LLM instance ids)
     * + routing mode (`PRIORITY` | `CHEAPEST`). Empty chain = no fallback (legacy
     * single-model path).
     *
     * @since 2026.3.4
     */
    private java.util.List<String> llmFallbackChainIds;
    private com.viglet.turing.resilience.llm.TurLlmFallbackMode llmFallbackMode;
    /**
     * T522 — multi-provider "second opinion": when enabled, a different-vendor
     * critic LLM judges the primary answer; the agreement is surfaced as a
     * confidence signal. {@code secondOpinionLlmId} is the critic instance.
     *
     * @since 2026.3.4
     */
    private boolean secondOpinionEnabled;
    private String secondOpinionLlmId;
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
    /** T338 — rerank model name (cross-encoder / Cohere / Bedrock ARN / Vertex model); blank = backend default. */
    private String ragSnRerankModel;
    /** T521 — AWS region for the managed Bedrock Rerank API ({@code BEDROCK}). */
    private String ragSnRerankRegion;
    /** T521 — GCP project for the managed Vertex AI Ranking API ({@code VERTEX_AI}). */
    private String ragSnRerankVertexProject;
    /** T521 — GCP location for Vertex AI Ranking (default {@code global}). */
    private String ragSnRerankVertexLocation;
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
    /**
     * T341 — whether reranker results are memoized in the {@code turRagRerankScore}
     * cache (default off). Skips repeat scoring of an identical query+candidate
     * pool; worth enabling only when profiling shows repeated identical rerank
     * calls in production.
     *
     * @since 2026.3.1
     */
    private boolean ragSnRerankCacheEnabled;
    /**
     * T687 — active speech-to-text backend: {@code OPENAI} (legacy default) |
     * {@code OPENAI_COMPATIBLE} | {@code NONE}.
     *
     * @since 2026.3.4
     */
    private TurTranscriptionProviderType transcriptionStrategy;
    /** T687 — dedicated OpenAI-compatible {@code /audio/transcriptions} endpoint (OPENAI_COMPATIBLE). */
    private String transcriptionEndpoint;
    /** T687 — transcription model name; blank uses the backend default (e.g. {@code whisper-1}). */
    private String transcriptionModel;
    /** T687 — per-request upload limit in bytes; audio above this is chunked (T688/T689). */
    private long transcriptionMaxUploadBytes;
    /**
     * T687 — transcription API key, <b>write-only</b>: accepted on update, never
     * echoed back on read (always blank in GET). Blank on update leaves the
     * stored key unchanged.
     *
     * @since 2026.3.4
     */
    private String transcriptionApiKey;
    /** T687 — read-only status: whether a transcription API key is configured. */
    private boolean transcriptionApiKeySet;
    /**
     * T739 — active URL content-fetch mode: {@code SIMPLE} (legacy default) |
     * {@code HEADLESS} | {@code AUTO}.
     *
     * @since 2026.3.4
     */
    private com.viglet.turing.genai.urlfetch.TurUrlFetchMode urlFetchMode;
    /** T739 — browserless sidecar base URL (e.g. {@code http://browserless:3000}); blank = no sidecar. */
    private String urlFetchBrowserlessUrl;
    /**
     * T739 — browserless token, <b>write-only</b>: accepted on update, never
     * echoed back on read (always blank in GET). Blank on update leaves the stored
     * token unchanged.
     *
     * @since 2026.3.4
     */
    private String urlFetchBrowserlessToken;
    /** T739 — read-only status: whether a browserless token is configured. */
    private boolean urlFetchBrowserlessTokenSet;
}
