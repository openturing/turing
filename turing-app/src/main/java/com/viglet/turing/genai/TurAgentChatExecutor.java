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
import java.util.Optional;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.persona.TurAgentPersonaResolver;
import com.viglet.turing.genai.persona.TurPersonaModelCalibration;
import com.viglet.turing.observability.TurChatPipelineObservation;
import com.viglet.turing.observability.TurMeterNames;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.service.chatanalytics.TurChatAnalyticsService;
import com.viglet.turing.service.chatanalytics.TurChatCohortResolver;
import com.viglet.turing.service.chatanalytics.TurChatSessionRefs;
import com.viglet.turing.service.chatanalytics.TurChatVisitorContext;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * Orchestrator for agent-driven chat turns. After T33's decomposition the
 * heavy lifting is split across three collaborator beans:
 * <ul>
 *   <li>{@link TurChatToolResolver} — native + MCP + custom + persona-brand
 *       callbacks, pipeline-decorated and BM25 pre-filtered.</li>
 *   <li>{@link TurChatPromptAssembler} — system-prompt composition (RAG +
 *       flow addendum + persona voice) and T30 conversation-relevance
 *       history enrichment.</li>
 *   <li>{@link TurChatStreamingDispatcher} — CALL vs STREAM branch, tone
 *       validation, telemetry, suggested options.</li>
 * </ul>
 *
 * <p>This class keeps:
 * <ul>
 *   <li>The per-turn flow-context resolution + early-advance dance (it
 *       drives the cursor BEFORE the LLM call per §I.5 step 1 / T9).</li>
 *   <li>The single per-turn persona resolution (§I.5 step 2 / T10).</li>
 *   <li>The setup-substage timing markers (decrypt / model / flow / persona)
 *       — the tools and compose substages move with their collaborators.</li>
 *   <li>The session-start analytics upsert (idempotent — fires once per
 *       conversation).</li>
 * </ul>
 *
 * <p>Both the standalone agent chat ({@code /api/v2/ai-agent/{id}/chat}) and
 * the SN site AI Mode chat ({@code /api/sn/{site}/chat/conversation}) call
 * into this — the SN flow simply augments the system prompt with retrieved
 * RAG context before delegating.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@Service
public class TurAgentChatExecutor {

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
    private final TurChatToolResolver toolResolver;
    private final TurChatPromptAssembler promptAssembler;
    private final TurChatStreamingDispatcher streamingDispatcher;
    private final TurTokenBudgetService tokenBudgetService;
    private final TurPromptCompactor promptCompactor;
    private final TurChatFlowRepository chatFlowRepository;
    private final TurChatFlowEngineService chatFlowEngineService;
    private final com.viglet.turing.genai.flow.TurConversationLock conversationLock;
    private final TurAgentPersonaResolver personaResolver;
    private final TurChatAnalyticsService chatAnalyticsService;
    private final TurChatCohortResolver chatCohortResolver;
    private final TurChatPipelineObservation chatPipelineObservation;
    private final com.viglet.turing.service.llm.budget.TurChatCostBudgetGate costBudgetGate;
    private final com.viglet.turing.resilience.llm.TurMetaChatModelResolver metaChatModelResolver;
    private final com.viglet.turing.properties.TurAbuseControlProperty abuseControlProperty;

