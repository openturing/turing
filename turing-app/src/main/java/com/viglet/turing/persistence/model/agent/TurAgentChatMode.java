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
 *   <li>{@link #AUTO} — stream when the turn can be streamed without losing
 *       anything, call otherwise. "Without losing anything" means the turn has
 *       no flow governing it <em>and</em> no tool callbacks in play, so the two
 *       things {@link #STREAM} silently gives up are not on the table: there is
 *       no tool to drop and no bubble to rewrite. Decided per turn, not per
 *       agent, because whether a turn carries tools is a per-turn fact — the
 *       public SN chat surface strips every callback for an untrusted caller
 *       (T647), so its turns are tool-free even on an agent that has tools.</li>
 * </ul>
 *
 * <h2>Why AUTO exists (T1004)</h2>
 *
 * <p>Site-scoped chat emitted its whole answer as one SSE {@code content} event,
 * so a consumer reading the body with a stream reader received exactly one token
 * and could render nothing until the answer was complete. The cause was not a
 * missing capability — {@link #STREAM} already works — but this enum's default:
 * an agent is {@link #CALL} unless an operator changes it, and changing it to
 * {@link #STREAM} would trade the tools away on every other surface that agent
 * serves. {@code AUTO} is the mode that can be the default for a public site
 * chat, because on the turn that matters there is nothing to trade.</p>
 *
 * <h2>OpenAI streaming transport (T18, §IV.1.b)</h2>
 *
 * <p>Spring AI 2.0.0-M6's SDK-based {@code OpenAiChatModel.stream(prompt)}
 * buffers the entire upstream delta stream before publishing the first
 * {@code onNext} — root cause: M6 migrated from the WebClient-based
 * {@code OpenAiApi} to the official OpenAI Java SDK ({@code OpenAIOkHttpClientAsync}),
 * whose {@code StreamResponse<ChatCompletionChunk>} is iterator-based and
 * wrapped via {@code Flux.fromStream(...)}, which drains the iterator
 * before publishing.
 *
 * <p>To restore real token-by-token streaming on the OpenAI provider,
 * {@link com.viglet.turing.genai.provider.llm.TurOpenAiLlmProvider#createStreamingChatModel}
 * returns a hand-rolled {@link com.viglet.turing.genai.provider.llm.TurOpenAiStreamingChatModel}
 * that POSTs directly to {@code /v1/chat/completions} with {@code stream:true}
 * via {@link org.springframework.web.reactive.function.client.WebClient}
 * and emits one Spring AI {@code ChatResponse} per SSE {@code delta.content}
 * line. The {@link com.viglet.turing.genai.TurAgentChatExecutor} STREAM
 * branch calls {@code llmModelFactory.createStreamingChatModel(...)} so the
 * SSE pipeline receives chunks as they arrive (validated empirically by
 * {@code TurOpenAiStreamingChatModelBehavioralIT}: 100-300 chunks per turn
 * vs. ~1 chunk on the SDK path).
 *
 * <p>Other providers (Ollama, Anthropic, Gemini) inherit the default
 * {@link com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider#createStreamingChatModel}
 * implementation that just returns the same model {@code createChatModel}
 * would — their Spring AI 2.0.0-M6 adapters are already Reactor-friendly.
 *
 * <p>Tracked in {@code docs/IMPROVEMENTS.md} (task T18 / §IV.1.b).
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public enum TurAgentChatMode {
    CALL,
    STREAM,
    AUTO
}
