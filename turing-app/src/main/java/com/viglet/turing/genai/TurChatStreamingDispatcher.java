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
import com.viglet.turing.genai.tool.TurChatToolCall;
import com.viglet.turing.genai.tool.TurToolCallCollector;
import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.genai.persona.TurPersonaToneValidator;
import com.viglet.turing.genai.persona.TurPersonaValidationResult;
import com.viglet.turing.genai.rag.TurRagSource;
import com.viglet.turing.genai.rag.TurRagSourceCollector;
import com.viglet.turing.genai.safety.guardrail.TurAnswerGuardrailAction;
import com.viglet.turing.genai.safety.guardrail.TurAnswerGuardrailService;
import com.viglet.turing.genai.safety.guardrail.TurAnswerGuardrailVerdict;
import com.viglet.turing.genai.secondopinion.TurSecondOpinion;
import com.viglet.turing.genai.secondopinion.TurSecondOpinionService;
import com.viglet.turing.genai.spectator.TurChatMessageEvent;
import com.viglet.turing.genai.spectator.TurChatMessageEventBus;
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
import reactor.core.publisher.Sinks;
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

    // --- S1192: extracted duplicated literals ---
    private static final String ASSISTANT = "assistant";


    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TurLlmModelFactory llmModelFactory;
    private final TurLLMTokenUsageService tokenUsageService;
    private final TurPersonaToneValidator personaToneValidator;
    private final TurChatAnalyticsService chatAnalyticsService;
    private final TurChatMemoryService chatMemoryService;
    private final TurChatPipelineObservation chatPipelineObservation;
    private final TurToolExecutionLoop toolExecutionLoop;
    private final TurChatMessageEventBus chatMessageEventBus;
    private final com.viglet.turing.service.llm.budget.TurChatCostBudgetGate costBudgetGate;
    private final com.viglet.turing.service.chatanalytics.TurToolCallTraceService toolCallTraceService;
    private final com.viglet.turing.genai.clienttool.TurClientToolService clientToolService;
    private final TurClientToolParkRegistry clientToolParkRegistry;
    private final TurAnswerGuardrailService answerGuardrailService;
    private final TurSecondOpinionService secondOpinionService;
    private final com.viglet.turing.genai.capture.TurPromptCaptureService promptCaptureService;

    public TurChatStreamingDispatcher(TurLlmModelFactory llmModelFactory,
            TurLLMTokenUsageService tokenUsageService,
            TurPersonaToneValidator personaToneValidator,
            TurChatAnalyticsService chatAnalyticsService,
            TurChatMemoryService chatMemoryService,
            TurChatPipelineObservation chatPipelineObservation,
            TurToolExecutionLoop toolExecutionLoop,
            TurChatMessageEventBus chatMessageEventBus,
            com.viglet.turing.service.llm.budget.TurChatCostBudgetGate costBudgetGate,
            com.viglet.turing.service.chatanalytics.TurToolCallTraceService toolCallTraceService,
            com.viglet.turing.genai.clienttool.TurClientToolService clientToolService,
            TurClientToolParkRegistry clientToolParkRegistry,
            TurAnswerGuardrailService answerGuardrailService,
            TurSecondOpinionService secondOpinionService,
            com.viglet.turing.genai.capture.TurPromptCaptureService promptCaptureService) {
        this.llmModelFactory = llmModelFactory;
        this.tokenUsageService = tokenUsageService;
        this.personaToneValidator = personaToneValidator;
        this.chatAnalyticsService = chatAnalyticsService;
        this.chatMemoryService = chatMemoryService;
        this.chatPipelineObservation = chatPipelineObservation;
        this.toolExecutionLoop = toolExecutionLoop;
        this.chatMessageEventBus = chatMessageEventBus;
        this.costBudgetGate = costBudgetGate;
        this.toolCallTraceService = toolCallTraceService;
        this.clientToolService = clientToolService;
        this.clientToolParkRegistry = clientToolParkRegistry;
        this.answerGuardrailService = answerGuardrailService;
        this.secondOpinionService = secondOpinionService;
        this.promptCaptureService = promptCaptureService;
    }

    /**
     * Dispatch the prepared chat turn to the chat model.
     *
     * @param turn the prepared turn context — bundles the chatting agent, the
     *             underlying LLM instance (token usage + streaming model swap on
     *             STREAM), conversationId (nullable — null means stateless turn),
     *             the latest user message, the resolved username (token usage
     *             attribution), and the turn-start / setup-end millis used for
     *             time accounting
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
     */
    public Flux<ChatResponse> dispatch(TurChatTurnContext turn,
            String decryptedApiKey,
            ChatModel chatModel,
            List<Message> messages,
            ChatOptions initialOptions,
            TurAgentChatFlowContext flowContext,
            TurPersona effectivePersona) {
        TurAIAgent agent = turn.agent();

        // T618 — capture the exact assembled prompt (system message + whole
        // message list) at send time, before either branch touches it, so the
        // Live Preview can replay this turn verbatim later. Opt-in per-agent +
        // storage-backed + fail-open (never breaks the turn); same list both
        // the CALL and STREAM branches send.
        promptCaptureService.captureSpringAi(agent, turn.conversationId(), messages);

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
            return dispatchStream(turn, decryptedApiKey, messages);
        }
        return dispatchCall(turn, chatModel, messages, initialOptions,
                flowContext, effectivePersona);
    }

    private Flux<ChatResponse> dispatchStream(TurChatTurnContext turn,
            String decryptedApiKey,
            List<Message> messages) {
        TurAIAgent agent = turn.agent();
        TurLLMInstance llmInstance = turn.llmInstance();
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
                    String chunk = extractChunkText(resp);
                    if (!chunk.isEmpty()) {
                        accumulated.append(chunk);
                    }
                    return new ChatResponse(ASSISTANT, chunk);
                })
                .filter(r -> !r.content().isEmpty())
                .doOnNext(r -> {
                    int n = chunkCount.incrementAndGet();
                    if (n == 1) {
                        firstChunkAt.compareAndSet(null, System.currentTimeMillis());
                    }
                })
                .doOnComplete(() -> recordStreamCompletion(turn, tLlmStart,
                        new StreamAccumulators(accumulated, lastRaw,
                                chunkCount, rawCount, firstChunkAt, firstRawAt)))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(err -> log.error(
                        "[AgentExec] STREAM agent '{}' error: {}",
                        agent.getTitle(), err.getMessage(), err));
    }

    /**
     * Extracts the text payload of a streaming chunk, tolerating the nullable
     * result/output/text chain Spring AI exposes on partial responses.
     *
     * @since 2026.3.1
     */
    private static String extractChunkText(org.springframework.ai.chat.model.ChatResponse resp) {
        return (resp.getResult() != null
                && resp.getResult().getOutput() != null
                && resp.getResult().getOutput().getText() != null)
                        ? resp.getResult().getOutput().getText()
                        : "";
    }

    /**
     * The mutable accumulators a streaming turn fills in as chunks arrive, read
     * once at completion to compute cost + timing telemetry. Bundled so
     * {@link #recordStreamCompletion} stays below the parameter threshold.
     */
    private record StreamAccumulators(
            StringBuilder accumulated,
            AtomicReference<org.springframework.ai.chat.model.ChatResponse> lastRaw,
            AtomicInteger chunkCount,
            AtomicInteger rawCount,
            AtomicReference<Long> firstChunkAt,
            AtomicReference<Long> firstRawAt) {
    }

    /**
     * Records cost, turn telemetry, and timing diagnostics when the streaming
     * Flux completes. Extracted from the {@code doOnComplete} lambda so the
     * reactive assembly in {@link #dispatchStream} stays readable.
     *
     * @since 2026.3.1
     */
    private void recordStreamCompletion(TurChatTurnContext turn, long tLlmStart,
            StreamAccumulators acc) {
        TurAIAgent agent = turn.agent();
        TurLLMInstance llmInstance = turn.llmInstance();
        String conversationId = turn.conversationId();
        String lastUserMessage = turn.lastUserMessage();
        String username = turn.username();
        long tStart = turn.tStart();
        long tSetupEnd = turn.tSetupEnd();
        StringBuilder accumulated = acc.accumulated();
        AtomicReference<org.springframework.ai.chat.model.ChatResponse> lastRaw = acc.lastRaw();
        AtomicInteger chunkCount = acc.chunkCount();
        AtomicInteger rawCount = acc.rawCount();
        AtomicReference<Long> firstChunkAt = acc.firstChunkAt();
        AtomicReference<Long> firstRawAt = acc.firstRawAt();
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
            double turnCost = tokenUsageService.recordUsage(llmInstance, lastResp, username,
                    agent == null ? null : agent.getId(),
                    TurMeterNames.STAGE_CHAT_LIVE);
            costBudgetGate.warnIfTurnOverCap(agent, turnCost);
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
    }

    private Flux<ChatResponse> dispatchCall(TurChatTurnContext turn,
            ChatModel chatModel,
            List<Message> messages,
            ChatOptions initialOptions,
            TurAgentChatFlowContext flowContext,
            TurPersona effectivePersona) {
        TurAIAgent agent = turn.agent();
        TurLLMInstance llmInstance = turn.llmInstance();
        String conversationId = turn.conversationId();
        String lastUserMessage = turn.lastUserMessage();
        String username = turn.username();
        long tStart = turn.tStart();
        long tSetupEnd = turn.tSetupEnd();
        final Prompt prompt = new Prompt(messages, initialOptions);
        // T438 — the agent's declared frontend tool names (empty unless opted in).
        // A model call to one of these parks the turn instead of executing it.
        final java.util.Set<String> clientToolNames = clientToolService.namesFor(agent);
        Flux<ChatResponse> turnFlux = Mono.fromCallable(() -> {
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
            //
            // T438 — when the model instead calls a declared frontend ("client")
            // tool, the client-tool-aware loop throws: we park the turn (storing
            // everything a resume needs) and emit a `client_tool_call` event so
            // the browser runs it and POSTs the result back to continue.
            org.springframework.ai.chat.model.ChatResponse response;
            try {
                response = toolExecutionLoop.callWithClientTools(chatModel, prompt, clientToolNames);
            } catch (com.viglet.turing.genai.clienttool.TurClientToolParkException park) {
                return parkClientTool(turn, chatModel, initialOptions, flowContext,
                        effectivePersona, park);
            }
            long tLlmEnd = System.currentTimeMillis();
            double turnCost = tokenUsageService.recordUsage(llmInstance, response, username,
                    agent == null ? null : agent.getId(),
                    TurMeterNames.STAGE_CHAT_LIVE);
            costBudgetGate.warnIfTurnOverCap(agent, turnCost);
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

            // T516 — answer-grounding guardrail (opt-in; no verdict + no event
            // when disabled, so the legacy path is byte-for-byte unchanged).
            // Runs before telemetry so a blocked/redacted answer is what gets
            // recorded in chat memory and seen by the spectator. Fail-open.
            GuardedAnswer guarded = applyAnswerGuardrail(lastUserMessage, text, initialOptions);
            text = guarded.text();
            TurAnswerGuardrailVerdict guardrailVerdict = guarded.verdict();

            // T522 — multi-provider "second opinion": a different-vendor critic
            // judges the answer; the agreement is surfaced beside it as a
            // confidence signal. Opt-in + fail-open (null when disabled). Uses the
            // final (possibly redacted) text + the retrieved RAG context.
            TurSecondOpinion secondOpinion = secondOpinionService.isEnabled()
                    ? secondOpinionService.evaluate(lastUserMessage, text,
                            drainRagContext(initialOptions), llmInstance).orElse(null)
                    : null;

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
            return new AssistantTurn(text, suggestedOptions, formJson, sources, guardrailVerdict,
                    secondOpinion, null);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(err -> log.error("[AgentExec] Agent '{}' error: {}",
                        agent.getTitle(), err.getMessage(), err))
                .flatMapMany(TurChatStreamingDispatcher::emitTurn);

        // T427 / T436 — the per-turn tool-call collector (published in the tool
        // context by the executor). After the loop, its terminal rows are drained
        // into the read-only trace store; when the agent opts into live events,
        // each call is also streamed as a `tool_call` SSE event interleaved with
        // the turn (they arrive while the tool loop runs, before the answer token).
        TurToolCallCollector collector = readToolCallCollector(initialOptions);
        if (collector == null) {
            return turnFlux;
        }
        if (!agent.isToolCallEventsEnabled()) {
            return turnFlux.doFinally(sig -> drainToolCallTrace(conversationId, collector));
        }
        Sinks.Many<ChatResponse> toolEventSink = Sinks.many().unicast().onBackpressureBuffer();
        collector.attachListener(call -> toolEventSink.tryEmitNext(toToolCallEvent(call)));
        Flux<ChatResponse> completing = turnFlux
                .doOnComplete(toolEventSink::tryEmitComplete)
                .doOnError(err -> toolEventSink.tryEmitComplete());
        return Flux.merge(toolEventSink.asFlux(), completing)
                .doFinally(sig -> drainToolCallTrace(conversationId, collector));
    }

    /**
     * Reads the per-turn {@link TurToolCallCollector} from the CALL-branch tool
     * context. Returns {@code null} for tool-stripped options (active node forbids
     * tools) or non-tool-calling options — both legitimately skip the trace.
     */
    private static TurToolCallCollector readToolCallCollector(ChatOptions options) {
        if (!(options instanceof org.springframework.ai.model.tool.ToolCallingChatOptions toolOptions)) {
            return null;
        }
        var ctx = toolOptions.getToolContext();
        if (ctx == null) {
            return null;
        }
        Object raw = ctx.get(
                com.viglet.turing.genai.tool.TurCustomToolCallbackService.TOOL_CONTEXT_TOOL_CALLS);
        return raw instanceof TurToolCallCollector collector ? collector : null;
    }

    /** Serializes a {@link TurChatToolCall} into a {@code "tool_call"} SSE event. */
    private static ChatResponse toToolCallEvent(
            com.viglet.turing.genai.tool.TurChatToolCall call) {
        try {
            return new ChatResponse(ASSISTANT, OBJECT_MAPPER.writeValueAsString(call), "tool_call");
        } catch (JacksonException e) {
            log.warn("[AgentExec] could not serialize tool_call event for '{}': {}",
                    call.name(), e.getMessage());
            return new ChatResponse(ASSISTANT, "{}", "tool_call");
        }
    }

    /**
     * Drains the turn's terminal tool-call rows into the per-conversation trace
     * store (T427). Best-effort and off the critical path — a failure here must
     * never surface to the chat.
     */
    private void drainToolCallTrace(String conversationId,
            com.viglet.turing.genai.tool.TurToolCallCollector collector) {
        try {
            if (conversationId != null && !conversationId.isBlank()
                    && toolCallTraceService != null && !collector.isEmpty()) {
                toolCallTraceService.record(conversationId, collector.snapshot());
            }
        } catch (RuntimeException e) {
            log.debug("[AgentExec] tool-call trace record failed: {}", e.getMessage());
        }
    }

    /**
     * T438 — parks the turn that just requested a frontend tool and returns the
     * {@link AssistantTurn} that {@link #emitTurn} fans out as the single
     * {@code client_tool_call} SSE event. The park holds everything a resume
     * needs ({@link #resumeClientTool}); a blank conversation id can't be resumed
     * (no key), so we still surface the event but skip the park.
     */
    private AssistantTurn parkClientTool(TurChatTurnContext turn, ChatModel chatModel,
            ChatOptions initialOptions, TurAgentChatFlowContext flowContext, TurPersona persona,
            com.viglet.turing.genai.clienttool.TurClientToolParkException park) {
        org.springframework.ai.chat.messages.AssistantMessage.ToolCall call = park.toolCall();
        String callId = call.id() != null && !call.id().isBlank()
                ? call.id()
                : java.util.UUID.randomUUID().toString();
        String conversationId = turn.conversationId();
        if (conversationId != null && !conversationId.isBlank()) {
            clientToolParkRegistry.park(new TurClientToolParkRegistry.ParkedClientTurn(
                    conversationId, callId, call.name(), call.arguments(),
                    turn, chatModel, initialOptions, flowContext, persona,
                    park.promptMessages(), park.assistantMessage(),
                    System.currentTimeMillis() + TurClientToolParkRegistry.DEFAULT_TTL_MILLIS));
        } else {
            log.warn("[ClientTool] stateless turn (no conversationId) — emitting "
                    + "client_tool_call '{}' without a resumable park", call.name());
        }
        return AssistantTurn.parked(new ClientToolCall(callId, call.name(), call.arguments()));
    }

    /**
     * T438 — resumes a turn parked on a frontend tool. Appends the browser's
     * result (or error) as the tool response for {@code callId} and re-dispatches
     * the continuation through {@link #dispatchCall} (which may answer or park
     * again on another client tool). Throws {@link IllegalArgumentException} when
     * the park is unknown / already resumed / expired so the controller can map
     * it to a 404/410.
     *
     * @param result the browser's tool result as a JSON/string payload (nullable)
     * @param error  a client-side error message; when non-null it is fed back to
     *               the model as the tool result instead of {@code result}
     */
    public Flux<ChatResponse> resumeClientTool(String conversationId, String callId,
            String result, String error) {
        TurClientToolParkRegistry.ParkedClientTurn parked =
                clientToolParkRegistry.take(conversationId, callId);
        if (parked == null) {
            throw new IllegalArgumentException(
                    "No parked client-tool call for conversation=" + conversationId + " callId=" + callId);
        }
        String responseData = error != null && !error.isBlank()
                ? "ERROR: " + error
                : (result == null ? "" : result);
        var toolResponse = new org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse(
                callId, parked.toolName(), responseData);
        List<Message> resumeMessages = new java.util.ArrayList<>(parked.promptMessages());
        resumeMessages.add(parked.assistantMessage());
        resumeMessages.add(org.springframework.ai.chat.messages.ToolResponseMessage.builder()
                .responses(List.of(toolResponse))
                .build());
        log.info("[ClientTool] resuming conv={} callId={} tool={} ({})",
                conversationId, callId, parked.toolName(), error != null ? "error" : "result");
        return dispatchCall(parked.turn(), parked.chatModel(), resumeMessages,
                parked.initialOptions(), parked.flowContext(), parked.persona());
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
        // T120 — tee the completed turn onto the spectator message bus so an
        // operator watching this conversation sees it fill in live. Best-effort,
        // off the chat's critical path; a dropped event self-heals from the
        // chat-memory snapshot on the spectator's next subscribe.
        if (chatMessageEventBus != null && conversationId != null && !conversationId.isBlank()) {
            chatMessageEventBus.publish(TurChatMessageEvent.user(conversationId, userMessage));
            chatMessageEventBus.publish(TurChatMessageEvent.assistant(conversationId, assistantMessage));
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
            List<TurRagSource> sources, TurAnswerGuardrailVerdict grounding,
            TurSecondOpinion secondOpinion, ClientToolCall clientToolCall) {

        /** T438 — a turn that parked on a frontend tool instead of answering. */
        static AssistantTurn parked(ClientToolCall call) {
            return new AssistantTurn("", List.of(), null, List.of(), null, null, call);
        }
    }

    /** T438 — payload of the {@code client_tool_call} SSE event. */
    private record ClientToolCall(String callId, String name, String args) {
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
            // Unify chunk-level provenance into one entry per source document so
            // the chat client doesn't show the same URL once per retrieved chunk.
            return TurRagSource.dedupeByDocument(collector.snapshot());
        }
        return List.of();
    }

    /**
     * T516 — drains the per-turn retrieved chunk <em>texts</em> from the same
     * {@link TurRagSourceCollector}, used as the grounding context for the
     * answer guardrail. Empty when no RAG ran this turn (the guardrail then
     * checks the answer with no context — moderation-only adapters still work).
     */
    private static List<String> drainRagContext(ChatOptions options) {
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
            return collector.contextSnapshot();
        }
        return List.of();
    }

    /**
     * T516 — runs the opt-in answer-grounding guardrail and decides what text to
     * deliver. Returns the (possibly redacted/blocked) answer plus the verdict to
     * surface (null when the guardrail is off or found nothing). Extracted from
     * {@link #dispatchCall} to keep that method's complexity in check. Fail-open
     * lives in {@link TurAnswerGuardrailService#evaluate}.
     */
    private GuardedAnswer applyAnswerGuardrail(String userMessage, String text, ChatOptions options) {
        if (!answerGuardrailService.isEnabled()) {
            return new GuardedAnswer(text, null);
        }
        var verdictOpt = answerGuardrailService.evaluate(userMessage, text, drainRagContext(options));
        if (verdictOpt.isEmpty()) {
            return new GuardedAnswer(text, null);
        }
        TurAnswerGuardrailVerdict verdict = verdictOpt.get();
        if (!answerGuardrailService.isBlockOnViolation()) {
            return new GuardedAnswer(text, verdict);
        }
        String replacement = verdict.redactedAnswer() != null
                ? verdict.redactedAnswer()
                : answerGuardrailService.getBlockedMessage();
        return new GuardedAnswer(replacement, verdict.withAction(TurAnswerGuardrailAction.BLOCK));
    }

    /** The outcome of {@link #applyAnswerGuardrail}: text to deliver + verdict to surface. */
    private record GuardedAnswer(String text, TurAnswerGuardrailVerdict verdict) {
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
        // T438 — a parked turn emits exactly one `client_tool_call` event and
        // nothing else; the answer (if any) comes after the browser resumes.
        if (turn.clientToolCall() != null) {
            ClientToolCall call = turn.clientToolCall();
            try {
                String json = OBJECT_MAPPER.writeValueAsString(call);
                return Flux.just(new ChatResponse(ASSISTANT, json, "client_tool_call"));
            } catch (JacksonException e) {
                log.warn("[AgentExec] could not serialize client_tool_call '{}': {}",
                        call.name(), e.getMessage());
                return Flux.empty();
            }
        }
        Flux<ChatResponse> events = turn.text() == null || turn.text().isEmpty()
                ? Flux.empty()
                : Flux.just(new ChatResponse(ASSISTANT, turn.text()));
        // T516 — guardrail verdict badge (present only when the guardrail is
        // enabled and flagged something), then T292 provenance chips, then chips.
        events = appendJsonEvent(events, turn.grounding(), "grounding");
        // T522 — cross-vendor second-opinion verdict (present only when enabled).
        events = appendJsonEvent(events, turn.secondOpinion(), "secondOpinion");
        events = appendJsonEvent(events,
                turn.sources() == null || turn.sources().isEmpty() ? null : turn.sources(), "sources");
        events = appendJsonEvent(events,
                turn.options() == null || turn.options().isEmpty() ? null : turn.options(), "options");
        if (turn.formJson() != null && !turn.formJson().isBlank()) {
            events = events.concatWith(
                    Flux.just(new ChatResponse(ASSISTANT, turn.formJson(), "form")));
        }
        return events;
    }

    /**
     * Concatenates one JSON SSE event of {@code type} carrying the serialized
     * {@code payload} onto {@code events}, or returns {@code events} unchanged
     * when {@code payload} is {@code null} or serialization fails (best-effort —
     * a side event must never break the answer stream).
     */
    private static Flux<ChatResponse> appendJsonEvent(Flux<ChatResponse> events,
            Object payload, String type) {
        if (payload == null) {
            return events;
        }
        try {
            String json = OBJECT_MAPPER.writeValueAsString(payload);
            return events.concatWith(Flux.just(new ChatResponse(ASSISTANT, json, type)));
        } catch (JacksonException e) {
            log.warn("[AgentExec] could not serialize {} SSE event: {}", type, e.getMessage());
            return events;
        }
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
