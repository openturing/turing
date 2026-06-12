/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */

package com.viglet.turing.genai;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.genai.persona.TurPersonaToneValidator;
import com.viglet.turing.genai.persona.TurPersonaValidationResult;
import com.viglet.turing.genai.rag.TurRagSource;
import com.viglet.turing.genai.rag.TurRagSourceCollector;
import com.viglet.turing.observability.TurChatPipelineObservation;
import com.viglet.turing.observability.TurMeterNames;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAgentChatMode;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.service.chatanalytics.TurChatAnalyticsService;
import com.viglet.turing.service.chatmemory.TurChatMemoryService;
import com.viglet.turing.service.llm.tokenusage.TurLLMTokenUsageService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Owns the post-setup dispatch of the per-turn chat pipeline:
 * <ul>
 *   <li>CALL vs STREAM branch selection (STREAM only when there is no
 *       chat flow governing this turn — flow-driven turns need the full
 *       reply text before suggested options can be derived).</li>
 *   <li>Token-usage recording, persona tone validation, telemetry, and
 *       chat-memory append (CALL branch).</li>
 *   <li>SSE event fan-out (text + optional {@code "options"} chip event).</li>
 * </ul>
 *
 * <p>Extracted from {@link TurAgentChatExecutor} in T33. Owns the
 * {@link TurMeterNames#STAGE_CHAT_LLM_CALL},
 * {@link TurMeterNames#STAGE_CHAT_POST},
 * {@link TurMeterNames#STAGE_CHAT_TOTAL},
 * {@link TurMeterNames#STAGE_CHAT_ADVANCE} and
 * {@link TurMeterNames#STAGE_CHAT_REGEN} timings. The latter two are
 * preserved (always 0) post-T16 for backwards-compatible dashboards.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurChatStreamingDispatcher {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TurLlmModelFactory llmModelFactory;
    private final TurLLMTokenUsageService tokenUsageService;
    private final TurPersonaToneValidator personaToneValidator;
    private final TurChatAnalyticsService chatAnalyticsService;
    private final TurChatMemoryService chatMemoryService;
    private final TurChatPipelineObservation chatPipelineObservation;
    private final TurToolExecutionLoop toolExecutionLoop;

    public TurChatStreamingDispatcher(TurLlmModelFactory llmModelFactory,
            TurLLMTokenUsageService tokenUsageService,
            TurPersonaToneValidator personaToneValidator,
            TurChatAnalyticsService chatAnalyticsService,
            TurChatMemoryService chatMemoryService,
            TurChatPipelineObservation chatPipelineObservation,
            TurToolExecutionLoop toolExecutionLoop) {
        this.llmModelFactory = llmModelFactory;
        this.tokenUsageService = tokenUsageService;
        this.personaToneValidator = personaToneValidator;
        this.chatAnalyticsService = chatAnalyticsService;
        this.chatMemoryService = chatMemoryService;
        this.chatPipelineObservation = chatPipelineObservation;
        this.toolExecutionLoop = toolExecutionLoop;
    }

    /**
     * Dispatch the prepared chat turn to the chat model.
     *
     * @param agent the chatting agent
     * @param llmInstance the underlying LLM (drives token usage + streaming model swap on STREAM)
     * @param decryptedApiKey the runtime-decrypted API key — only used to build the
     *                        provider's streaming-optimized {@code ChatModel} on the STREAM branch
     * @param chatModel the CALL-branch {@code ChatModel} (already wrapped in resilience)
     * @param messages the assembled {@code List<Message>} (system + history) — needed for
     *                 the STREAM branch which rebuilds the prompt with no ChatOptions
     * @param initialOptions options for the CALL branch — already tool-stripped when the
     *                       active node forbids tools (T11 declarative check)
     * @param flowContext nullable — null disables flow-aware behaviors (suggested options,
     *                    STREAM gating)
     * @param effectivePersona the post-walk persona (single resolution per §I.5 step 2)
     * @param conversationId nullable — null means stateless turn
     * @param lastUserMessage the latest user message (telemetry)
     * @param username resolved from the {@code SecurityContext} (token usage attribution)
     * @param tStart turn-start millis (for total-time accounting)
     * @param tSetupEnd setup-phase end millis (for post-time accounting)
     */
    public Flux<ChatResponse> dispatch(TurAIAgent agent,
            TurLLMInstance llmInstance,
            String decryptedApiKey,
            ChatModel chatModel,
            List<Message> messages,
            ChatOptions initialOptions,
            TurAgentChatFlowContext flowContext,
            TurPersona effectivePersona,
            String conversationId,
            String lastUserMessage,
            String username,
            long tStart,
            long tSetupEnd) {

        // STREAM mode: forward each token chunk to the SSE pipeline as it
        // arrives so the bubble fills in token-by-token. We only take this
        // branch when there is NO flow context — flow-driven turns need
        // the full reply text before suggested options can be emitted, and
        // there is no clean way to "un-emit" a token already sent.
        //
        // Tone validation is also skipped on this path: TurPersonaToneValidator
        // returns a sanitizedText that may differ from what was streamed —
        // we can't retro-sanitize tokens already on the wire. Agents that
        // rely on persona forbidden-term enforcement should stay on CALL.
        boolean useStreaming = agent.getChatMode() == TurAgentChatMode.STREAM
                && flowContext == null;
        if (useStreaming) {
            return dispatchStream(agent, llmInstance, decryptedApiKey, messages,
                    conversationId, lastUserMessage, username, tStart, tSetupEnd);
        }
        return dispatchCall(agent, llmInstance, chatModel, messages, initialOptions,
                flowContext, effectivePersona, conversationId, lastUserMessage,
                username, tStart, tSetupEnd);
    }

    private Flux<ChatResponse> dispatchStream(TurAIAgent agent,
            TurLLMInstance llmInstance,
            String decryptedApiKey,
            List<Message> messages,
            String conversationId,
            String lastUserMessage,
            String username,
            long tStart,
            long tSetupEnd) {
        log.info("[AgentExec] STREAM mode for agent '{}' (no flow) — forwarding token chunks to SSE",
                agent.getTitle());
        // T18 / §IV.1.b: swap to the provider's streaming-optimized
        // ChatModel for this path. On OpenAI agents (Spring AI 2.0.0-M6)
        // this is the WebClient-based TurOpenAiStreamingChatModel which
        // publishes SSE deltas per chunk instead of buffering them
        // through the SDK's drain-iterator-then-publish pattern. Other
        // providers inherit the default (same instance as the CALL model).
        //
        // The streaming-only model is built per-turn (cheap — same
        // WebClient construction cost as the SDK path) so the resilience
        // wrap and provider lookup stay uniform.
        final ChatModel streamingChatModel = llmModelFactory.createStreamingChatModel(
                llmInstance, decryptedApiKey);
        // Build the streaming prompt with NO ChatOptions at all.
        //
        // Why: even with toolCallbacks=empty + internalToolExecutionEnabled(false),
        // passing a DefaultToolCallingChatOptions instance to
        // chatModel.stream() activates Spring AI's tool-aware aggregator
        // upstream. The aggregator buffers the entire delta stream so it
        // can inspect for tool calls before publishing — observed
        // first_token_ms ≈ llm_ms (all 400+ chunks drain in the last
        // ~150ms of the round-trip), which destroys the token-by-token
        // UX completely.
        //
        // Falling back to the ChatModel's default ChatOptions (the
        // ones baked into the model at construction) lets the upstream
        // SSE deltas flow through unaggregated — first token in
        // ~300-800ms, then continuous fill-in.
        //
        // Trade-off (documented on TurAgentChatMode): STREAM mode does
        // not honor per-turn tool callbacks. Agents that need tool
        // calling should stay on CALL.
        Prompt streamPrompt = new Prompt(messages);

        final long tLlmStart = System.currentTimeMillis();
        final StringBuilder accumulated = new StringBuilder();
        // Captures the last raw Spring AI ChatResponse so we can read
        // token-usage metadata at completion (providers attach Usage to
        // the final aggregated chunk; reading the first chunk would
        // under-report).
        final AtomicReference<org.springframework.ai.chat.model.ChatResponse> lastRaw =
                new AtomicReference<>();
        // Diagnostics: count chunks and capture first-token time. The ratio
        // chars/chunks tells us at a glance whether Spring AI is buffering
        // (one big chunk) or actually forwarding deltas (~30-200 small
        // chunks for a typical reply).
        //
        // Two first-chunk timers isolate WHERE buffering happens:
        //   firstRawAt   — instant the FIRST raw Spring AI ChatResponse
        //                  arrives at our subscriber.
        //   firstChunkAt — instant the FIRST non-empty CONTENT chunk
        //                  reaches the SSE emit point (after map+filter).
        final AtomicInteger chunkCount = new AtomicInteger(0);
        final AtomicInteger rawCount = new AtomicInteger(0);
        final AtomicReference<Long> firstChunkAt = new AtomicReference<>();
        final AtomicReference<Long> firstRawAt = new AtomicReference<>();
        return streamingChatModel.stream(streamPrompt)
                .doOnNext(raw -> {
                    rawCount.incrementAndGet();
                    firstRawAt.compareAndSet(null, System.currentTimeMillis());
                })
                .map(resp -> {
                    lastRaw.set(resp);
                    String chunk = (resp.getResult() != null
                            && resp.getResult().getOutput() != null
                            && resp.getResult().getOutput().getText() != null)
                                    ? resp.getResult().getOutput().getText()
                                    : "";
                    if (!chunk.isEmpty()) {
                        accumulated.append(chunk);
                    }
                    return new ChatResponse("assistant", chunk);
                })
                .filter(r -> !r.content().isEmpty())
                .doOnNext(r -> {
                    int n = chunkCount.incrementAndGet();
                    if (n == 1) {
                        firstChunkAt.compareAndSet(null, System.currentTimeMillis());
                    }
                })
                .doOnComplete(() -> {
                    long tLlmEnd = System.currentTimeMillis();
                    long tTotal = tLlmEnd - tStart;
                    Long firstAt = firstChunkAt.get();
                    long firstTokenMs = firstAt == null ? -1 : (firstAt - tLlmStart);
                    chatPipelineObservation.recordMillis(
                            TurMeterNames.STAGE_CHAT_LLM_CALL, tLlmEnd - tLlmStart);
                    chatPipelineObservation.recordMillis(
                            TurMeterNames.STAGE_CHAT_TOTAL, tTotal);
                    var lastResp = lastRaw.get();
                    if (lastResp != null) {
                        tokenUsageService.recordUsage(llmInstance, lastResp, username);
                    }
                    recordTurnTelemetry(agent, conversationId, lastUserMessage,
                            accumulated.toString(), lastResp);
                    int chunks = chunkCount.get();
                    int raws = rawCount.get();
                    int avgChunkChars = chunks > 0 ? accumulated.length() / chunks : 0;
                    Long firstRaw = firstRawAt.get();
                    long firstRawMs = firstRaw == null ? -1 : (firstRaw - tLlmStart);
                    log.info("[AgentExec][timing] STREAM total_ms={} setup_ms={} llm_ms={} "
                            + "first_raw_ms={} first_token_ms={} raws={} chunks={} chars={} avg_chunk_chars={}",
                            tTotal, tSetupEnd - tStart, tLlmEnd - tLlmStart,
                            firstRawMs, firstTokenMs, raws, chunks, accumulated.length(), avgChunkChars);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(err -> log.error(
                        "[AgentExec] STREAM agent '{}' error: {}",
                        agent.getTitle(), err.getMessage(), err));
    }

    private Flux<ChatResponse> dispatchCall(TurAIAgent agent,
            TurLLMInstance llmInstance,
            ChatModel chatModel,
            List<Message> messages,
            ChatOptions initialOptions,
            TurAgentChatFlowContext flowContext,
            TurPersona effectivePersona,
            String conversationId,
            String lastUserMessage,
            String username,
            long tStart,
            long tSetupEnd) {
        final Prompt prompt = new Prompt(messages, initialOptions);
        return Mono.fromCallable(() -> {
            // Post-T16: single LLM call per turn. The system prompt was
            // composed in setup with the post-advance node already in
            // context — no need to skip the call on freshly-triggered
            // turns (the engine's loadOrInitState parked the cursor on
            // the entry interactive node, and the prompt reflects it).
            String text = "";
            long tLlmStart = System.currentTimeMillis();
            log.info("[AgentExec] Calling LLM for agent '{}', LLM '{}'",
                    agent.getTitle(), llmInstance.getTitle());
            // Spring AI 2.0.0-RC1 no longer runs the tool-execution loop inside
            // ChatModel.call() — drive it explicitly so registered tools (native
            // / MCP / custom) actually fire instead of the model returning a bare
            // tool-call response (empty assistant text). See TurToolExecutionLoop.
            org.springframework.ai.chat.model.ChatResponse response =
                    toolExecutionLoop.call(chatModel, prompt);
            long tLlmEnd = System.currentTimeMillis();
            tokenUsageService.recordUsage(llmInstance, response, username);
            if (response.getResult() != null
                    && response.getResult().getOutput() != null
                    && response.getResult().getOutput().getText() != null) {
                text = response.getResult().getOutput().getText();
            }
            log.info("[AgentExec] Agent '{}' response: {} chars",
                    agent.getTitle(), text.length());
            chatPipelineObservation.recordMillis(TurMeterNames.STAGE_CHAT_LLM_CALL, tLlmEnd - tLlmStart);
            log.info("[AgentExec][timing] llm_ms={}", tLlmEnd - tLlmStart);

            // §I.5 step 1 invariant (post-T16): advance() already ran in
            // the setup phase BEFORE the LLM call, so {@code flowContext}
            // already reflects the post-advance cursor and the LLM has
            // composed {@code text} with the correct node in its system
            // prompt. Nothing to do at the post-LLM phase — no second
            // advance, no regen. Patches #6, #7, #8, #11, #14, #15 are
            // gone by construction.
            TurChatFlowState newState = flowContext == null ? null : flowContext.state();
            // Kept at 0 — advance() ran in the setup phase, no post-LLM
            // advance / regen substages exist in the inverted harness.
            // Preserved so the existing timing log keeps its shape
            // (advance_ms / regen_ms columns still emitted but always 0).
            long tAdvanceMs = 0;
            long tRegenMs = 0;

            // Persona's negative-constraint check runs LAST so it also
            // catches forbidden terms reintroduced by the flow override.
            // Validate against the effective persona — when the flow
            // triggered or transitioned to a new persona this turn, the
            // forbidden-terms list is the one of the active voice, not
            // the agent's default.
            TurPersonaValidationResult validation =
                    personaToneValidator.validate(effectivePersona, text);
            text = validation.sanitizedText();

            // Translate the LLM's `sandbox:` artifact URLs (code-interpreter
            // charts/files) into same-origin-relative /api/... paths so they
            // render inline regardless of the client's markdown renderer.
            text = TurChatArtifactUrls.normalize(text);

            // Telemetry: increment turn count + accumulate tokens for the
            // session, and append the turn to chat memory (NoOp when either
            // is disabled; safe to call unconditionally).
            recordTurnTelemetry(agent, conversationId, lastUserMessage, text, response);

            // Suggested chip options for the NEXT user turn. Two sources:
            //   • the current node's `inlineOptions` (chips that don't branch);
            //   • the labels of the immediate downstream switch's options
            //     (chips that route the flow on click via Tier-1 exact match).
            // Emitted as a separate SSE event AFTER the assistant text so the UI
            // can render the chips below the freshly-rendered bubble.
            List<String> suggestedOptions = List.of();
            // T107 — when the turn parks on a native multi-field formCapture
            // node, emit a structured "form" SSE event so the SDK renders a
            // native form instead of single-turn validation.
            String formJson = null;
            if (flowContext != null && newState != null) {
                ChatFlowGraph graph = flowContext.graph();
                ChatFlowNode current = graph.nodeById(newState.getCurrentNodeId()).orElse(null);
                suggestedOptions = ChatFlowOps.suggestedOptions(graph, current);
                formJson = buildFormJson(current);
            }
            // T292 — drain the per-turn RAG provenance the search_knowledge_base
            // tool recorded during the tool-execution loop, to emit it as a
            // structured `sources[]` SSE event below the answer.
            List<TurRagSource> sources = drainRagSources(initialOptions);
            long tTotal = System.currentTimeMillis() - tStart;
            // Post = total - setup - llm_call. Captures the time AFTER the LLM
            // returns: tone validation, telemetry, suggested options. Calling
            // it "post" lets the breakdown assert against pure post-processing
            // without the LLM round-trip muddying the number.
            long tPost = tTotal - (tSetupEnd - tStart) - (tLlmEnd - tLlmStart);
            chatPipelineObservation.recordMillis(TurMeterNames.STAGE_CHAT_POST, tPost);
            chatPipelineObservation.recordMillis(TurMeterNames.STAGE_CHAT_TOTAL, tTotal);
            chatPipelineObservation.recordMillis(TurMeterNames.STAGE_CHAT_ADVANCE, tAdvanceMs);
            chatPipelineObservation.recordMillis(TurMeterNames.STAGE_CHAT_REGEN, tRegenMs);
            log.info("[AgentExec][timing] total_ms={} setup_ms={} llm_ms={} post_ms={} (advance_ms={} regen_ms={})",
                    tTotal, tSetupEnd - tStart, tLlmEnd - tLlmStart, tPost,
                    tAdvanceMs, tRegenMs);
            return new AssistantTurn(text, suggestedOptions, formJson, sources);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(err -> log.error("[AgentExec] Agent '{}' error: {}",
                        agent.getTitle(), err.getMessage(), err))
                .flatMapMany(TurChatStreamingDispatcher::emitTurn);
    }

    /**
     * Records token + turn counters and appends the user/assistant pair to
     * chat memory. Token counts come from {@code response.getMetadata()} when
     * available; otherwise we record zeros so dashboards still get the turn
     * tick.
     */
    private void recordTurnTelemetry(TurAIAgent agent, String conversationId,
            String userMessage, String assistantMessage,
            org.springframework.ai.chat.model.ChatResponse response) {
        if (conversationId == null || conversationId.isBlank()) return;
        long tokensIn = 0L;
        long tokensOut = 0L;
        try {
            if (response != null && response.getMetadata() != null
                    && response.getMetadata().getUsage() != null) {
                Integer inT = response.getMetadata().getUsage().getPromptTokens();
                Integer outT = response.getMetadata().getUsage().getCompletionTokens();
                if (inT != null) tokensIn = inT.longValue();
                if (outT != null) tokensOut = outT.longValue();
            }
        } catch (RuntimeException e) {
            log.debug("[AgentExec] could not read token usage from response: {}", e.getMessage());
        }
        try {
            if (chatAnalyticsService != null && chatAnalyticsService.isEnabled()) {
                chatAnalyticsService.recordTurn(conversationId, tokensIn, tokensOut);
            }
            if (chatMemoryService != null) {
                chatMemoryService.recordTurn(null, agent, conversationId, null,
                        userMessage, assistantMessage);
            }
        } catch (RuntimeException e) {
            log.debug("[AgentExec] turn telemetry failed: {}", e.getMessage());
        }
    }

    /**
     * Single assistant turn: the final text, any chips that should appear
     * underneath, and an optional pre-serialized {@code "form"} payload (T107)
     * when the turn parks on a native multi-field {@code formCapture} node.
     * Internal record — never crosses the controller boundary;
     * {@link #emitTurn(AssistantTurn)} fans it out into one to three
     * {@link ChatResponse} events.
     */
    private record AssistantTurn(String text, List<String> options, String formJson,
            List<TurRagSource> sources) {
    }

    /**
     * T292 — drains the per-turn {@link TurRagSourceCollector} the chat
     * executor published into the CALL-branch {@link ChatOptions} tool context.
     * Returns an empty list when no collector is present (e.g. STREAM-only
     * agents, or options without a tool context). The same options instance is
     * carried unchanged across the {@code TurToolExecutionLoop} rounds, so the
     * collector here is the one the {@code search_knowledge_base} tool wrote to.
     */
    private static List<TurRagSource> drainRagSources(ChatOptions options) {
        if (!(options instanceof org.springframework.ai.model.tool.ToolCallingChatOptions toolOptions)) {
            return List.of();
        }
        var ctx = toolOptions.getToolContext();
        if (ctx == null) {
            return List.of();
        }
        Object raw = ctx.get(
                com.viglet.turing.genai.tool.TurCustomToolCallbackService.TOOL_CONTEXT_RAG_SOURCES);
        if (raw instanceof TurRagSourceCollector collector) {
            return collector.snapshot();
        }
        return List.of();
    }

    /**
     * Fans an {@link AssistantTurn} out into the SSE stream: always one
     * {@code "token"} event with the text (when non-empty), optionally
     * followed by one {@code "options"} event whose {@code content} is the
     * JSON-encoded chip labels, and optionally one {@code "form"} event whose
     * {@code content} is the JSON-encoded form schema (T107). The form event
     * is emitted last so the SDK can render the form below the freshly-shown
     * assistant bubble and chips.
     */
    private static Flux<ChatResponse> emitTurn(AssistantTurn turn) {
        Flux<ChatResponse> events = turn.text() == null || turn.text().isEmpty()
                ? Flux.empty()
                : Flux.just(new ChatResponse("assistant", turn.text()));
        // T292 — provenance chips render directly under the answer bubble, so
        // emit the `sources` event right after the text token and before the
        // chip/form events.
        if (turn.sources() != null && !turn.sources().isEmpty()) {
            try {
                String sourcesJson = OBJECT_MAPPER.writeValueAsString(turn.sources());
                events = events.concatWith(
                        Flux.just(new ChatResponse("assistant", sourcesJson, "sources")));
            } catch (JacksonException e) {
                log.warn("[AgentExec] could not serialize {} RAG source(s): {}",
                        turn.sources().size(), e.getMessage());
            }
        }
        if (turn.options() != null && !turn.options().isEmpty()) {
            try {
                String optionsJson = OBJECT_MAPPER.writeValueAsString(turn.options());
                events = events.concatWith(
                        Flux.just(new ChatResponse("assistant", optionsJson, "options")));
            } catch (JacksonException e) {
                log.warn("[AgentExec] could not serialize {} suggested option(s): {}",
                        turn.options().size(), e.getMessage());
            }
        }
        if (turn.formJson() != null && !turn.formJson().isBlank()) {
            events = events.concatWith(
                    Flux.just(new ChatResponse("assistant", turn.formJson(), "form")));
        }
        return events;
    }

    /**
     * Builds the JSON payload for a {@code "form"} SSE event when {@code node}
     * is a native multi-field {@code formCapture} (T107), else returns
     * {@code null}. The shape mirrors what the SDK expects:
     * {@code {nodeId, title, submitLabel, fields:[{name,label,type,required,placeholder,validationRule,options}]}}.
     */
    private static String buildFormJson(ChatFlowNode node) {
        if (!ChatFlowOps.isNativeForm(node)) {
            return null;
        }
        String title = node.aiInstruction() != null && !node.aiInstruction().isBlank()
                ? node.aiInstruction()
                : node.label();
        FormPrompt prompt = new FormPrompt(node.id(), title, node.formFields());
        try {
            return OBJECT_MAPPER.writeValueAsString(prompt);
        } catch (JacksonException e) {
            log.warn("[AgentExec] could not serialize form schema for node '{}': {}",
                    node.id(), e.getMessage());
            return null;
        }
    }

    /**
     * Wire shape of the {@code "form"} SSE event (T107). {@code fields} reuses
     * the engine's {@link ChatFlowNode.FormField} record verbatim.
     */
    private record FormPrompt(String nodeId, String title,
            List<ChatFlowNode.FormField> fields) {
    }
}
