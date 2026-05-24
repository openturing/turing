/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.testsupport.genai.executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.viglet.turing.resilience.llm.TurLlmModelFactory;

import reactor.core.publisher.Flux;

/**
 * Shared {@code @TestConfiguration} for Structural and Contract integration
 * tests of {@link com.viglet.turing.genai.TurAgentChatExecutor}. Replaces the
 * production {@link TurLlmModelFactory} with a {@code @Primary} mock factory
 * that returns a Mockito {@link ChatModel} controlled by {@link ChatModelHarness}.
 *
 * <h2>Why this class lives under {@code com.viglet.testsupport.*}</h2>
 *
 * {@code @TestConfiguration} (meta-annotated with {@code @TestComponent}) is
 * <b>auto-discovered</b> by every {@code @SpringBootTest} whose
 * {@code @SpringBootApplication} root scans this class's package. When this
 * class previously lived under {@code com.viglet.turing.genai.testsupport} —
 * inside the {@code com.viglet.turing} scan owned by {@code TuringES} — it
 * silently registered its {@code @Primary} mock factory in EVERY
 * {@code @SpringBootTest} context, including ITs that wanted the real
 * {@code TurLlmModelFactory} (e.g. {@code TurAgentChatLatencyIT}). The
 * result: empty chat responses with no exception, ~10ms LLM "round trips",
 * and hours of head-scratching.
 *
 * <p>Moving the class out of the {@code com.viglet.turing.*} subtree keeps
 * it OFF the production scan path so it can only enter a test context via
 * an explicit {@code @Import(TurAgentChatExecutorMockSupport.class)}. The
 * {@link Bean @Bean} contract is unchanged — tests that want the mock keep
 * working unchanged; tests that don't (now) get the real factory.
 *
 * <h2>What this enables</h2>
 *
 * <ul>
 *   <li><b>Determinism</b>: every test enqueues the exact assistant reply the
 *       executor will see — no real LLM round-trip, no token usage, no API key.
 *       Behavioral assertions (anchoring, persona drift, tool-strip) move to
 *       {@code TurAgentChatExecutorBehavioralIT} under the {@code -Pllm-it}
 *       profile (see {@code docs/IMPROVEMENTS.md} §VI.3 pairing matrix).</li>
 *   <li><b>Prompt capture</b>: every {@link ChatModel#call(Prompt)} invocation
 *       is recorded via an {@link ArgumentCaptor}, so assertions can inspect
 *       the SystemMessage text, message count, ChatOptions tool list, etc.
 *       The same {@code prompts} list also feeds shortcuts like {@code firstPrompt()}
 *       / {@code regenPrompt()} on {@link AbstractAgentExecutorIT}.</li>
 *   <li><b>Bypass the resilience wrapper</b>: substituting
 *       {@link TurLlmModelFactory} at {@code @Primary} level means the mock
 *       {@code ChatModel} is returned directly, skipping {@code TurResilientChatModel}.
 *       This preserves the invariant "1 executor call = 1 Mockito-verified call"
 *       without retry / circuit-breaker masking the call count.</li>
 *   <li><b>NoOp side-channels</b>: chat-analytics and chat-memory services are
 *       silently no-op'd so tests don't have to wire MongoDB/Redis or
 *       tolerate stray writes to the test DB.</li>
 * </ul>
 *
 * <h2>How to use</h2>
 *
 * <pre>{@code
 * @Import(TurAgentChatExecutorMockSupport.class)
 * class TurAgentChatExecutorRaceIT extends AbstractAgentExecutorIT {
 *
 *     @Test
 *     void persona_isResolvedAfterFlowAdvance() {
 *         harness().queueResponse("ok");
 *         // ... drive a turn that crosses a persona-* node ...
 *         Prompt p = firstPrompt();
 *         assertThat(systemTextOf(p)).contains("Lucas");
 *     }
 * }
 * }</pre>
 *
 * <p>Streaming is supported via {@link ChatModelHarness#queueStreamingResponse(String...)}:
 * each varargs element becomes one chunk of the {@code Flux<ChatResponse>} the
 * mock will emit. The executor's STREAM path
 * ({@link com.viglet.turing.persistence.model.agent.TurAgentChatMode#STREAM})
 * exercises this code path.
 *
 * <p><b>Tool-strip tests (patches #3, #4)</b> don't need to simulate the LLM
 * returning a tool call. Inspect the captured {@link Prompt}'s
 * {@link org.springframework.ai.model.tool.ToolCallingChatOptions} directly:
 *
 * <pre>{@code
 * var opts = (ToolCallingChatOptions) firstPrompt().getOptions();
 * assertThat(opts.getToolCallbacks())
 *         .as("Initial call must run tool-free when node forbids tools")
 *         .isEmpty();
 * }</pre>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@TestConfiguration
public class TurAgentChatExecutorMockSupport {

    @Bean
    public ChatModelHarness chatModelHarness() {
        return new ChatModelHarness();
    }

    /**
     * Marked {@code @Primary} so Spring resolves it ahead of the production
     * {@link com.viglet.turing.resilience.llm.TurLlmModelFactory @Component}.
     * The downstream {@code createChatModel(...)} always returns the same
     * harness-backed mock — tests rely on that to enqueue / inspect calls.
     */
    @Bean
    @Primary
    public TurLlmModelFactory mockLlmModelFactory(ChatModelHarness harness) {
        // Re-use the same Mockito instance across createChatModel invocations
        // so a test can stage responses BEFORE the executor obtains the model
        // (the production factory is called from within execute()).
        return new TurLlmModelFactory(null, null, null, null) {
            @Override
            public org.springframework.ai.chat.model.ChatModel createChatModel(
                    com.viglet.turing.persistence.model.llm.TurLLMInstance llmInstance,
                    String decryptedApiKey) {
                return harness.chatModel();
            }

            @Override
            public org.springframework.ai.embedding.EmbeddingModel createEmbeddingModel(
                    com.viglet.turing.persistence.model.llm.TurLLMInstance llmInstance,
                    String decryptedApiKey) {
                // Defensive: the @Primary bean WILL be picked up by every
                // EmbeddingModel consumer in the context. RAG ITs shouldn't
                // rely on this — they should import their own mock support
                // tailored to embeddings. Fail loudly if reached so the
                // misuse is obvious.
                throw new UnsupportedOperationException(
                        "TurAgentChatExecutorMockSupport does not provide an EmbeddingModel "
                                + "— wire a dedicated mock support if your test needs RAG");
            }
        };
    }

    /**
     * Controllable Mockito wrapper around {@link ChatModel} used by every
     * Structural / Contract IT. One instance is created per Spring context
     * and reused across tests in the same class; {@link #reset()} must be
     * called from {@code @BeforeEach} to isolate test methods.
     *
     * <p>Threading: every public method is intended to be called from the
     * test thread; queued responses are popped FIFO. The mock itself is
     * thread-safe (Mockito default).
     */
    public static class ChatModelHarness {

        /** FIFO of canned blocking responses for {@code ChatModel.call(prompt)}. */
        private final Deque<ChatResponse> blockingQueue = new ArrayDeque<>();
        /** FIFO of canned streaming responses; each list is the chunks for one stream call. */
        private final Deque<List<ChatResponse>> streamingQueue = new ArrayDeque<>();
        /** Every {@code call(prompt)} / {@code stream(prompt)} invocation in order. */
        private final List<Prompt> capturedPrompts = Collections.synchronizedList(new ArrayList<>());
        /** Counters for cheap call-count assertions. */
        private final AtomicInteger callInvocations = new AtomicInteger();
        private final AtomicInteger streamInvocations = new AtomicInteger();
        /** The single Mockito mock; recreated by {@link #reset()}. */
        private ChatModel chatModel;

        ChatModelHarness() {
            buildFreshMock();
        }

        /**
         * Discards every queued response and resets capture state. Call from
         * a JUnit {@code @BeforeEach} to isolate test methods sharing the
         * context.
         */
        public void reset() {
            blockingQueue.clear();
            streamingQueue.clear();
            capturedPrompts.clear();
            callInvocations.set(0);
            streamInvocations.set(0);
            buildFreshMock();
        }

        /**
         * Enqueue a plain-text assistant reply that the next
         * {@code ChatModel.call(prompt)} will return. Multiple calls stack
         * FIFO — turn 1 pops the first, turn 2 the second, etc.
         */
        public void queueResponse(String assistantText) {
            blockingQueue.add(buildBlockingResponse(assistantText));
        }

        /**
         * Enqueue a streaming sequence — every varargs entry becomes one
         * {@code onNext} on the {@code Flux<ChatResponse>} the mock returns
         * from {@link ChatModel#stream(Prompt)}. Empty strings are allowed
         * and will exercise the executor's empty-chunk filter.
         */
        public void queueStreamingResponse(String... chunks) {
            List<ChatResponse> events = new ArrayList<>(chunks.length);
            for (String chunk : chunks) {
                events.add(buildBlockingResponse(chunk));
            }
            streamingQueue.add(events);
        }

        /** Captured prompts in the order the executor passed them to the mock. */
        public List<Prompt> capturedPrompts() {
            return List.copyOf(capturedPrompts);
        }

        /** Convenience: {@code call(...)} count alone, ignoring streaming. */
        public int callCount() {
            return callInvocations.get();
        }

        /** Convenience: {@code stream(...)} count alone. */
        public int streamCount() {
            return streamInvocations.get();
        }

        /**
         * The underlying Mockito mock. Intentionally exposed so tests can
         * stack their own verifications (e.g. {@code verify(harness.chatModel())
         * .call(any())}) that go beyond the queued-response abstraction.
         */
        public ChatModel chatModel() {
            return chatModel;
        }

        /**
         * Returns a Mockito {@link ArgumentCaptor} primed for
         * {@code call(Prompt)} on the underlying mock. Use this when the
         * order doesn't matter and you want Mockito to do the bookkeeping
         * for you (the {@link #capturedPrompts()} list already covers most
         * cases at lower ceremony).
         */
        public ArgumentCaptor<Prompt> promptCaptor() {
            return ArgumentCaptor.forClass(Prompt.class);
        }

        // ─────────────────────────── internals ───────────────────────────

        private void buildFreshMock() {
            chatModel = mock(ChatModel.class);
            when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
                Prompt prompt = invocation.getArgument(0);
                capturedPrompts.add(prompt);
                callInvocations.incrementAndGet();
                ChatResponse next = blockingQueue.poll();
                if (next != null) {
                    return next;
                }
                // No response queued — default to empty so the executor's
                // null/empty-text guards still exercise instead of NPEing.
                return buildBlockingResponse("");
            });
            when(chatModel.stream(any(Prompt.class))).thenAnswer(invocation -> {
                Prompt prompt = invocation.getArgument(0);
                capturedPrompts.add(prompt);
                streamInvocations.incrementAndGet();
                List<ChatResponse> events = streamingQueue.poll();
                if (events != null) {
                    return Flux.fromIterable(events);
                }
                return Flux.<ChatResponse>empty();
            });
        }

        private static ChatResponse buildBlockingResponse(String text) {
            AssistantMessage msg = new AssistantMessage(text == null ? "" : text);
            return new ChatResponse(List.of(new Generation(msg)),
                    ChatResponseMetadata.builder().build());
        }
    }
}