    public TurAgentChatExecutor(TurLlmModelFactory llmModelFactory,
            TurSecretCryptoService turSecretCryptoService,
            TurChatToolResolver toolResolver,
            TurChatPromptAssembler promptAssembler,
            TurChatStreamingDispatcher streamingDispatcher,
            TurTokenBudgetService tokenBudgetService,
            TurPromptCompactor promptCompactor,
            TurChatFlowRepository chatFlowRepository,
            TurChatFlowEngineService chatFlowEngineService,
            com.viglet.turing.genai.flow.TurConversationLock conversationLock,
            TurAgentPersonaResolver personaResolver,
            TurChatAnalyticsService chatAnalyticsService,
            TurChatCohortResolver chatCohortResolver,
            TurChatPipelineObservation chatPipelineObservation,
            com.viglet.turing.service.llm.budget.TurChatCostBudgetGate costBudgetGate,
            com.viglet.turing.resilience.llm.TurMetaChatModelResolver metaChatModelResolver,
            com.viglet.turing.properties.TurAbuseControlProperty abuseControlProperty) {
        this.llmModelFactory = llmModelFactory;
        this.turSecretCryptoService = turSecretCryptoService;
        this.toolResolver = toolResolver;
        this.promptAssembler = promptAssembler;
        this.streamingDispatcher = streamingDispatcher;
        this.tokenBudgetService = tokenBudgetService;
        this.promptCompactor = promptCompactor;
        this.chatFlowRepository = chatFlowRepository;
        this.chatFlowEngineService = chatFlowEngineService;
        this.conversationLock = conversationLock;
        this.personaResolver = personaResolver;
        this.chatAnalyticsService = chatAnalyticsService;
        this.chatCohortResolver = chatCohortResolver;
        this.chatPipelineObservation = chatPipelineObservation;
        this.costBudgetGate = costBudgetGate;
        this.metaChatModelResolver = metaChatModelResolver;
        this.abuseControlProperty = abuseControlProperty;
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
     * the agent. Delegates to {@link TurChatToolResolver}. The legacy
     * overload (no user message, no pre-filter) is preserved for non-chat
     * surfaces and unit tests that don't have a user message in hand.
     *
     * <p>Production chat paths should prefer
     * {@link #resolveToolCallbacks(TurAIAgent, TurPersona, String, ChatFlowNode)}
     * so the §IV.2 / T29 pre-filter can trim large catalogs.
     */
    public ToolCallback[] resolveToolCallbacks(TurAIAgent agent, TurPersona activePersona) {
        return toolResolver.resolve(agent, activePersona, null, null);
    }

    /**
     * Same as {@link #resolveToolCallbacks(TurAIAgent, TurPersona)} but
     * runs the §IV.2 / T29 BM25 pre-filter against {@code userMessage}
     * before returning. Tools listed in the agent's {@code nativeTools}
     * and on the active node's {@code requiredTools} are always kept
     * regardless of score; everything else is ranked and the top-K
     * survives.
     *
     * @since 2026.3.1
     */
    public ToolCallback[] resolveToolCallbacks(TurAIAgent agent, TurPersona activePersona,
            String userMessage, ChatFlowNode activeNode) {
        return toolResolver.resolve(agent, activePersona, userMessage, activeNode);
    }

    /**
     * §I.5 step 3 / T11 — declarative tool-strip check. Delegates to
     * {@link TurChatToolResolver#forbidsToolCalls(ChatFlowNode)}. Preserved
     * here as a static façade so existing test code that calls
     * {@code TurAgentChatExecutor.forbidsToolCalls(...)} keeps working.
     *
     * @since 2026.2.7
     */
    public static boolean forbidsToolCalls(ChatFlowNode node) {
        return TurChatToolResolver.forbidsToolCalls(node);
    }

    /**
     * Run an agent-driven chat. The {@code request} bundles the agent, LLM,
     * history, optional {@code systemPromptOverride} (RAG flows replace the
     * agent's own prompt to embed retrieved chunks) and the optional
     * conversation/flow ids + visitor attachments — see
     * {@link TurAgentChatRequest}.
     *
     * <p>When both {@code conversationId} and {@code flowId} are non-blank and
     * the flow is reachable, the runtime engine augments the system prompt with
     * the current node's contract and advances the persisted state BEFORE the
     * LLM call (§I.5 step 1 / T9 — inverted harness).
     *
     * @return a single-emission {@link Flux} carrying the assistant response
     *         (matches Spring AI's call-not-stream model so tool calling is
     *         resolved end-to-end before the response is emitted).
     */
    public Flux<ChatResponse> execute(TurAgentChatRequest request) {
        return execute(request, 0);
    }

    /**
     * Overload accepting visitor attachments + a forced A/B variant label
     * (T73 / §VII.8.d) — the entry point the chat REST controller uses when a
     * {@code ?_ab_variant=<label>} hint rode in on the request. Runs at
     * {@code agentInvokeDepth = 0} (a top-level visitor turn). All other
     * behaviour matches the files overload.
     *
     * @since 2026.3.1
     */
    public Flux<ChatResponse> execute(TurAgentChatRequest request, String forcedVariant) {
        return execute(request, 0, forcedVariant);
    }

    /**
     * Overload accepting visitor attachments + a forced A/B variant label + an
     * optional {@code selectedSkillId} skill-mode pin (T325 / §IX.4.d) — the
     * entry point the chat REST controller uses. Runs at
     * {@code agentInvokeDepth = 0} (a top-level visitor turn). A blank
     * {@code selectedSkillId} offers all enabled skills (legacy progressive
     * disclosure); a set value pins the turn to that single skill.
     *
     * @since 2026.3.1
     */
    public Flux<ChatResponse> execute(TurAgentChatRequest request, String forcedVariant,
            String selectedSkillId) {
        return execute(request, 0, forcedVariant, selectedSkillId);
    }

    /**
     * Overload accepting the current {@code agent.invoke(...)} recursion
     * depth. Published into the Spring AI {@code ToolContext} via
     * {@link com.viglet.turing.genai.tool.TurCustomToolCallbackService#TOOL_CONTEXT_AGENT_INVOKE_DEPTH}
     * so a Custom Tool spawned by the LLM in this child turn sees the
     * accurate depth and can short-circuit at the configured cap. Public
     * for use by {@link com.viglet.turing.genai.tool.TurCustomToolAgentHelper}.
     *
     * @param agentInvokeDepth the depth this child turn runs at (caller
     *                         increments by 1 against the parent context).
     * @since 2026.3.1
     */
    public Flux<ChatResponse> execute(TurAgentChatRequest request, int agentInvokeDepth) {
        return execute(request, agentInvokeDepth, null);
    }

    /**
     * Overload accepting a forced A/B variant label (T73 / §VII.8.d).
     * Carries the visitor-supplied {@code ?_ab_variant=<label>} hint down to
     * {@link TurChatFlowEngineService#selectActiveFlow} so QA / sales-demo
     * sessions can preview a specific experiment arm. A blank value is the
     * production default and behaves exactly like the legacy overloads.
     *
     * @param forcedVariant variant label to hard-pin within whatever
     *                      experiment the router selects; null/blank → normal
     *                      sticky assignment.
     * @since 2026.3.1
     */
    public Flux<ChatResponse> execute(TurAgentChatRequest request, int agentInvokeDepth,
            String forcedVariant) {
        return execute(request, agentInvokeDepth, forcedVariant, null);
    }

    /**
     * Deepest overload — additionally accepts an optional {@code selectedSkillId}
     * (T325 / §IX.4.d) that pins this turn to a single skill (the "skill mode" of
     * a multi-skill ZIP). When blank/null the full enabled skill set is offered
     * for progressive disclosure, exactly as the legacy overloads; when set, both
     * the system-prompt skill block and the {@code run_skill} tool are scoped to
     * that one skill. The skill still runs on the Global Settings Default LLM.
     *
     * @param selectedSkillId nullable skill id/name to pin; null/blank → all
     *                        enabled skills (legacy progressive disclosure)
     * @since 2026.3.1
     */
    public Flux<ChatResponse> execute(TurAgentChatRequest request, int agentInvokeDepth,
            String forcedVariant, String selectedSkillId) {
        TurAIAgent agent = request.agent();
        TurLLMInstance llmInstance = request.llmInstance();
        List<ChatMessageItem> history = request.history();
        String systemPromptOverride = request.systemPromptOverride();
        String conversationId = request.conversationId();
        String flowId = request.flowId();
        List<MultipartFile> files = request.files();

        // Per-turn timing markers. Logged as "[AgentExec][timing] ..." so a
        // single grep across the log surfaces a chat-turn latency breakdown
        // (setup / llm / post). Sub-stages are also fanned out into the
        // Micrometer timer registry — DECRYPT/MODEL/FLOW/PERSONA are owned
        // here; TOOLS/COMPOSE are recorded inside the collaborators.
        final long tStart = System.currentTimeMillis();

        // T291 / §XVI.3 (Block L) — turn-time soft budget gate. When the agent's
        // month-to-date spend has reached its monthlyBudgetUsd cap and a cheaper
        // downgrade LLM is configured, swap to it BEFORE decrypt/createChatModel
        // so the cheaper provider's key + client are the ones built. Soft: never
        // aborts the turn; warn-only when no downgrade is set. No-op (returns the
        // same instance) when the gate is disabled — the common case.
        llmInstance = costBudgetGate.resolveInstance(agent, llmInstance);

        String decryptedApiKey = turSecretCryptoService.decrypt(llmInstance.getApiKeyEncrypted());
        final long tAfterDecrypt = System.currentTimeMillis();

        // T518 — wrap the (budget-resolved) primary in the cost-aware
        // cross-provider fallback chain when one is configured; a no-op pass-
        // through when the chain is empty (legacy single-model path).
        ChatModel chatModel = metaChatModelResolver.wrapWithFallback(llmInstance,
                llmModelFactory.createChatModel(llmInstance, decryptedApiKey));
        final long tAfterChatModel = System.currentTimeMillis();

        // Note (§I.5 step 2 / T10): persona, tool callbacks, and chat
        // options are NOT resolved here — they are deferred to AFTER the
        // flow context (and possible early advance) is established. This
        // lets a single {@code personaResolver.resolve(...)} call see the
        // post-walk {@code __activePersonaId} the engine wrote during
        // {@code loadOrInitState}'s transparent walk or the T9 early
        // advance — collapsing the previous 2-3 resolutions per turn down
        // to ONE.
        String baseSystemPrompt = resolveSystemPrompt(agent, systemPromptOverride);
        String lastUserMessageEarly = lastMessageOfRole(history, "user");
        // Flow detection + pre-LLM advance run as ONE per-conversation critical
        // section so a concurrent side-channel write on the same conversation
        // (POST /chat/slots, /form-submit, /flow-select, /chat/resume) cannot
        // interleave and lost-update the cursor. Without this, an optimistic
        // slot write firing alongside the chat send could revert the advance
        // and drop the visitor's answer (the turn then fell through to RAG).
        // The lock is released here — BEFORE the LLM call — so the long-running
        // model stream is never serialized; only the fast state mutation is.
        // §I.5 step 1 inversion: when a flow governs this turn (and it is NOT
        // the freshly-triggered activation turn — that one needs the legacy
        // skip path because the user msg is the trigger, not data the strategy
        // can force-capture as the first slot value), run advance(...) NOW —
        // before the LLM call — passing assistantMessage=null. The strategy
        // decides the cursor purely from the user message; the LLM then
        // composes ONE reply with the post-advance node already baked into the
        // system prompt. No regen, no anchoring patches.
        final TurAgentChatFlowContext flowContext = conversationLock.runExclusive(conversationId, () -> {
            TurAgentChatFlowContext initial = resolveFlowContext(agent, conversationId,
                    flowId, lastUserMessageEarly, chatModel, forcedVariant);
            boolean runEarly = initial != null && !initial.freshlyTriggered();
            // Diagnostic: shows whether a governing flow was resolved for this
            // turn under the per-conversation lock. A turn that lands on plain
            // RAG despite an in-progress flow shows flowResolved=false.
            log.debug("[ConvLock] flow turn conv='{}' flowResolved={} freshlyTriggered={} userMsg='{}'",
                    conversationId, initial != null && initial.flow() != null,
                    initial != null && initial.freshlyTriggered(), lastUserMessageEarly);
            return runEarly
                    ? earlyAdvanceBeforeLlm(initial, lastUserMessageEarly, chatModel)
                    : initial;
        });
        final long tAfterFlow = System.currentTimeMillis();

        // §I.5 step 2 / T10: SINGLE persona resolution per turn — runs
        // AFTER flow context resolution (and after early advance on the
        // inverted path). By the time this fires the cursor is FINAL pre-LLM.
        // T633 — the per-request persona override (public SN demo path) is
        // honoured here, validated against the catalog inside resolve(...); a
        // flow-state override still wins, so flow-driven turns are unaffected.
        final TurPersona activePersona = personaResolver.resolve(agent, conversationId,
                request.requestPersonaId());
        final long tAfterPersona = System.currentTimeMillis();

        // Resolve the active node up-front so the §IV.2 / T29 tool
        // pre-filter (inside the resolver) has access to its
        // requiredTools list. Reused below for the declarative tool-strip
        // decision (T11).
        ChatFlowNode activeNode = flowContext == null ? null
                : flowContext.graph().nodeById(flowContext.state().getCurrentNodeId()).orElse(null);

        // Tool resolution + decoration + BM25 pre-filter. The resolver
        // records STAGE_CHAT_SETUP_TOOLS internally.
        final long tBeforeTools = System.currentTimeMillis();
        ToolCallback[] callbacks = toolResolver.resolve(agent, activePersona,
                lastUserMessageEarly, activeNode, selectedSkillId);
        // T647 / §XXXVII.9 — tool trust boundary: strip ALL tool callbacks for an
        // untrusted (anonymous public SN chat) caller when the anonymous-tools
        // policy is off, so an anonymous visitor / injection payload can't trigger
        // native / MCP-client / custom / skill tool execution with system authority.
        if (request.untrustedCaller() && !abuseControlProperty.getChat().isAnonymousToolsEnabled()
                && callbacks != null && callbacks.length > 0) {
            log.info("[AgentExec] Stripping {} tool callback(s) for untrusted anonymous caller "
                    + "(turing.abuse.chat.anonymous-tools-enabled=false)", callbacks.length);
            callbacks = new ToolCallback[0];
        }
        final long tAfterTools = System.currentTimeMillis();

        // Build chat options + per-turn tool context map (drives Custom
        // Tool Groovy scripts: conversationId, agentId, agentPythonReqs).
        ChatOptions chatOptions = buildChatOptions(chatModel, agent, conversationId, callbacks,
                agentInvokeDepth, activePersona);

        // Strip the toolset from the INITIAL LLM call when the active
        // node's declarative {@code toolsEnabled=false} forbids them
        // (T11). Without this, gpt-4o-mini frequently called tools
        // preemptively on slot-capture nodes (e.g. ai-coupon-code) BEFORE
        // the guardrail strategy could capture the user's literal value.
        boolean activeNodeForbidsTools = TurChatToolResolver.forbidsToolCalls(activeNode);
        // Spring AI 2.0.0-RC1 removed internalToolExecutionEnabled; the empty
        // toolCallbacks list alone already guarantees the tool-free initial call
        // (no callbacks means nothing for the model to invoke). The options must
        // still be the provider's concrete type (see buildChatOptions) — Spring
        // AI 2.0.0 (final) hard-casts prompt.getOptions() to OpenAiChatOptions.
        ChatOptions initialOptions = activeNodeForbidsTools
                ? providerToolOptions(chatModel, new ToolCallback[0], java.util.Map.of(), activePersona)
                : chatOptions;
        if (activeNodeForbidsTools) {
            log.info("[AgentExec] Initial call on node '{}' running tool-free (aiInstruction forbids tools)",
                    activeNode.id());
        }

        // Assemble the List<Message>. The assembler records
        // STAGE_CHAT_SETUP_COMPOSE internally around the personaComposer
        // call (matches the pre-T33 substage boundary).
        final long tBeforeCompose = System.currentTimeMillis();
        List<Message> messages = promptAssembler.assemble(
                new TurChatPromptRequest(agent, history, conversationId, flowContext,
                        activePersona, lastUserMessageEarly, baseSystemPrompt),
                files, selectedSkillId);
        final long tAfterCompose = System.currentTimeMillis();

        // T123 / §IX.7 — enforceable token-budget check. Disabled when
        // agent.maxPromptTokens == 0 (the default). ERROR mode short-
        // circuits the LLM call with a single ASSISTANT_TURN error event
        // so the SSE consumer sees a structured failure rather than a
        // dangling stream; WARN logs + proceeds; COMPACT routes the prompt
        // through the T115-backed compactor (summarizing the older middle
        // turns) and proceeds best-effort even if still over budget.
        TurTokenBudgetService.CheckResult budgetCheck =
                tokenBudgetService.check(agent, llmInstance, messages);
        if (budgetCheck.decision() == TurTokenBudgetService.Decision.COMPACT) {
            TurPromptCompactor.CompactionResult compaction = promptCompactor.compact(agent, messages);
            if (compaction.compacted()) {
                messages = compaction.messages();
                if (compaction.afterTokens() > budgetCheck.budget()) {
                    log.warn("[AgentExec] agent '{}' still over budget after COMPACT (est={} max={}) — "
                            + "proceeding best-effort", agent.getId(), compaction.afterTokens(),
                            budgetCheck.budget());
                }
            } else {
                log.warn("[AgentExec] agent '{}' COMPACT requested but nothing to compact "
                        + "(est={} max={}) — proceeding over budget",
                        agent.getId(), budgetCheck.estimatedTokens(), budgetCheck.budget());
            }
        } else if (budgetCheck.decision() == TurTokenBudgetService.Decision.ERROR) {
            log.warn("[AgentExec] Token budget exceeded for agent '{}' — estimate={} max={} — aborting",
                    agent.getId(), budgetCheck.estimatedTokens(), budgetCheck.budget());
            String errMsg = String.format(
                    "Prompt over budget (estimated %d tokens, limit %d). Trim the conversation or raise the agent's maxPromptTokens.",
                    budgetCheck.estimatedTokens(), budgetCheck.budget());
            return Flux.just(new ChatResponse("assistant", errMsg));
        }

        String username = resolveUsername();

        // Open the analytics session record on the first turn. Idempotent —
        // subsequent turns of the same conversation no-op past the upsert.
        recordSessionStartSafely(agent, activePersona, llmInstance,
                conversationId, lastUserMessageEarly, username);

        final long tSetupEnd = System.currentTimeMillis();
        chatPipelineObservation.recordMillis(TurMeterNames.STAGE_CHAT_SETUP, tSetupEnd - tStart);

        // T32 / §IV.7 — sub-stage breakdown of the setup phase. DECRYPT /
        // MODEL / FLOW / PERSONA recorded here; TOOLS and COMPOSE are
        // owned by the collaborators (TurChatToolResolver /
        // TurChatPromptAssembler) and recorded inside their respective
        // calls. The parent STAGE_CHAT_SETUP timer (above) stays as the
        // backwards-compatible aggregate.
        chatPipelineObservation.recordMillis(TurMeterNames.STAGE_CHAT_SETUP_DECRYPT,
                tAfterDecrypt - tStart);
        chatPipelineObservation.recordMillis(TurMeterNames.STAGE_CHAT_SETUP_MODEL,
                tAfterChatModel - tAfterDecrypt);
        chatPipelineObservation.recordMillis(TurMeterNames.STAGE_CHAT_SETUP_FLOW,
                tAfterFlow - tAfterChatModel);
        chatPipelineObservation.recordMillis(TurMeterNames.STAGE_CHAT_SETUP_PERSONA,
                tAfterPersona - tAfterFlow);

        // Cold-start (first turn after restart) typically spends seconds in
        // tools (MCP handshake) or chat_model (TLS to OpenAI). Subsequent
        // turns hit caches and stay under ~100ms. Each delta is one phase:
        //   decrypt    — TurSecretCryptoService.decrypt (AES, ~ms)
        //   chatModel  — llmModelFactory.createChatModel (HTTP client init)
        //   flow       — resolveFlowContext + earlyAdvanceBeforeLlm
        //   persona    — personaResolver.resolve (SINGLE resolution per turn)
        //   tools      — toolResolver.resolve (MCP handshake, BM25 pre-filter)
        //   compose    — promptAssembler.assemble (RAG prefix, personaComposer)
        //   session    — recordSessionStartSafely (analytics upsert)
        log.info("[AgentExec][timing] setup_ms={} "
                + "(decrypt={} chatModel={} flow={} persona={} tools={} compose={} session={})",
                tSetupEnd - tStart,
                tAfterDecrypt - tStart,
                tAfterChatModel - tAfterDecrypt,
                tAfterFlow - tAfterChatModel,
                tAfterPersona - tAfterFlow,
                tAfterTools - tBeforeTools,
                tAfterCompose - tBeforeCompose,
                tSetupEnd - tAfterCompose);

        return streamingDispatcher.dispatch(
                new TurChatTurnContext(agent, llmInstance, conversationId, lastUserMessageEarly,
                        username, tStart, tSetupEnd),
                decryptedApiKey, chatModel, messages, initialOptions, flowContext, activePersona);
    }

    /**
     * Builds the per-turn {@link ChatOptions} for the CALL branch:
     * tool callbacks + internal tool execution + the per-turn tool context
     * map (conversationId / agentId / agentPythonRequirements) consumed by
     * Custom Tool Groovy scripts.
     */
    private static ChatOptions buildChatOptions(ChatModel chatModel, TurAIAgent agent,
            String conversationId, ToolCallback[] callbacks, int agentInvokeDepth,
            TurPersona activePersona) {
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
        // T41 — publish the authenticated user's username so a Custom Tool
        // invocation can look up an active draft for the same admin in
        // TurCustomToolDraftRegistry. Resolved at this point on the HTTP
        // thread (before the reactive Flux starts) so SecurityContextHolder
        // is still populated. Anonymous sessions skip — no draft can apply.
        String username = resolveUsername();
        if (username != null && !"anonymous".equals(username)) {
            toolContextMap.put(
                    com.viglet.turing.genai.tool.TurCustomToolCallbackService.TOOL_CONTEXT_USERNAME,
                    username);
        }
        // T109 — publish the current agent.invoke(...) recursion depth so a
        // Custom Tool spawned by the LLM in this turn (nested or not) sees
        // the right depth and can short-circuit at the cap. Always
        // published (even at depth 0) so a Custom Tool reading the value
        // gets a stable presence-or-absence contract.
        if (agentInvokeDepth > 0) {
            toolContextMap.put(
                    com.viglet.turing.genai.tool.TurCustomToolCallbackService.TOOL_CONTEXT_AGENT_INVOKE_DEPTH,
                    agentInvokeDepth);
        }
        // T292 — publish a per-turn RAG provenance sink. The
        // search_knowledge_base tool appends the retrieved chunks' source
        // metadata to it; the streaming dispatcher drains it after the
        // tool-execution loop to emit the structured `sources[]` SSE event.
        // Always present so the tool has a stable target; empty when no RAG
        // tool fires this turn (drained to nothing → no event emitted).
        toolContextMap.put(
                com.viglet.turing.genai.tool.TurCustomToolCallbackService.TOOL_CONTEXT_RAG_SOURCES,
                new com.viglet.turing.genai.rag.TurRagSourceCollector());
        // T427 / T436 — publish a per-turn tool-call collector. TurLoggingToolCallback
        // records each invocation into it; the dispatcher drains it into the
        // read-only trace store after the loop and (when toolCallEventsEnabled)
        // streams the calls live as `tool_call` SSE events. Always present so the
        // callback has a stable target; empty when no tool fires this turn.
        toolContextMap.put(
                com.viglet.turing.genai.tool.TurCustomToolCallbackService.TOOL_CONTEXT_TOOL_CALLS,
                new com.viglet.turing.genai.tool.TurToolCallCollector());
        // internalToolExecutionEnabled was removed in Spring AI 2.0.0-RC1;
        // internal tool execution is now the default once callbacks are present.
        return providerToolOptions(chatModel, callbacks, toolContextMap, activePersona);
    }

    /**
     * Builds the per-turn {@link ChatOptions} by seeding from the provider's
     * own concrete options (see {@link TurChatToolOptions}) and attaching the
     * tool callbacks + tool context. Must NOT hand the model a generic
     * {@code DefaultToolCallingChatOptions} — Spring AI 2.0.0 hard-casts the
     * prompt options to the provider type and would throw {@code ClassCastException}.
     */
    private static ChatOptions providerToolOptions(ChatModel chatModel,
            ToolCallback[] callbacks, java.util.Map<String, Object> toolContext,
            TurPersona activePersona) {
        List<ToolCallback> callbackList = callbacks == null ? List.of() : List.of(callbacks);
        ToolCallingChatOptions.Builder<?> builder = TurChatToolOptions.builderFrom(chatModel)
                .toolCallbacks(callbackList);
        if (toolContext != null && !toolContext.isEmpty()) {
            builder.toolContext(toolContext);
        }
        // T605 — opt-in style→model calibration. When the active persona opts in,
        // its verbosity/tone override the resolved sampling params for this turn;
        // otherwise the builder keeps the instance's own temperature/maxTokens.
        TurPersonaModelCalibration.Params calibration =
                TurPersonaModelCalibration.forPersona(activePersona);
        if (calibration.temperature() != null) {
            builder.temperature(calibration.temperature());
        }
        if (calibration.maxTokens() != null) {
            builder.maxTokens(calibration.maxTokens());
        }
        return builder.build();
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
            // T74 — resolve the visitor cohort (locale / timezone / device)
            // from the HTTP request bound to THIS thread. Runs in the
            // synchronous prologue of execute(...) — same place resolveUsername
            // reads SecurityContextHolder — before the reactive Flux starts on
            // a worker thread, so the request headers are still reachable.
            // Off-request callers (agent.invoke child sessions) get an empty
            // cohort, which is correct: a child has no visitor cohort.
            TurChatCohortResolver.Cohort cohort = chatCohortResolver == null
                    ? TurChatCohortResolver.Cohort.empty()
                    : chatCohortResolver.resolveFromCurrentRequest();
            TurChatSessionRefs refs = new TurChatSessionRefs(
                    conversationId,
                    agent.getId(),
                    activePersona == null ? null : activePersona.getId(),
                    llmInstance == null ? null : llmInstance.getId(),
                    agent.getTurEmbeddingModelInstance() == null
                            ? null : agent.getTurEmbeddingModelInstance().getId(),
                    agent.getTurStoreInstance() == null
                            ? null : agent.getTurStoreInstance().getId(),
                    username);
            chatAnalyticsService.recordSessionStart(refs,
                    new TurChatVisitorContext(cohort.locale(), cohort.timezone(), cohort.deviceType()),
                    firstUserMessage);
        } catch (RuntimeException e) {
            log.debug("[AgentExec] analytics start failed: {}", e.getMessage());
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
    private TurAgentChatFlowContext resolveFlowContext(TurAIAgent agent,
            String conversationId,
            String flowId,
            String lastUserMessage,
            ChatModel routerModel,
            String forcedVariant) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        if (FLOW_ID_NONE.equals(flowId)) {
            return null;
        }
        if (flowId != null && !flowId.isBlank()) {
            return resolveForcedFlow(agent, conversationId, flowId);
        }
        // Auto mode: ask the engine's router which flow (if any) fits. The
        // forced-variant hint (T73) only bites here — an explicit flowId pin
        // above already bypasses A/B routing entirely.
        return chatFlowEngineService
                .selectActiveFlow(agent, conversationId, lastUserMessage, routerModel, forcedVariant)
                .map(s -> new TurAgentChatFlowContext(s.flow(), s.graph(), s.state(), s.freshlyTriggered()))
                .orElse(null);
    }

    /**
     * §I.5 step 1 — runs the flow engine's {@code advance(...)} BEFORE the
     * initial LLM call with {@code assistantMessage = null} and returns a
     * fresh {@link TurAgentChatFlowContext} pinned to the post-advance state.
     * The graph is re-resolved when {@code advance} descended into a sub-flow
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
    private TurAgentChatFlowContext earlyAdvanceBeforeLlm(TurAgentChatFlowContext initial,
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
        return new TurAgentChatFlowContext(newState.getFlow(), newGraph, newState,
                initial.freshlyTriggered());
    }

    private TurAgentChatFlowContext resolveForcedFlow(TurAIAgent agent, String conversationId, String flowId) {
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
        // processed by LlmJudge against the current node.
        return new TurAgentChatFlowContext(activeFlow, activeGraph, activeState, false);
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

    private static String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }
}
