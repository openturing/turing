package com.viglet.turing.genai.provider.llm;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import reactor.core.publisher.Flux;

/**
 * Wraps a {@link ChatModel} so that a request rejected for sending a sampling
 * parameter ({@code temperature} / {@code top_p} / {@code top_k}) is transparently
 * retried against an equivalent model built <b>without</b> those parameters.
 *
 * <p>Current-generation reasoning models remove sampling parameters and return
 * <b>HTTP 400</b> when they're sent — Anthropic Claude Opus 4.7/4.8, Sonnet 5,
 * Fable 5 (<i>"`temperature` is deprecated for this model."</i>) and the OpenAI
 * o-series behave this way. Some models also reject sending <em>both</em>
 * {@code temperature} and {@code top_p} at once
 * (<i>"`temperature` and `top_p` cannot both be specified for this model.
 * Please use only one."</i>). A stored LLM instance still carries a temperature
 * (Turing defaults it) and may also carry a topP, so selecting such a model
 * would otherwise fail every call. Rather than maintain a per-model capability
 * table that goes stale as new models ship, this fallback detects the rejection
 * at runtime and retries once without the offending parameters — future models
 * get the same treatment for free.
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
                return withoutSampling.call(withoutSamplingParams(prompt));
            }
            throw e;
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return primary.stream(prompt)
                .onErrorResume(e -> isSamplingParamRejection(e)
                        ? withoutSampling.stream(withoutSamplingParams(prompt))
                        : Flux.error(e));
    }

    /**
     * Returns a copy of {@code prompt} with the sampling parameters
     * ({@code temperature} / {@code top_p} / {@code top_k}) cleared, preserving
     * every other runtime option (model, max tokens, tool callbacks, tool
     * context, …).
     *
     * <p>The {@link #withoutSampling} model only omits sampling params from its
     * <em>default</em> options. But the agent/tool path attaches explicit
     * per-turn {@link ToolCallingChatOptions} to the prompt — seeded from the
     * <em>primary</em> model via {@code TurChatToolOptions.builderFrom(...)}, so
     * they carry the instance's temperature and topP. Spring AI merges those
     * explicit runtime options <em>over</em> the model defaults, so simply
     * swapping models leaves the offending params on the wire and the retry
     * fails identically. Clearing them on the prompt itself is what actually
     * drops them. When the prompt carries no {@link ToolCallingChatOptions}
     * (e.g. a bare {@code call(text)}), it is returned untouched and the
     * {@link #withoutSampling} model's sampling-free defaults take over.
     */
    private static Prompt withoutSamplingParams(Prompt prompt) {
        if (prompt.getOptions() instanceof ToolCallingChatOptions options) {
            ChatOptions stripped = options.mutate()
                    .temperature(null)
                    .topP(null)
                    .topK(null)
                    .build();
            return new Prompt(prompt.getInstructions(), stripped);
        }
        return prompt;
    }

    @Override
    public ChatOptions getOptions() {
        // Spring AI 2.0.0-RC1 deprecated getDefaultOptions() in favour of
        // getOptions(); delegate through the non-deprecated method so the
        // primary model's provider-specific options (e.g. OpenAiChatOptions)
        // propagate to callers such as TurChatToolOptions.builderFrom(...).
        // Overriding only getDefaultOptions() here let the generic default
        // getOptions() leak through, which made builderFrom fall back to a
        // DefaultToolCallingChatOptions and blow up with a ClassCastException
        // in OpenAiChatModel.createRequest.
        return primary.getOptions();
    }

    /**
     * True when the throwable (or any cause) is a bad-request that names a
     * sampling parameter and rejects it — either because the parameter is
     * deprecated/unsupported (e.g. Anthropic's
     * {@code "`temperature` is deprecated for this model."} or OpenAI's
     * {@code "Unsupported value: 'temperature' ..."}) or because sending
     * {@code temperature} and {@code top_p} together is not allowed (e.g.
     * {@code "`temperature` and `top_p` cannot both be specified for this
     * model. Please use only one."}). In every case, retrying without the
     * sampling parameters resolves it.
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
                    || lower.contains("not allowed") || lower.contains("does not support")
                    || lower.contains("cannot both be specified") || lower.contains("use only one");
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
