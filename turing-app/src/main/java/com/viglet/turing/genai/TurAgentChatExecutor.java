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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.genai.persona.TurAgentPersonaResolver;
import com.viglet.turing.genai.persona.TurPersonaBrandContextProvider;
import com.viglet.turing.genai.persona.TurPersonaPromptComposer;
import com.viglet.turing.genai.persona.TurPersonaToneValidator;
import com.viglet.turing.genai.persona.TurPersonaValidationResult;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.genai.tool.TurCustomToolCallbackService;
import com.viglet.turing.genai.tool.TurMcpToolCallbackService;
import com.viglet.turing.genai.tool.TurToolCallbackPipeline;
import com.viglet.turing.genai.tool.TurNativeToolService;
import com.viglet.turing.observability.TurChatPipelineObservation;
import com.viglet.turing.observability.TurMeterNames;
import com.viglet.turing.persistence.model.agent.TurAgentChatMode;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.service.chatanalytics.TurChatAnalyticsService;
import com.viglet.turing.service.chatmemory.TurChatMemoryService;
import com.viglet.turing.service.llm.tokenusage.TurLLMTokenUsageService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared executor for agent-driven chats. Centralizes:
 * <ul>
 *   <li>Resolving the agent's tool callbacks (native + MCP).</li>
 *   <li>Building the chat model from an LLM instance + decrypted API key.</li>
 *   <li>Assembling the {@link Prompt} with system/user/assistant messages.</li>
 *   <li>Streaming the response as {@code Flux<ChatResponse>} and recording
 *       token usage.</li>
 * </ul>
 *
 * Both the standalone agent chat ({@code /api/v2/ai-agent/{id}/chat}) and the
 * SN site AI Mode chat ({@code /api/sn/{site}/chat/conversation}) call into
 * this — the SN flow simply augments the system prompt with retrieved RAG
 * context before delegating.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@Service
