package com.viglet.turing.genai.provider.llm;

import java.util.List;
import java.util.OptionalInt;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;

public interface TurGenAiLlmProvider {

    String getPluginType();

    ChatModel createChatModel(TurLLMInstance turLLMInstance, String decryptedApiKey);

    EmbeddingModel createEmbeddingModel(TurLLMInstance turLLMInstance, String decryptedApiKey);

    /**
     * Returns a {@link ChatModel} optimized for token-by-token SSE
     * streaming. Providers that can stream correctly through their
     * primary {@code ChatModel} (Anthropic, Ollama, Gemini-native)
     * inherit the default — it simply returns the same model
     * {@link #createChatModel(TurLLMInstance, String)} would. Providers
     * with buffering issues in the primary transport (notably OpenAI on
     * Spring AI 2.0.0-M6, where the SDK adapter drains the delta
     * iterator before publishing) override this to return a hand-rolled
     * streaming model — see {@code TurOpenAiStreamingChatModel}.
     *
     * <p><b>Contract:</b> the returned model's {@code stream(Prompt)}
     * MUST publish each provider-emitted delta as a separate
     * {@link reactor.core.publisher.Flux Flux} signal — not batch them
     * up at the end. Callers (the {@code TurAgentChatExecutor} STREAM
     * branch) are allowed to assume token-by-token delivery on this
     * path; if a provider can't make that guarantee, it should keep the
     * default and the agent operator should leave {@code chatMode=CALL}.
     *
     * <p><b>Tools:</b> streaming models may drop tool-calling support
     * to preserve the SSE shape (Spring AI's tool-aware aggregator
     * buffers internally). Document this on the implementation; the
     * STREAM mode docs on {@code TurAgentChatMode} already do.
     *
     * @since 2026.2.7
     */
    default ChatModel createStreamingChatModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        return createChatModel(turLLMInstance, decryptedApiKey);
    }

    /**
     * Fetches the actual context window size from the provider's API.
     * Returns empty if the provider doesn't support runtime discovery,
     * in which case the stored contextWindow value from the instance is used.
     */
    default OptionalInt fetchContextWindow(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        return OptionalInt.empty();
    }

    /**
     * Lists the models the vendor exposes for this instance, for the
     * admin model-picker (T577). Providers that can query the vendor API
     * (OpenAI-family, Anthropic, Gemini, Ollama) override this and return
     * a live list; the rest inherit the empty default and the discovery
     * service falls back to the bundled static catalog.
     *
     * <p><b>Contract:</b> implementations MUST be resilient — a missing
     * API key, network failure, or unexpected response shape returns an
     * empty list (never throws), so the caller can degrade to the static
     * catalog. {@code decryptedApiKey} may be {@code null}/blank when the
     * instance hasn't been given a key yet.
     *
     * @since 2026.3.4
     */
    default List<TurLlmModelOption> listModels(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        return List.of();
    }
}
