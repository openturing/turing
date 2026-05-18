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
            TurChatMemoryService chatMemoryService) {
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

        String decryptedApiKey = turSecretCryptoService.decrypt(llmInstance.getApiKeyEncrypted());
        ChatModel chatModel = llmModelFactory.createChatModel(llmInstance, decryptedApiKey);

        // Resolve the active persona ONCE per turn — flow overrides may
        // already be in the conversation's flow state. Passed to every
        // downstream consumer (tool callbacks, prompt composer, tone
        // validator, analytics) so the same voice governs the whole turn.
        final TurPersona activePersona = personaResolver.resolve(agent, conversationId);

        ToolCallback[] callbacks = resolveToolCallbacks(agent, activePersona);
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

        String baseSystemPrompt = resolveSystemPrompt(agent, systemPromptOverride);
        String lastUserMessageEarly = lastMessageOfRole(history, "user");
        FlowContext flowContext = resolveFlowContext(agent, conversationId, flowId,
                lastUserMessageEarly, chatModel);
        // Re-resolve the persona AFTER flow selection. The first call at line
        // ~240 happened before any flow ran — on a freshly-triggered flow the
        // engine's transparent walk traversed a `persona-*` node that wrote
        // {@code __activePersonaId} onto the flow state, switching the active
        // voice (e.g. in-company router lands on the Camila node). Without
        // this re-resolve the regen below still uses the agent's default
        // persona (Marina), so the visitor sees "Sou a Marina" intro on the
        // first turn of an in-company conversation while every subsequent
        // turn correctly speaks as Camila.
        final TurPersona effectivePersona = flowContext != null
                ? personaResolver.resolve(agent, conversationId)
                : activePersona;
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

        Prompt prompt = new Prompt(messages, chatOptions);
        String username = resolveUsername();
        // Reuse the value already computed for the router; both paths need it.
        final String lastUserMessage = lastUserMessageEarly;

        // Open the analytics session record on the first turn. Idempotent —
        // subsequent turns of the same conversation no-op past the upsert.
        recordSessionStartSafely(agent, effectivePersona, llmInstance,
                conversationId, lastUserMessage, username);

        return Mono.fromCallable(() -> {
            log.info("[AgentExec] Calling LLM for agent '{}', LLM '{}'",
                    agent.getTitle(), llmInstance.getTitle());
            var response = chatModel.call(prompt);

            tokenUsageService.recordUsage(llmInstance, response, username);

            String text = "";
            if (response.getResult() != null
                    && response.getResult().getOutput() != null
                    && response.getResult().getOutput().getText() != null) {
                text = response.getResult().getOutput().getText();
            }
            log.info("[AgentExec] Agent '{}' response: {} chars",
                    agent.getTitle(), text.length());

            TurChatFlowState newState = flowContext == null ? null : flowContext.state();
            // Tracks whether the final `text` was authored for the CURRENT
            // node (post-advance) or is stale (authored for the OLD node).
            // Drives chip suppression at the end of this turn — chips for a
            // node the user hasn't seen the question for create the demo
            // bug where the "objetivo" question shows up with area chips.
            //
            // Truth table:
            //   • No flow context → trivially aligned (no chips anyway)
            //   • Engine didn't advance → text is for original=current node ✓
            //   • Engine advanced + LlmJudge override → text is for OLD node
            //     (a redirect_message to the user); chips would mismatch.
            //   • Engine advanced + regen succeeded → text rewritten for new
            //     node by {@link #regenerateReplyForNewNode}; chips align ✓
            //   • Engine advanced + regen returned null → text still from
            //     the initial LLM call (for old node); chips would mismatch.
            boolean textAlignedWithCurrentNode = true;
            if (flowContext != null) {
                if (flowContext.freshlyTriggered()) {
                    // Trigger turn: user's msg is the activation signal, NOT
                    // input for the first interactive node. Skip the LlmJudge
                    // advance entirely (would force-capture the trigger text
                    // as the first slot value — e.g. name = "Plano de
                    // carreira pessoal"). Force regen so the user sees the
                    // first node's question this very turn, instead of the
                    // initial LLM reply that was generated WITHOUT the new
                    // flow's addendum in context.
                    log.info("[AgentExec] Flow freshly triggered for conversation '{}' — skipping advance, regenerating for entry node",
                            conversationId);
                    String regenerated = regenerateReplyForNewNode(
                            agent, llmInstance, chatModel, chatOptions,
                            effectivePersona, baseSystemPrompt, history,
                            lastUserMessage, flowContext.state(), flowContext, username);
                    if (regenerated != null) {
                        text = regenerated;
                    }
                    // newState stays as flowContext.state() (initialized).
                    // textAlignedWithCurrentNode stays true (regen targeted
                    // the current/entry node).
                } else {
                    String originalNodeId = flowContext.state().getCurrentNodeId();
                    var advance = chatFlowEngineService.advance(flowContext.flow(),
                            flowContext.state(), flowContext.graph(),
                            lastUserMessage, text, chatModel);
                    // LLM_JUDGE may rewrite the assistant text when it judges the
                    // reply off-topic; HEURISTIC always leaves the override null.
                    if (advance.assistantOverride() != null) {
                        text = advance.assistantOverride();
                    }
                    newState = advance.state();
                    boolean advanced = originalNodeId != null
                            && newState != null
                            && !originalNodeId.equals(newState.getCurrentNodeId());

                    // When advance walked transparently from the current aiQuestion
                    // through switch/condition/slot/persona nodes and parked the
                    // conversation on a NEW aiQuestion, the reply we just got from
                    // the LLM was generated with the OLD node's Goal — so the user
                    // sees an acknowledgment of their last input but never the new
                    // node's content (the "vou montar um plano… um momento" demo
                    // bug). Regenerate the reply with the new node's Goal as the
                    // active step so the user sees the proper response on this
                    // very turn. Skipped when:
                    //   • the strategy already produced an override (LLM_JUDGE
                    //     redirect — that's already targeted at the user);
                    //   • the state did not transition;
                    //   • the new node is not an aiQuestion (start/end/persona/
                    //     slot/switch/condition are transparent or terminal, no
                    //     fresh user-facing prompt to compose).
                    if (advance.assistantOverride() == null && advanced) {
                        // Re-resolve the persona one more time. The transparent
                        // walk inside {@code advance()} may have crossed a
                        // {@code persona-*} node that swapped the active voice
                        // (some flows do this — e.g. a router flow that owns
                        // the front door but hands off to a specialist sub-
                        // persona at the first switch). Using the same lookup
                        // as the initial post-flow resolve keeps both paths
                        // (fresh-trigger and mid-flow advance) consistent.
                        TurPersona regenPersona = personaResolver.resolve(agent, conversationId);
                        if (regenPersona == null) regenPersona = effectivePersona;
                        String regenerated = regenerateReplyForNewNode(
                                agent, llmInstance, chatModel, chatOptions,
                                regenPersona, baseSystemPrompt, history,
                                lastUserMessage, newState, flowContext, username);
                        if (regenerated != null) {
                            text = regenerated;
                        }
                        // Aligned only when regen actually produced new text.
                        textAlignedWithCurrentNode = regenerated != null;
                    } else if (advanced) {
                        // Override path: LlmJudge sent a redirect for the OLD node
                        // while the engine advanced — text and current-node chips
                        // would describe different things.
                        textAlignedWithCurrentNode = false;
                    }
                }
            }

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
     * Second LLM call for the new leaf node after the flow engine's transparent
     * walk moved the conversation past the original aiQuestion. Composes the
     * same prompt shape the first call used (base prompt + flow addendum for
     * the NEW node + persona wrap + full history) and returns the new text.
     *
     * <p>Returns {@code null} when the new leaf node has no aiInstruction (no
     * Goal to address — e.g. an end node a strategy mis-resolved into), when
     * its type is not {@code aiQuestion}, or when the LLM call yields no text.
     * Caller treats {@code null} as "keep the original reply".
     *
     * @since 2026.2.7
     */
    private String regenerateReplyForNewNode(TurAIAgent agent,
            TurLLMInstance llmInstance,
            ChatModel chatModel,
            org.springframework.ai.model.tool.ToolCallingChatOptions chatOptions,
            TurPersona activePersona,
            String baseSystemPrompt,
            List<ChatMessageItem> history,
            String lastUserMessage,
            TurChatFlowState newState,
            FlowContext originalContext,
            String username) {
        // Resolve the graph for the new state's flow — may differ from the
        // original when the walk descended into a sub-flow.
        ChatFlowGraph newGraph = newState.getFlow() != null
                && newState.getFlow().getId() != null
                && newState.getFlow().getId().equals(originalContext.flow().getId())
                        ? originalContext.graph()
                        : chatFlowEngineService.parseGraph(newState.getFlow())
                                .orElse(originalContext.graph());
        ChatFlowNode newNode = newGraph.nodeById(newState.getCurrentNodeId()).orElse(null);
        if (newNode == null || !"aiQuestion".equals(newNode.type())) {
            return null;
        }
        if (newNode.aiInstruction() == null || newNode.aiInstruction().isBlank()) {
            return null;
        }

        // Regen-specific addendum: NO "Next step preview" hint. The hint is
        // designed for the pre-LLM call where the bot can capture + chain
        // into the next question in one reply. In the regen path, the user's
        // last message has already been captured by the judge and the flow
        // is parked on the NEW current node — exposing the next node's
        // instruction here makes the LLM ask one step ahead, producing the
        // chip-vs-question mismatch we kept seeing in the in-company flow
        // ("bot asks setor question, chips are num-pessoas", etc.).
        String newAddendum = ChatFlowOps.buildBaseAddendum(
                newNode, newState, newGraph, /* includeNextStepHint */ false);
        // Highest-priority override appended AFTER the persona-composed
        // prompt. Empirically the regen LLM tends to anchor on the
        // conversational pattern in history ("we've been asking about
        // cargo/objetivo, let's keep doing that") and quietly ignore the
        // new node's aiInstruction baked into newAddendum. Explicit
        // directive — placed last so it's the freshest tokens before the
        // user message — flips compliance in the cases we've seen fail.
        // Bilingual on purpose: persona may run in PT or EN; either way
        // the directive is unambiguous.
        String regenOverride = buildRegenOverride(newNode);
        // Deliberately drop `baseSystemPrompt` from the regen call. That
        // prompt contains the agent's orchestration rules ("show 3 caminhos
        // welcome on first turn", "ROUTER LLM decides flow", etc.) which
        // are correct for the INITIAL LLM call (routing + persona-free
        // greeting) but FIGHT the regen's narrow purpose: produce the
        // current node's question. Observed in production with gpt-4o-mini:
        // even with the regenOverride explicitly forbidding the welcome,
        // the LLM kept emitting it on freshly-triggered flows because the
        // welcome example sits earlier in the prompt and anchors stronger.
        // Persona prompts already carry the role context (e.g. "Você é a
        // Marina, consultora de carreira do Insper EE") and the security
        // rules (NUNCA peça CPF, NUNCA cite concorrentes), so dropping the
        // agent prompt loses no critical guardrail. The flow addendum
        // (`newAddendum`) carries the active node's Goal + Collect — that's
        // all the regen needs.
        String regenSystemPrompt = personaPromptComposer.compose(
                activePersona,
                agent.getTurEmbeddingModelInstance(),
                lastUserMessage,
                newAddendum) + regenOverride;

        // Trim history to the last few turns. Sending the full conversation
        // gave the LLM too many examples of the old-pattern Q&A and it kept
        // re-asking the previous step's question despite the system prompt
        // change. Recent context (3 turns = 6 messages) is enough for the
        // model to know who the visitor is + what they just said, without
        // anchoring on stale patterns.
        List<ChatMessageItem> regenHistory = trimToRecentTurns(history, REGEN_HISTORY_TURN_BUDGET);
        List<Message> regenMessages = new ArrayList<>();
        regenMessages.add(new SystemMessage(regenSystemPrompt));
        for (ChatMessageItem item : regenHistory) {
            String content = item.content() == null ? "" : item.content();
            if ("assistant".equals(item.role())) {
                regenMessages.add(new AssistantMessage(content));
            } else {
                regenMessages.add(new UserMessage(content));
            }
        }

        log.info("[AgentExec] Regenerating reply for new node '{}' (type={}) after transparent walk — history trimmed from {} to {} messages",
                newNode.id(), newNode.type(), history.size(), regenHistory.size());
        Prompt regenPrompt = new Prompt(regenMessages, chatOptions);
        var regenResponse = chatModel.call(regenPrompt);
        tokenUsageService.recordUsage(llmInstance, regenResponse, username);
        if (regenResponse.getResult() == null
                || regenResponse.getResult().getOutput() == null
                || regenResponse.getResult().getOutput().getText() == null) {
            return null;
        }
        String regenText = regenResponse.getResult().getOutput().getText();
        log.info("[AgentExec] Regenerated reply: {} chars", regenText.length());
        return regenText;
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

    /**
     * Number of trailing user turns kept when regenerating the reply for a
     * newly-advanced node. A "turn" here = one user message + its (potentially
     * stale) assistant response. Sending the FULL history during regen gave
     * the LLM too many examples of the old pattern and it kept asking the
     * previous step's question; trimming to the recent slice fixes
     * compliance while preserving "who the visitor is + what they just said".
     */
    private static final int REGEN_HISTORY_TURN_BUDGET = 3;

    /**
     * Bilingual highest-priority override appended after the persona prompt
     * during regen. When the destination node renders chip buttons in the UI,
     * those choices are listed inline so the LLM cannot produce a question
     * incongruent with them (observed gpt-4o-mini failure: a decision-role
     * node with chips [RH/L&D, Gestor, C-level, Comprador] regenerated as
     * "Quantas pessoas participarão?" — the chips render separately, so the
     * visitor saw a num_pessoas question with role chips below it).
     */
    private static String buildRegenOverride(ChatFlowNode newNode) {
        StringBuilder sb = new StringBuilder("""


                === HIGHEST-PRIORITY OVERRIDE / INSTRUÇÃO DE PRIORIDADE MÁXIMA ===
                The conversation has advanced to a NEW step in the flow.
                Your next reply MUST address the CURRENT step's goal. Do NOT repeat
                or rephrase earlier questions. Do NOT continue the conversational
                pattern of older turns. Do NOT skip ahead to a later step. The
                user's last message has ALREADY been captured as the answer to the
                previous step — acknowledge briefly (one short sentence at most)
                and ask the CURRENT step's question.

                A conversa avançou para uma NOVA etapa do flow. Sua próxima resposta
                DEVE abordar a pergunta da etapa atual. NÃO repita nem reformule
                perguntas anteriores. NÃO continue o padrão das mensagens antigas.
                NÃO pule pra uma etapa futura. A mensagem do usuário JÁ FOI capturada
                como resposta da etapa anterior — faça um acknowledge curto (uma
                frase, opcional) e em seguida a pergunta da etapa atual.""");
        // Inline the node's aiInstruction directly in the override. Without
        // this the override only POINTED to a "FLOW STATE / aiInstruction
        // section above" — empirically gpt-4o-mini frequently ignored that
        // pointer and recycled an instruction from a later step it had
        // already seen via the conversational pattern (in-company flow:
        // visitor reported the bot asking the `tema` question while the
        // engine was parked on `ai-num-pessoas`). Re-stating the literal
        // instruction next to the imperative wording flips compliance.
        if (newNode.aiInstruction() != null && !newNode.aiInstruction().isBlank()) {
            sb.append("\n\nCURRENT STEP'S INSTRUCTION (literal — follow this, not any earlier step's):\n")
              .append(newNode.aiInstruction().trim());
        }
        List<String> chips = newNode.inlineOptions();
        if (chips != null && !chips.isEmpty()) {
            String joined = String.join(" · ", chips);
            sb.append("\n\nThe UI will render these chip buttons below your reply: ")
              .append(joined)
              .append("\nYour question MUST be congruent with these choices — "
                    + "i.e. a question whose set of natural answers includes all of them. "
                    + "Do NOT mention the chips literally; just ask the question they answer.")
              .append("\n\nA interface vai mostrar estes botões abaixo da sua resposta: ")
              .append(joined)
              .append("\nSua pergunta DEVE ser congruente com essas escolhas — "
                    + "uma pergunta cujas respostas naturais incluam todas elas. "
                    + "NÃO cite os chips literalmente; apenas formule a pergunta que eles respondem.");
        }
        sb.append("\n=== END OVERRIDE / FIM ===");
        return sb.toString();
    }

    /**
     * Returns the trailing portion of {@code history} containing at most
     * {@code maxUserTurns} user messages (plus any assistant messages
     * interleaved with or after them). Preserves chronological order.
     * Defensive vs. malformed history (null items, blank role) — those are
     * passed through; the regen prompt isn't the place to assert message
     * shape, just to limit volume.
     */
    static List<ChatMessageItem> trimToRecentTurns(List<ChatMessageItem> history, int maxUserTurns) {
        if (history == null || history.isEmpty() || maxUserTurns <= 0) {
            return history == null ? List.of() : history;
        }
        int userTurnsSeen = 0;
        int startIdx = history.size();
        // Walk backwards counting user turns; stop at the boundary just
        // before crossing the budget so we keep `maxUserTurns` user messages
        // plus whatever assistant/system items sit between them.
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessageItem item = history.get(i);
            if (item != null && "user".equals(item.role())) {
                userTurnsSeen++;
                if (userTurnsSeen > maxUserTurns) {
                    break;
                }
            }
            startIdx = i;
        }
        if (startIdx == 0) return history;
        return history.subList(startIdx, history.size());
    }

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
