package com.viglet.turing.persistence.model.agent;

/**
 * Per-agent toggle for how {@link com.viglet.turing.genai.TurAgentChatExecutor}
 * issues the LLM round-trip:
 *
 * <ul>
 *   <li>{@link #CALL} — blocking {@code chatModel.call(prompt)}. The assistant
 *       text arrives as a single SSE {@code "token"} event after the LLM
 *       finishes. Required for flow-driven chats today because the post-LLM
 *       advance/regen pipeline needs the full text before deciding whether to
 *       rewrite the bubble.</li>
 *   <li>{@link #STREAM} — reactive {@code chatModel.stream(prompt)}. Each
 *       token chunk is intended to flow through the SSE pipeline so the
 *       bubble fills in token-by-token. Effective on the non-flow path only
 *       (flow-driven turns silently fall back to {@code CALL} so regen still
 *       works) AND incurs the trade-off that the per-turn tool callbacks
 *       (MCP / native / custom) are NOT honored on this path.</li>
 * </ul>
 *
 * <h2>Known limitation in Spring AI 2.0.0-M6 (OpenAI provider)</h2>
 *
 * <p>The {@code chatModel.stream(prompt)} returned by
 * {@link org.springframework.ai.openai.OpenAiChatModel} in Spring AI
 * 2.0.0-M6 buffers the entire upstream delta stream before publishing the
 * first {@code onNext}. Empirically (see {@code TurAgentChatLatencyIT}
 * stream variant): {@code first_raw_ms ≈ llm_ms} — all 500+ deltas drain
 * in the last ~150 ms of the round-trip, defeating token-by-token UX. The
 * agent-chat pipeline ({@link com.viglet.turing.genai.TurAgentChatExecutor})
 * cannot fix this — the buffering happens inside the Spring AI client,
 * above our reactive operators.
 *
 * <p>Root cause: M6 migrated from the WebClient-based {@code OpenAiApi} to
 * the official OpenAI Java SDK ({@code com.openai.client.okhttp.OpenAIOkHttpClientAsync}),
 * whose {@code StreamResponse<ChatCompletionChunk>} is iterator-based on top
 * of OkHttp's body buffering. Spring AI wraps it via
 * {@code Flux.fromStream(...)} which drains the iterator before publishing.
 *
 * <p>What this means for users today: setting an agent to {@code STREAM} on
 * an OpenAI-backed LLM persists the choice but UX-wise behaves identical to
 * {@code CALL} — chunks arrive in one final burst. The setting is kept for:
 *
 * <ol>
 *   <li>Forward-compatibility with future Spring AI versions that fix the
 *       transport (or with the planned IV.1 WebClient-based bypass for
 *       OpenAI in {@link com.viglet.turing.genai.provider.llm.TurOpenAiLlmProvider}).</li>
 *   <li>Providers whose Spring AI streaming implementation does NOT exhibit
 *       this buffering — Ollama and Anthropic have historically been more
 *       Reactor-friendly; behavior should be verified per-provider before
 *       relying on it.</li>
 * </ol>
 *
 * <p>Tracked in {@code docs/IMPROVEMENTS.md} (task IV.1).
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public enum TurAgentChatMode {
    CALL,
    STREAM
}