public class TurAgentChatExecutor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are an AI assistant. Answer the user's questions using the tools available to you.
            If you have access to MCP server tools, use them when relevant to fulfill the user's request.
            If the user asks in a specific language, respond in that same language.
            """;

    /**
     * Sentinel value the front-end sends as {@code flowId} when the user
     * picks "Sem fluxo" in the chat dropdown — the auto-trigger router must
     * be skipped entirely.
     */
    public static final String FLOW_ID_NONE = "__none__";

    private final TurLlmModelFactory llmModelFactory;
    private final TurSecretCryptoService turSecretCryptoService;
    private final TurNativeToolService nativeToolService;
    private final TurMcpToolCallbackService mcpToolCallbackService;
    private final TurCustomToolCallbackService customToolCallbackService;
    private final TurToolCallbackPipeline toolCallbackPipeline;
    private final TurLLMTokenUsageService tokenUsageService;
    private final TurChatFlowRepository chatFlowRepository;
    private final TurChatFlowEngineService chatFlowEngineService;
    private final TurPersonaPromptComposer personaPromptComposer;
    private final TurPersonaToneValidator personaToneValidator;
    private final TurPersonaBrandContextProvider personaBrandContextProvider;
    private final TurAgentPersonaResolver personaResolver;
    private final TurChatAnalyticsService chatAnalyticsService;
    private final TurChatMemoryService chatMemoryService;
    private final TurChatPipelineObservation chatPipelineObservation;

    public TurAgentChatExecutor(TurLlmModelFactory llmModelFactory,
            TurSecretCryptoService turSecretCryptoService,
            TurNativeToolService nativeToolService,
            TurMcpToolCallbackService mcpToolCallbackService,
            TurCustomToolCallbackService customToolCallbackService,
            TurToolCallbackPipeline toolCallbackPipeline,
            TurLLMTokenUsageService tokenUsageService,
            TurChatFlowRepository chatFlowRepository,
            TurChatFlowEngineService chatFlowEngineService,
            TurPersonaPromptComposer personaPromptComposer,
            TurPersonaToneValidator personaToneValidator,
            TurPersonaBrandContextProvider personaBrandContextProvider,
            TurAgentPersonaResolver personaResolver,
            TurChatAnalyticsService chatAnalyticsService,
            TurChatMemoryService chatMemoryService,
            TurChatPipelineObservation chatPipelineObservation) {
        this.llmModelFactory = llmModelFactory;
        this.turSecretCryptoService = turSecretCryptoService;
        this.nativeToolService = nativeToolService;
        this.mcpToolCallbackService = mcpToolCallbackService;
        this.customToolCallbackService = customToolCallbackService;
        this.toolCallbackPipeline = toolCallbackPipeline;
        this.tokenUsageService = tokenUsageService;
        this.chatFlowRepository = chatFlowRepository;
        this.chatFlowEngineService = chatFlowEngineService;
        this.personaPromptComposer = personaPromptComposer;
        this.personaToneValidator = personaToneValidator;
        this.personaBrandContextProvider = personaBrandContextProvider;
        this.personaResolver = personaResolver;
        this.chatAnalyticsService = chatAnalyticsService;
        this.chatMemoryService = chatMemoryService;
        this.chatPipelineObservation = chatPipelineObservation;
    }

    public record ChatMessageItem(String role, String content) {
    }

    /**
     * One emission of the chat SSE stream. {@code type} discriminates the payload:
     * <ul>
     *   <li>{@code "token"} (default) — {@code content} is the assistant's text turn,
     *       sent as a single chunk (the executor does not stream token-by-token).</li>
     *   <li>{@code "options"} — {@code content} is a JSON array of strings to render as
     *       clickable chips below the assistant message. Picking one sends that label
     *       as the next user message; free-text input stays available in parallel.</li>
     * </ul>
     */
    public record ChatResponse(String role, String content, String type) {
        /** Backwards-compatible constructor: defaults {@code type} to {@code "token"}. */
        public ChatResponse(String role, String content) {
            this(role, content, "token");
        }
    }

    /**
     * Resolve the union of native + MCP + custom tool callbacks attached to
     * the agent. The {@code activePersona} parameter is the
     * runtime-resolved persona (which may differ from
     * {@code agent.getDefaultPersona()} when a chat flow has overridden it)
     * — used to decide which brand-context MCP tool, if any, to attach.
     */
    public ToolCallback[] resolveToolCallbacks(TurAIAgent agent, TurPersona activePersona) {
        Set<String> selectedNativeTools = parseNativeTools(agent.getNativeTools());
        ToolCallback[] nativeCallbacks = selectedNativeTools.isEmpty()
                ? new ToolCallback[0]
                : nativeToolService.getToolCallbacks(selectedNativeTools);
        ToolCallback[] mcpCallbacks = agent.getMcpServers() == null || agent.getMcpServers().isEmpty()
                ? new ToolCallback[0]
                : mcpToolCallbackService.getToolCallbacks(agent.getMcpServers());
        ToolCallback[] customCallbacks = agent.getCustomTools() == null || agent.getCustomTools().isEmpty()
                ? new ToolCallback[0]
                : customToolCallbackService.getToolCallbacks(agent.getCustomTools());
        ToolCallback[] personaBrandCallbacks =
                personaBrandContextProvider.getToolCallbacks(activePersona);
        return toolCallbackPipeline.decorate(
                Stream.of(nativeCallbacks, mcpCallbacks, customCallbacks, personaBrandCallbacks)
                        .flatMap(Arrays::stream)
                        .toArray(ToolCallback[]::new));
    }

    /**
     * Run an agent-driven chat without a chat flow attached. Convenience
     * wrapper kept for the SN site AI Mode (RAG) flow that doesn't use the
     * Phase B engine.
     */
    public Flux<ChatResponse> execute(TurAIAgent agent,
            TurLLMInstance llmInstance,
            List<ChatMessageItem> history,
            String systemPromptOverride) {
        return execute(agent, llmInstance, history, systemPromptOverride, null, null);
    }

    /**
     * Run an agent-driven chat. The {@code systemPromptOverride}, when
     * provided, replaces the agent's own {@code TurAIAgent.getSystemPrompt()}.
     * Use this from RAG flows that need to embed retrieved chunks in the
     * system message.
     *
     * <p>When both {@code conversationId} and {@code flowId} are non-blank
     * and the flow is reachable, the runtime engine augments the system
     * prompt with the current node's contract and advances the persisted
     * state after the LLM responds.
     *
     * @return a single-emission {@link Flux} carrying the assistant response
     *         (matches Spring AI's call-not-stream model so tool calling is
     *         resolved end-to-end before the response is emitted).
     */
    public Flux<ChatResponse> execute(TurAIAgent agent,
            TurLLMInstance llmInstance,
            List<ChatMessageItem> history,
            String systemPromptOverride,
            String conversationId,
            String flowId) {

        // Per-turn timing markers. Logged as "[AgentExec][timing] ..." so a
        // single grep across the log surfaces a chat-turn latency breakdown
        // (setup / llm / post). Used by TurAgentChatLatencyIT and field
        // diagnostics. Sub-stages of setup are captured separately so we can
        // tell apart "everything cached" (~100ms) from a cold-start MCP
        // handshake / first-time flow load (multi-second).
        final long tStart = System.currentTimeMillis();

        String decryptedApiKey = turSecretCryptoService.decrypt(llmInstance.getApiKeyEncrypted());
        final long tAfterDecrypt = System.currentTimeMillis();

        ChatModel chatModel = llmModelFactory.createChatModel(llmInstance, decryptedApiKey);
        final long tAfterChatModel = System.currentTimeMillis();

        // Note (§I.5 step 2 / T10): persona, tool callbacks, and chat
        // options are NOT resolved here — they are deferred to AFTER the
        // flow context (and possible early advance) is established. This
        // lets a single {@code personaResolver.resolve(...)} call see the
        // post-walk {@code __activePersonaId} the engine wrote during
        // {@code loadOrInitState}'s transparent walk (legacy path) or the
        // T9 early advance (inverted path) — collapsing the previous 2-3
        // resolutions per turn down to ONE. Tool callbacks built off that
        // single persona are correctly aligned with the voice that will
        // compose the prompt and the regen.

        String baseSystemPrompt = resolveSystemPrompt(agent, systemPromptOverride);
        String lastUserMessageEarly = lastMessageOfRole(history, "user");
        FlowContext initialFlowContext = resolveFlowContext(agent, conversationId, flowId,
                lastUserMessageEarly, chatModel);
        // §I.5 step 1 inversion: when the inverted-harness flag is on AND a
        // flow is governing this turn (and the turn is NOT the freshly-
        // triggered activation turn — that one needs the legacy skip path
        // because the user msg is the trigger, not data the strategy can
        // force-capture as the first slot value), run advance(...) NOW —
        // before the LLM call — passing assistantMessage=null. The strategy
        // decides the cursor purely from the user message; the LLM then
        // composes ONE reply with the post-advance node already baked into
        // the system prompt. No regen, no anchoring patches.
        //
        // {@code flowContext} is single-assignment because the downstream
        // {@code Mono.fromCallable(...)} captures it — Java requires
        // effective-finalness for lambda-captured locals. The helper packs
        // the conditional advance + graph re-resolve so the assignment fits
        // a ternary.
        //
        // Post-T16: invert-or-not is no longer a runtime knob. The ONLY
        // condition under which we skip the early advance is the
        // freshly-triggered activation turn — the user msg is the trigger,
        // not data; advance() would force-capture it as the first slot.
        final boolean runEarlyAdvance = initialFlowContext != null
                && !initialFlowContext.freshlyTriggered();
        final FlowContext flowContext = runEarlyAdvance
                ? earlyAdvanceBeforeLlm(initialFlowContext, lastUserMessageEarly, chatModel)
                : initialFlowContext;
        final long tAfterFlow = System.currentTimeMillis();

        // §I.5 step 2 / T10: SINGLE persona resolution per turn — runs
        // AFTER flow context resolution (and after early advance on the
        // inverted path). By the time this fires:
        //   • Legacy path: loadOrInitState's transparent walk has parked
        //     the cursor on the entry interactive node, possibly setting
        //     {@code __activePersonaId} via a persona-* node along the way.
        //   • Inverted path: earlyAdvanceForInvertedHarness has additionally
        //     run the strategy's advance, walking through any post-cursor
        //     persona-* nodes. The cursor is FINAL pre-LLM.
        // Either way, a single {@code personaResolver.resolve(...)} now
        // returns the post-walk persona — collapsing the historic 2-3
        // resolutions down to 1. Patches #1 and #2 of the §I.5 inventory
        // disappear by construction.
        //
        // Both the legacy 2nd-resolution conditional ({@code flowContext != null
        // ? resolve : activePersona}) and the {@code activePersona} pre-flow
        // variable are gone — only the regen block's 3rd resolution remains,
        // and that itself disappears in T12 along with regen.
        final TurPersona activePersona = personaResolver.resolve(agent, conversationId);
        final long tAfterPersona = System.currentTimeMillis();

        ToolCallback[] callbacks = resolveToolCallbacks(agent, activePersona);
        final long tAfterTools = System.currentTimeMillis();
        log.info("[AgentExec] Agent '{}': {} tool callbacks", agent.getTitle(), callbacks.length);
        if (log.isDebugEnabled()) {
            for (ToolCallback cb : callbacks) {
                log.debug("[AgentExec] Available tool: '{}'", cb.getToolDefinition().name());
            }
        }

        // Publish per-turn context into the tool context map so Custom
        // Tool Groovy scripts (via the `slots`/`code` helpers) see it on
        // every invocation:
        //   - conversationId  → drives slot read/write through TurChatFlowEngineService
        //   - agentPythonReqs → unioned with GLOBAL_PYTHON_REQUIREMENTS by
        //                       the dep service before pip install
        // Map.of() doesn't accept nulls — build with a LinkedHashMap so
        // we can skip entries cleanly when their source value is absent.
        var toolContextMap = new java.util.LinkedHashMap<String, Object>();
        if (conversationId != null && !conversationId.isBlank()) {
            toolContextMap.put(
                    com.viglet.turing.genai.tool.TurCustomToolCallbackService.TOOL_CONTEXT_CONVERSATION_ID,
                    conversationId);
        }
        // agentId — drives tenant-isolated Code Interpreter session dirs
        // (tenants/{agentId}/{conversationId}/...). Always published when
        // the agent has an id (every persisted agent does).
        if (agent.getId() != null && !agent.getId().isBlank()) {
            toolContextMap.put(
                    com.viglet.turing.genai.tool.TurCustomToolCallbackService.TOOL_CONTEXT_AGENT_ID,
                    agent.getId());
        }
        String agentPythonRequirements = agent.getPythonRequirements();
        if (agentPythonRequirements != null && !agentPythonRequirements.isBlank()) {
            toolContextMap.put(
                    com.viglet.turing.genai.tool.TurCustomToolCallbackService.TOOL_CONTEXT_AGENT_PYTHON_REQUIREMENTS,
                    agentPythonRequirements);
        }
        var chatOptionsBuilder = DefaultToolCallingChatOptions.builder()
                .toolCallbacks(callbacks)
                .internalToolExecutionEnabled(true);
        if (!toolContextMap.isEmpty()) {
            chatOptionsBuilder.toolContext(toolContextMap);
        }
        var chatOptions = chatOptionsBuilder.build();

        // The single persona resolution above is the canonical voice for
        // the whole turn — alias for downstream readability.
        final TurPersona effectivePersona = activePersona;
        String flowAugmented = flowContext == null
                ? baseSystemPrompt
                : baseSystemPrompt + chatFlowEngineService.buildSystemPromptAddendum(
                        flowContext.flow(), flowContext.state(), flowContext.graph());
        // Fuse the persona on top of everything: its instruction + style
        // guidelines + vocabulary constraints + brand-context hint +
        // few-shot examples wrap the (already RAG-augmented) base prompt.
        // No-op when effectivePersona is null.
        String resolvedSystemPrompt = personaPromptComposer.compose(
                effectivePersona,
                agent.getTurEmbeddingModelInstance(),
                lastUserMessageEarly,
                flowAugmented);
        final long tAfterCompose = System.currentTimeMillis();

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(resolvedSystemPrompt));
        for (ChatMessageItem item : history) {
            String content = item.content() == null ? "" : item.content();
            if ("assistant".equals(item.role())) {
                messages.add(new AssistantMessage(content));
            } else {
                messages.add(new UserMessage(content));
            }
        }

        // Strip the toolset from the INITIAL LLM call too when the active
        // node's aiInstruction explicitly forbids tool calls (e.g. the
        // {@code ai-coupon-code} node which only asks "qual é o código?").
        // Without this, gpt-4o-mini frequently called
        // {@code compor_proposta_in_company} preemptively in the initial
        // call BEFORE {@link LlmJudgeGuardrailStrategy} could capture the
        // coupon code, generating a proposta without the coupon and
        // publishing it via SSE. The downstream regen for
        // {@code ai-deliver-proposta} then ran the tool AGAIN with the
        // freshly-captured coupon, re-writing the slot — the visitor
        // saw the card flash (no-coupon → with-coupon).
        // Skipping tools here means the initial call only produces an
        // acknowledgment text; tools fire once on the regen, with the
        // coupon already captured, and the card renders correctly the
        // first time.
        ChatFlowNode activeNode = flowContext == null ? null
                : flowContext.graph().nodeById(flowContext.state().getCurrentNodeId()).orElse(null);
        // §I.5 step 3 / T11: use the node-aware overload so the declarative
        // ChatFlowNode.toolsEnabled field wins when set; otherwise falls
        // back to the legacy bilingual substring scan on aiInstruction.
        boolean activeNodeForbidsTools = forbidsToolCalls(activeNode);
        org.springframework.ai.chat.prompt.ChatOptions initialOptions = activeNodeForbidsTools
                ? DefaultToolCallingChatOptions.builder()
                        .toolCallbacks(List.<ToolCallback>of())
                        .internalToolExecutionEnabled(false)
                        .build()
                : chatOptions;
        if (activeNodeForbidsTools) {
            log.info("[AgentExec] Initial call on node '{}' running tool-free (aiInstruction forbids tools)",
                    activeNode.id());
        }
        Prompt prompt = new Prompt(messages, initialOptions);
        String username = resolveUsername();
        // Reuse the value already computed for the router; both paths need it.
        final String lastUserMessage = lastUserMessageEarly;

        // Open the analytics session record on the first turn. Idempotent —
        // subsequent turns of the same conversation no-op past the upsert.
        recordSessionStartSafely(agent, effectivePersona, llmInstance,
                conversationId, lastUserMessage, username);

        final long tSetupEnd = System.currentTimeMillis();
        chatPipelineObservation.recordMillis(TurMeterNames.STAGE_CHAT_SETUP, tSetupEnd - tStart);

        // STREAM mode: forward each token chunk to the SSE pipeline as it
        // arrives so the bubble fills in token-by-token. We only take this
        // branch when there is NO flow context — flow-driven turns need
        // the full reply text before advance() / regenerateReplyForNewNode
        // can decide whether to rewrite the bubble, and there is no clean
        // way to "un-emit" a token we already sent to the browser. Per
        // the IV.1 design sketch this fallback is intentional and the
        // TurAgentChatMode docstring documents it.
        //
        // Tone validation is also skipped on this path: TurPersonaToneValidator
        // returns a sanitizedText that may differ from what was streamed —
        // we can't retro-sanitize tokens already on the wire. Agents that
        // rely on persona forbidden-term enforcement should stay on CALL.
        boolean useStreaming = agent.getChatMode() == TurAgentChatMode.STREAM
                && flowContext == null;
        if (useStreaming) {
            log.info("[AgentExec] STREAM mode for agent '{}' (no flow) — forwarding token chunks to SSE",
                    agent.getTitle());
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
            // chunks for a typical reply). Logged at INFO on completion so
            // operators don't need to enable DEBUG to verify streaming health.
            //
            // Two first-chunk timers isolate WHERE buffering happens:
            //   firstRawAt   — instant the FIRST raw Spring AI ChatResponse
            //                  arrives at our subscriber (i.e. left
            //                  chatModel.stream() and the resilience wrap).
            //                  If this ≈ llm_ms, the OpenAI SDK / Spring AI
            //                  is buffering — nothing we do in this pipeline
            //                  will help; need to swap the transport.
            //   firstChunkAt — instant the FIRST non-empty CONTENT chunk
            //                  reaches the SSE emit point (after map+filter).
            //                  If much later than firstRawAt, our filter is
            //                  dropping a long preamble of metadata-only chunks.
            final java.util.concurrent.atomic.AtomicInteger chunkCount =
                    new java.util.concurrent.atomic.AtomicInteger(0);
            final java.util.concurrent.atomic.AtomicInteger rawCount =
                    new java.util.concurrent.atomic.AtomicInteger(0);
            final AtomicReference<Long> firstChunkAt = new AtomicReference<>();
            final AtomicReference<Long> firstRawAt = new AtomicReference<>();
            return chatModel.stream(streamPrompt)
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
        // Cold-start (first turn after restart) typically spends seconds in
        // tools (MCP handshake) or chat_model (TLS to OpenAI). Subsequent
        // turns hit caches and stay under ~100ms. Each delta is one phase:
        //   decrypt    — TurSecretCryptoService.decrypt (AES, ~ms)
        //   chatModel  — llmModelFactory.createChatModel (HTTP client init)
        //   flow       — resolveFlowContext (chat flow load + state init,
        //                plus T9's earlyAdvanceForInvertedHarness on the
        //                inverted path)
        //   persona    — personaResolver.resolve (SINGLE resolution per turn
        //                post §I.5 step 2; reads post-walk __activePersonaId)
        //   tools      — resolveToolCallbacks (MCP handshake, custom tools)
        //   compose    — personaPromptComposer.compose (RAG prefix, prompts)
        //   session    — recordSessionStartSafely (analytics upsert)
        log.info("[AgentExec][timing] setup_ms={} "
                + "(decrypt={} chatModel={} flow={} persona={} tools={} compose={} session={})",
                tSetupEnd - tStart,
                tAfterDecrypt - tStart,
                tAfterChatModel - tAfterDecrypt,
                tAfterFlow - tAfterChatModel,
                tAfterPersona - tAfterFlow,
                tAfterTools - tAfterPersona,
                tAfterCompose - tAfterTools,
                tSetupEnd - tAfterCompose);

        return Mono.fromCallable(() -> {
            // When the flow is freshly triggered, the regen path below ALWAYS
            // overwrites `text` with the flow-aware reply — the initial LLM
            // call's output is unconditionally discarded. Skip it entirely to
            // save the ~1.5-2s of that wasted round-trip. Same reasoning the
            // original comment block on the freshlyTriggered branch laid out
            // Post-T16: single LLM call per turn. The system prompt was
            // composed in setup with the post-advance node already in
            // context — no need to skip the call on freshly-triggered
            // turns (the engine's loadOrInitState parked the cursor on
            // the entry interactive node, and the prompt reflects it).
            String text = "";
            long tLlmStart = System.currentTimeMillis();
            log.info("[AgentExec] Calling LLM for agent '{}', LLM '{}'",
                    agent.getTitle(), llmInstance.getTitle());
            org.springframework.ai.chat.model.ChatResponse response = chatModel.call(prompt);
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
            // the setup phase BEFORE the LLM call (see
            // {@link #earlyAdvanceForInvertedHarness}), so {@code flowContext}
            // already reflects the post-advance cursor and the LLM has
            // composed {@code text} with the correct node in its system
            // prompt. Nothing to do at the post-LLM phase — no second
            // advance, no regen. Patches #6, #7, #8, #11, #14, #15 are
            // gone by construction.
            //
            // textAlignedWithCurrentNode is unconditionally true: the
            // initial LLM call composed text with the post-advance node,
            // so chip suggestions pulled from the same node match.
            TurChatFlowState newState = flowContext == null ? null : flowContext.state();
            boolean textAlignedWithCurrentNode = true;
            // Kept at 0 — advance() ran in the setup phase, no post-LLM
            // advance / regen substages exist in the inverted harness.
            // Preserved as variables so the existing timing log line in
            // [AgentExec][timing] keeps its shape (advance_ms / regen_ms
            // columns are still emitted but always 0 after T16).
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
            //
            // Suppressed when `text` was authored for a different node than
            // the post-advance state — otherwise the user sees a question
            // from node N with chips from node N+1 (e.g. the "objetivo"
            // question rendered with the 4 area chips below it). One-turn
            // chip delay > permanent UX confusion.
            List<String> suggestedOptions = List.of();
            if (textAlignedWithCurrentNode && flowContext != null && newState != null) {
                ChatFlowGraph graph = flowContext.graph();
                ChatFlowNode current = graph.nodeById(newState.getCurrentNodeId()).orElse(null);
                suggestedOptions = ChatFlowOps.suggestedOptions(graph, current);
            }
            long tTotal = System.currentTimeMillis() - tStart;
            // Post = total - setup - llm_call. Captures the time AFTER the LLM
            // returns: flow advance, regen LLM round-trip (if any), token
            // usage record, analytics. Calling it "post" lets the breakdown
            // assert against pure post-processing without the LLM round-trip
            // muddying the number.
            long tPost = tTotal - (tSetupEnd - tStart) - (tLlmEnd - tLlmStart);
            chatPipelineObservation.recordMillis(TurMeterNames.STAGE_CHAT_POST, tPost);
            chatPipelineObservation.recordMillis(TurMeterNames.STAGE_CHAT_TOTAL, tTotal);
            chatPipelineObservation.recordMillis(TurMeterNames.STAGE_CHAT_ADVANCE, tAdvanceMs);
            chatPipelineObservation.recordMillis(TurMeterNames.STAGE_CHAT_REGEN, tRegenMs);
            log.info("[AgentExec][timing] total_ms={} setup_ms={} llm_ms={} post_ms={} (advance_ms={} regen_ms={})",
                    tTotal, tSetupEnd - tStart, tLlmEnd - tLlmStart, tPost,
                    tAdvanceMs, tRegenMs);
            return new AssistantTurn(text, suggestedOptions);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(err -> log.error("[AgentExec] Agent '{}' error: {}",
                        agent.getTitle(), err.getMessage(), err))
                .flatMapMany(TurAgentChatExecutor::emitTurn);
    }

    /**
     * Single assistant turn: the final text plus any chips that should appear underneath.
     * Internal record — never crosses the controller boundary; {@link #emitTurn(AssistantTurn)}
     * fans it out into one or two {@link ChatResponse} events.
     */
    private record AssistantTurn(String text, List<String> options) {
    }

    /**
     * Fans an {@link AssistantTurn} out into the SSE stream: always one {@code "token"} event
     * with the text (when non-empty), optionally followed by one {@code "options"} event whose
     * {@code content} is the JSON-encoded chip labels.
     */
    private static Flux<ChatResponse> emitTurn(AssistantTurn turn) {
        Flux<ChatResponse> assistantText = turn.text() == null || turn.text().isEmpty()
                ? Flux.empty()
                : Flux.just(new ChatResponse("assistant", turn.text()));
        if (turn.options() == null || turn.options().isEmpty()) {
            return assistantText;
        }
        String optionsJson;
        try {
            optionsJson = OBJECT_MAPPER.writeValueAsString(turn.options());
        } catch (JacksonException e) {
            log.warn("[AgentExec] could not serialize {} suggested option(s): {}",
                    turn.options().size(), e.getMessage());
            return assistantText;
        }
        return assistantText.concatWith(
                Flux.just(new ChatResponse("assistant", optionsJson, "options")));
    }

    /**
     * §I.5 step 3 / T11 — declarative tool-strip check. The node author
     * sets {@link ChatFlowNode#toolsEnabled()} to {@code Boolean.FALSE} to
     * strip tool callbacks on the LLM call against this node. {@code null}
     * or {@code TRUE} = tools allowed (default).
     *
     * <p>The legacy {@code forbidsToolCalls(String)} substring matcher
     * (patches #3/#4/#5 — bilingual sentinels in {@code aiInstruction})
     * was deleted in T16 along with {@code regenerateReplyForNewNode},
     * {@code buildRegenOverride}, {@code trimToRecentTurns}, and
     * {@code REGEN_HISTORY_TURN_BUDGET}. Authors of pre-T16 flows that
     * still rely on the sentinel must migrate to the declarative field.
     *
     * @since 2026.2.7
     */
    static boolean forbidsToolCalls(ChatFlowNode node) {
        return node != null && Boolean.FALSE.equals(node.toolsEnabled());
    }

    /**
     * Best-effort open of the analytics session. Anything that throws here
     * is swallowed — telemetry must never break the chat itself.
     */
    private void recordSessionStartSafely(TurAIAgent agent, TurPersona activePersona,
            TurLLMInstance llmInstance,
            String conversationId, String firstUserMessage, String username) {
        if (conversationId == null || conversationId.isBlank()) return;
        if (chatAnalyticsService == null || !chatAnalyticsService.isEnabled()) return;
        try {
            chatAnalyticsService.recordSessionStart(
                    conversationId,
                    agent.getId(),
                    activePersona == null ? null : activePersona.getId(),
                    llmInstance == null ? null : llmInstance.getId(),
                    agent.getTurEmbeddingModelInstance() == null
                            ? null : agent.getTurEmbeddingModelInstance().getId(),
                    agent.getTurStoreInstance() == null
                            ? null : agent.getTurStoreInstance().getId(),
                    username,
                    null,
                    firstUserMessage);
        } catch (RuntimeException e) {
            log.debug("[AgentExec] analytics start failed: {}", e.getMessage());
        }
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
                Integer inT  = response.getMetadata().getUsage().getPromptTokens();
                Integer outT = response.getMetadata().getUsage().getCompletionTokens();
                if (inT  != null) tokensIn  = inT.longValue();
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

    private static String resolveSystemPrompt(TurAIAgent agent, String systemPromptOverride) {
        if (systemPromptOverride != null && !systemPromptOverride.isBlank()) {
            return systemPromptOverride;
        }
        if (agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank()) {
            return agent.getSystemPrompt();
        }
        return DEFAULT_SYSTEM_PROMPT;
    }

    /**
     * Resolves which flow (if any) should govern this chat turn, with three
     * paths:
     *
     * <ul>
     *   <li>{@code flowId == FLOW_ID_NONE}: user explicitly opted out in the
     *       dropdown → no flow runs and the router is skipped.</li>
     *   <li>{@code flowId != null} and not blank: forced selection — used to
     *       simulate a specific path. Loads the flow and inits state.</li>
     *   <li>{@code flowId == null/blank}: auto mode — the engine's router
     *       picks a flow based on each flow's {@code triggerDescription},
     *       continues an in-progress flow if any, or returns no selection.</li>
     * </ul>
     *
     * Returns null in every case where no flow should be applied.
     */
    private FlowContext resolveFlowContext(TurAIAgent agent,
            String conversationId,
            String flowId,
            String lastUserMessage,
            ChatModel routerModel) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        if (FLOW_ID_NONE.equals(flowId)) {
            return null;
        }
        if (flowId != null && !flowId.isBlank()) {
            return resolveForcedFlow(agent, conversationId, flowId);
        }
        // Auto mode: ask the engine's router which flow (if any) fits.
        return chatFlowEngineService
                .selectActiveFlow(agent, conversationId, lastUserMessage, routerModel)
                .map(s -> new FlowContext(s.flow(), s.graph(), s.state(), s.freshlyTriggered()))
                .orElse(null);
    }

    /**
     * §I.5 step 1 — runs the flow engine's {@code advance(...)} BEFORE the
     * initial LLM call with {@code assistantMessage = null} and returns a
     * fresh {@link FlowContext} pinned to the post-advance state. The
     * graph is re-resolved when {@code advance} descended into a sub-flow
     * so downstream composition reads the correct interactive node.
     *
     * <p>Defensive: when {@code advance} returns a null/empty state (no
     * cursor change, no strategy override) the original context is
     * returned unchanged.
     *
     * <p>Caller has already verified
     * {@code initialFlowContext != null && !initialFlowContext.freshlyTriggered()};
     * this helper does not re-check those preconditions.
     *
     * @since 2026.2.7
     */
    private FlowContext earlyAdvanceBeforeLlm(FlowContext initial,
            String userMessage,
            ChatModel auxiliaryModel) {
        log.info("[AgentExec] Running advance(flow={}, userMsg) BEFORE LLM",
                initial.flow().getId());
        var earlyAdvance = chatFlowEngineService.advance(initial.flow(),
                initial.state(), initial.graph(),
                userMessage, /* assistantMessage = */ null, auxiliaryModel);
        TurChatFlowState newState = earlyAdvance.state();
        if (newState == null || newState.getFlow() == null) {
            return initial;
        }
        ChatFlowGraph newGraph = newState.getFlow().getId() != null
                && newState.getFlow().getId().equals(initial.flow().getId())
                        ? initial.graph()
                        : chatFlowEngineService.parseGraph(newState.getFlow())
                                .orElse(initial.graph());
        return new FlowContext(newState.getFlow(), newGraph, newState,
                initial.freshlyTriggered());
    }

    private FlowContext resolveForcedFlow(TurAIAgent agent, String conversationId, String flowId) {
        Optional<TurChatFlow> flowOpt = chatFlowRepository.findById(flowId);
        if (flowOpt.isEmpty()) {
            log.warn("[AgentExec] Chat flow '{}' not found", flowId);
            return null;
        }
        TurChatFlow flow = flowOpt.get();
        // Defense in depth: a flow only governs chats for its owning agent.
        if (flow.getTurAIAgent() == null || !agent.getId().equals(flow.getTurAIAgent().getId())) {
            log.warn("[AgentExec] Flow '{}' does not belong to agent '{}'", flowId, agent.getId());
            return null;
        }
        if (flow.getEnabled() != 1) {
            log.info("[AgentExec] Flow '{}' is disabled — ignoring", flowId);
            return null;
        }
        Optional<ChatFlowGraph> graphOpt = chatFlowEngineService.parseGraph(flow);
        if (graphOpt.isEmpty()) {
            log.warn("[AgentExec] Flow '{}' has no parseable graph — ignoring", flowId);
            return null;
        }
        Optional<TurChatFlowState> stateOpt = chatFlowEngineService.loadOrInitState(
                conversationId, flow, graphOpt.get());
        if (stateOpt.isEmpty()) {
            log.warn("[AgentExec] Could not initialize flow state for '{}'/'{}'",
                    conversationId, flowId);
            return null;
        }
        // The state may have descended into a sub-flow (the forced flow's
        // first interactive node was a Sub Flow node, or a previous turn
        // already pushed onto the chain). Align FlowContext with the leaf
        // so the addendum and strategy run against the correct graph.
        TurChatFlowState activeState = stateOpt.get();
        TurChatFlow activeFlow = activeState.getFlow();
        ChatFlowGraph activeGraph = activeFlow.getId().equals(flow.getId())
                ? graphOpt.get()
                : chatFlowEngineService.parseGraph(activeFlow).orElse(graphOpt.get());
        // Forced flow: the operator explicitly pinned this flow via the
        // flowId param. Treat as continuation — the user's msg gets
        // processed by LlmJudge against the current node. (If this turns
        // out to also need the trigger-turn skip, can be threaded later.)
        return new FlowContext(activeFlow, activeGraph, activeState, false);
    }

    private static String lastMessageOfRole(List<ChatMessageItem> history, String role) {
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessageItem item = history.get(i);
            if (role.equals(item.role())) {
                return item.content() == null ? "" : item.content();
            }
        }
        return "";
    }

    // §I.5 step 4 / T16 — deleted: REGEN_HISTORY_TURN_BUDGET (patch #14),
    // buildRegenOverride(ChatFlowNode) (patches #10/#12/#13), and
    // trimToRecentTurns(List, int) (patch #14). All three existed only to
    // support regenerateReplyForNewNode, which itself was deleted earlier
    // in T16. Recoverable from git log on pre-T16 commits.

    /**
     * Resolved per-turn flow runtime — group of values that always travel
     * together. {@code freshlyTriggered} mirrors
     * {@link com.viglet.turing.genai.flow.TurChatFlowEngineService.FlowSelection#freshlyTriggered}:
     * true only on the very turn that activated the flow (user msg = trigger,
     * not data). The executor uses this to skip the LlmJudge advance pass
     * so the trigger text doesn't get force-captured as the first slot.
     */
    private record FlowContext(TurChatFlow flow, ChatFlowGraph graph, TurChatFlowState state,
            boolean freshlyTriggered) {
    }

    private static Set<String> parseNativeTools(String nativeTools) {
        if (nativeTools == null || nativeTools.isBlank()) {
            return new HashSet<>();
        }
        Set<String> result = new HashSet<>();
        for (String tool : nativeTools.split(",")) {
            String trimmed = tool.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }
}
