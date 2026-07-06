package com.viglet.turing.genai.provider.llm;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import reactor.core.publisher.Flux;

/**
 * Wraps a {@link ChatModel} so that a request rejected for sending a sampling
 * parameter ({@code temperature} / {@code top_p} / {@code top_k}) is transparently
 * retried against an equivalent model built <b>without</b> those parameters.
 *
 * <p>Current-generation reasoning models remove sampling parameters and return
 * <b>HTTP 400</b> when they're sent — Anthropic Claude Opus 4.7/4.8, Sonnet 5,
 * Fable 5 (<i>"`temperature` is deprecated for this model."</i>) and the OpenAI
 * o-series behave this way. A stored LLM instance still carries a temperature
 * (Turing defaults it), so selecting such a model would otherwise fail every
 * call. Rather than maintain a per-model capability table that goes stale as
 * new models ship, this fallback detects the rejection at runtime and retries
 * once without the offending parameters — future models get the same treatment
 * for free.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurSamplingParamFallbackChatModel implements ChatModel {

    private final ChatModel primary;
    private final ChatModel withoutSampling;

    public TurSamplingParamFallbackChatModel(ChatModel primary, ChatModel withoutSampling) {
        this.primary = primary;
        this.withoutSampling = withoutSampling;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        try {
            return primary.call(prompt);
        } catch (RuntimeException e) {
            if (isSamplingParamRejection(e)) {
                return withoutSampling.call(prompt);
            }
            throw e;
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return primary.stream(prompt)
                .onErrorResume(e -> isSamplingParamRejection(e)
                        ? withoutSampling.stream(prompt)
                        : Flux.error(e));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return primary.getDefaultOptions();
    }

    /**
     * True when the throwable (or any cause) is a bad-request that names a
     * sampling parameter as deprecated/unsupported — e.g. Anthropic's
     * {@code "`temperature` is deprecated for this model."} or OpenAI's
     * {@code "Unsupported value: 'temperature' ..."}.
     */
    static boolean isSamplingParamRejection(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message == null) {
                continue;
            }
            String lower = message.toLowerCase(java.util.Locale.ROOT);
            boolean namesSamplingParam = lower.contains("temperature")
                    || lower.contains("top_p") || lower.contains("topp")
                    || lower.contains("top_k") || lower.contains("topk");
            boolean rejected = lower.contains("deprecated")
                    || lower.contains("not supported") || lower.contains("unsupported")
                    || lower.contains("not allowed") || lower.contains("does not support");
            if (namesSamplingParam && rejected) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
