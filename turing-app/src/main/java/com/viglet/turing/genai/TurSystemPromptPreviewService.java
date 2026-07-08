/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.flow.ChatFlowEdge;
import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.flow.TurChatFlowEngineService.ConversationStateDto;
import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.genai.persona.TurAgentPersonaResolver;
import com.viglet.turing.genai.capture.TurPromptCapture;
import com.viglet.turing.genai.tool.TurChatToolCall;
import com.viglet.turing.genai.prompt.TurMessageAssemblyContext;
import com.viglet.turing.genai.prompt.TurMessageAssemblyPipeline;
import com.viglet.turing.genai.prompt.TurPromptAssembly;
import com.viglet.turing.genai.prompt.TurPromptAssemblyContext;
import com.viglet.turing.genai.prompt.TurPromptAssemblyPipeline;
import com.viglet.turing.genai.prompt.TurPromptMessage;
import com.viglet.turing.genai.prompt.TurPromptSegment;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptCapturedTurnDto;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptFlowNodeDto;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptFlowOptionDto;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptInertRegionDto;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptMessageDto;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptPreviewDto;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptReplayDto;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptSegmentDto;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptToolCallDto;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptToolDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.model.customtool.TurCustomTool;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.service.chatanalytics.TurToolCallTraceService;
import com.viglet.turing.service.llm.price.TurLLMPriceService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds a legible, segmented preview of the system prompt an AI Agent sends to
 * the LLM. Block AL / T613 — the preview now renders the <em>same</em>
 * {@link TurPromptSegment segments} the runtime assembles through the shared
 * {@link TurPromptAssemblyPipeline}, instead of rebuilding a lookalike. That
 * retires the "two prompt-builders that drift" root cause (the persona lie, the
 * stray {@code ===}) — {@code assembledText} is now byte-for-byte what the model
 * receives for the previewed scenario.
 *
 * <p>The operator picks which chat flow governs the previewed turn ({@code
 * flowId}); the selected flow's entry-node state is synthesized and fed to the
 * pipeline's flow contributor exactly as the runtime would resolve it. Fragments
 * that depend on the live message (persona few-shot retrieval) or on the SN-site
 * RAG path stay informational example segments and out of {@code assembledText}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurSystemPromptPreviewService {

    // Mirrors TurAgentChatExecutor.DEFAULT_SYSTEM_PROMPT — the fallback used
    // when an agent has no system prompt of its own. Kept in sync manually
    // (both are tiny and rarely change); the preview labels it as a fallback.
    static final String DEFAULT_SYSTEM_PROMPT = """
            You are an AI assistant. Answer the user's questions using the tools available to you.
            If you have access to MCP server tools, use them when relevant to fulfill the user's request.
            If the user asks in a specific language, respond in that same language.
            """;

    /** Sentinel flowId meaning "preview a turn with no chat flow governing". */
    public static final String FLOW_NONE = "__none__";

    /**
     * Prefix for the i18n keys the preview emits in each segment/message
     * {@code note}. The note is a localization KEY, not prose: the frontend
     * renders it with {@code t(note)} against {@code aiAgent.systemPrompt.preview.note.*}
     * in its locale bundles. The backend keeps deciding WHICH note applies (only
     * it knows the resolution context); the frontend owns the wording per locale.
     */
    private static final String NOTE_PREFIX = "aiAgent.systemPrompt.preview.note.";

    private final TurPromptAssemblyPipeline promptAssemblyPipeline;
    private final TurMessageAssemblyPipeline messageAssemblyPipeline;
    private final TurChatFlowEngineService chatFlowEngineService;
    private final TurChatFlowRepository chatFlowRepository;
    private final TurLLMPriceService priceService;
    private final TurInertRegionDetector inertRegionDetector;
    private final TurAgentPersonaResolver personaResolver;
    private final TurToolCallTraceService toolCallTraceService;
    private final com.viglet.turing.genai.capture.TurPromptCaptureService promptCaptureService;

    public TurSystemPromptPreviewService(TurPromptAssemblyPipeline promptAssemblyPipeline,
            TurMessageAssemblyPipeline messageAssemblyPipeline,
            TurChatFlowEngineService chatFlowEngineService,
            TurChatFlowRepository chatFlowRepository,
            TurLLMPriceService priceService,
            TurInertRegionDetector inertRegionDetector,
            TurAgentPersonaResolver personaResolver,
            TurToolCallTraceService toolCallTraceService,
            com.viglet.turing.genai.capture.TurPromptCaptureService promptCaptureService) {
        this.promptAssemblyPipeline = promptAssemblyPipeline;
        this.messageAssemblyPipeline = messageAssemblyPipeline;
        this.chatFlowEngineService = chatFlowEngineService;
        this.chatFlowRepository = chatFlowRepository;
        this.priceService = priceService;
        this.inertRegionDetector = inertRegionDetector;
        this.personaResolver = personaResolver;
        this.toolCallTraceService = toolCallTraceService;
        this.promptCaptureService = promptCaptureService;
    }

    /** Convenience overload — previews the default flow (first enabled). */
    @Transactional(readOnly = true)
    public TurSystemPromptPreviewDto buildPreview(TurAIAgent agent) {
        return buildPreviewInternal(agent, null, null, null, null);
    }

    /**
     * Compose the preview for {@code agent}, with the {@code flowId} flow
     * governing the turn ({@code null} = default to the first enabled flow,
     * {@link #FLOW_NONE} = no flow). {@code @Transactional(readOnly)} keeps a
     * session open so lazy associations resolve.
     */
    @Transactional(readOnly = true)
    public TurSystemPromptPreviewDto buildPreview(TurAIAgent agent, String flowId) {
        return buildPreviewInternal(agent, flowId, null, null, null);
    }

    /**
     * T611 — compose the preview for a specific mid-conversation turn: the
     * {@code nodeId} node of the selected flow governs (instead of the entry
     * anchor) and {@code variablesJson} seeds the collected slot values, so the
     * flow addendum's "Already collected" line + next-step hint reflect a real
     * turn. Blank/unknown {@code nodeId} falls back to the entry anchor;
     * invalid {@code variablesJson} falls back to empty state.
     */
    @Transactional(readOnly = true)
    public TurSystemPromptPreviewDto buildPreview(TurAIAgent agent, String flowId,
            String nodeId, String variablesJson) {
        return buildPreviewInternal(agent, flowId, nodeId, variablesJson, null);
    }

    /**
     * T612 — replay the assembled prompt from a <em>real</em> conversation. Reads
     * {@code conversationId}'s persisted leaf state (its governing flow, current
     * cursor node and collected slots) and the persona the runtime resolved for it
     * ({@link TurAgentPersonaResolver}, honouring a {@code __activePersonaId} flow
     * override), then feeds them through the same shared assembler the runtime uses
     * — so {@code assembledText} is what the model would receive for that
     * conversation's next turn, not a synthesized entry turn. The conversation's
     * T427 tool-call trace rides along so the operator can read "what the prompt
     * said" against "what the model actually called".
     *
     * <p>A blank id, or one with no persisted state / whose flow isn't this agent's,
     * degrades gracefully to a plain preview with {@code replay.resolved=false}.
     */
    @Transactional(readOnly = true)
    public TurSystemPromptPreviewDto buildReplayPreview(TurAIAgent agent, String conversationId) {
        return buildReplayPreview(agent, conversationId, null);
    }

    /**
     * T618 — true per-past-turn replay. When {@code turnIndex} names a captured
     * turn that still exists in the prompt-capture store, the preview shows it
     * <em>verbatim</em> — the exact assembled system message + whole-message list
     * frozen when the executor sent it — instead of reconstructing the
     * conversation's current state (which T612 does when {@code turnIndex} is
     * {@code null}). A requested turn that was pruned/evicted falls back to the
     * current-state replay, still offering the picker. Either way the replay
     * envelope carries {@code availableTurns} so the operator can pick another.
     */
    @Transactional(readOnly = true)
    public TurSystemPromptPreviewDto buildReplayPreview(TurAIAgent agent, String conversationId,
            Integer turnIndex) {
        List<TurSystemPromptCapturedTurnDto> availableTurns = listCapturedTurns(agent, conversationId);
        if (turnIndex != null && StringUtils.hasText(conversationId)) {
            Optional<TurPromptCapture> capture =
                    promptCaptureService.getCapture(agent.getId(), conversationId, turnIndex);
            if (capture.isPresent()) {
                return buildVerbatimPreview(agent, capture.get(), availableTurns);
            }
            // Requested turn no longer exists — fall through to current-state replay.
        }
        if (!StringUtils.hasText(conversationId)) {
            return buildPreviewInternal(agent, FLOW_NONE, null, null,
                    new ReplayContext(conversationId, false, null, null, List.of(), availableTurns));
        }
        // Never null — getConversationState returns an empty-field DTO for an
        // unknown conversation rather than null.
        ConversationStateDto state = chatFlowEngineService.getConversationState(conversationId);
        boolean resolved = StringUtils.hasText(state.flowId());
        // Honour the conversation's flow only when it is one of this agent's flows;
        // otherwise the replay falls back to a no-flow turn rather than silently
        // rendering a different agent's flow (resolveSelectedFlow's default-pick).
        boolean flowIsAgents = resolved && chatFlowRepository
                .findByTurAIAgent_IdOrderByNameAsc(agent.getId()).stream()
                .anyMatch(f -> f.getId().equals(state.flowId()));
        String flowId = flowIsAgents ? state.flowId() : FLOW_NONE;
        String nodeId = flowIsAgents ? state.currentNodeId() : null;
        String variablesJson = flowIsAgents
                ? serializeSlots(chatFlowEngineService.listSlotsForConversation(conversationId).slots())
                : null;
        // The persona the runtime would actually speak with for this conversation.
        TurPersona persona = personaResolver.resolve(agent, conversationId);
        List<TurChatToolCall> trace = toolCallTraceService.getTrace(conversationId);
        ReplayContext replay = new ReplayContext(conversationId, resolved,
                flowIsAgents ? state.flowName() : null, persona, trace, availableTurns);
        return buildPreviewInternal(agent, flowId, nodeId, variablesJson, replay);
    }

    /**
     * T618 — the captured turns available for {@code conversationId}, newest
     * first, for the preview's turn picker. Empty when the agent never opted into
     * capture, storage is disabled, or none were stored.
     */
    @Transactional(readOnly = true)
    public List<TurSystemPromptCapturedTurnDto> listCapturedTurns(TurAIAgent agent, String conversationId) {
        if (!promptCaptureService.isEnabled(agent) || !StringUtils.hasText(conversationId)) {
            return List.of();
        }
        return promptCaptureService.listTurns(agent.getId(), conversationId).stream()
                .map(ref -> new TurSystemPromptCapturedTurnDto(ref.turnIndex(), ref.capturedAt()))
                .toList();
    }

    /**
     * T618 — builds the preview DTO directly from a stored capture, byte-for-byte.
     * Unlike a reconstructed preview there is no per-contributor provenance (only
     * the final text was captured), so the system message is a single opaque
     * segment and the whole non-system message list rides in {@code messages}.
     */
    private TurSystemPromptPreviewDto buildVerbatimPreview(TurAIAgent agent,
            TurPromptCapture capture, List<TurSystemPromptCapturedTurnDto> availableTurns) {
        String systemText = capture.systemPrompt() == null ? "" : capture.systemPrompt();
        int systemTokens = estimateTokens(systemText);
        List<TurSystemPromptSegmentDto> segments = List.of(new TurSystemPromptSegmentDto(
                "CAPTURED", "Captured system prompt", systemText, true, false,
                "Loaded verbatim from turn " + capture.turnIndex()
                        + " — the exact system message the model received.",
                systemTokens, "STABLE"));

        List<TurSystemPromptMessageDto> messages = new ArrayList<>();
        int totalTokens = systemTokens;
        for (TurPromptCapture.Message message : capture.messages()) {
            if ("system".equals(message.role())) {
                continue; // already surfaced as the segment / assembledText
            }
            String content = message.content() == null ? "" : message.content();
            int tokens = estimateTokens(content);
            totalTokens += tokens;
            messages.add(new TurSystemPromptMessageDto(message.role(), "CAPTURED",
                    capturedRoleLabel(message.role()), content, tokens, false, null));
        }

        List<TurSystemPromptToolCallDto> toolCalls = new ArrayList<>();
        for (TurChatToolCall call : toolCallTraceService.getTrace(capture.conversationId())) {
            toolCalls.add(new TurSystemPromptToolCallDto(call.name(), call.argsSummary(),
                    call.status(), call.durationMs() == null ? 0L : call.durationMs()));
        }
        TurSystemPromptReplayDto replay = new TurSystemPromptReplayDto(capture.conversationId(),
                true, null, null, null, toolCalls, capture.turnIndex(), true,
                capture.capturedAt(), availableTurns);

        // No cost estimate / cache split on a verbatim view — those describe the
        // live assembler's output, not a frozen snapshot. Tools panel left empty
        // (the schema channel was not captured).
        return new TurSystemPromptPreviewDto(segments, List.of(), systemText,
                List.of(), null, totalTokens, systemTokens, -1, 0.0, null,
                List.of(), null, messages, replay);
    }

    private static int estimateTokens(String text) {
        return text == null || text.isEmpty() ? 0 : text.length() / 4;
    }

    private static String capturedRoleLabel(String role) {
        return switch (role == null ? "" : role) {
            case "assistant" -> "Assistant";
            case "user" -> "User";
            case "system" -> "System";
            default -> role;
        };
    }

    private TurSystemPromptPreviewDto buildPreviewInternal(TurAIAgent agent, String flowId,
            String nodeId, String variablesJson, ReplayContext replay) {
        boolean usesDefault = !StringUtils.hasText(agent.getSystemPrompt());
        String base = usesDefault ? DEFAULT_SYSTEM_PROMPT.strip() : agent.getSystemPrompt();

        List<TurChatFlow> flows = chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(agent.getId());
        List<TurSystemPromptFlowOptionDto> flowOptions = flows.stream()
                .map(f -> new TurSystemPromptFlowOptionDto(f.getId(), f.getName(), f.getEnabled() == 1))
                .toList();
        TurChatFlow selectedFlow = resolveSelectedFlow(flows, flowId);

        // Synthesize the selected flow's node state (T611: the picked nodeId +
        // simulated collected variables, or the entry anchor) so the pipeline's
        // flow contributor produces the exact addendum the runtime would there.
        FlowPreview flowPreview = selectedFlow == null
                ? null : buildFlowPreview(agent, selectedFlow, nodeId, variablesJson);
        TurAgentChatFlowContext flowContext = flowPreview == null ? null : flowPreview.context();

        // T607 — resolve the persona the way the runtime does: a persona-type
        // node on the path to the entry anchor switches the active voice via
        // __activePersonaId (mirroring TurAgentPersonaResolver). Fall back to the
        // agent default when no flow governs the turn or none applies. For a T612
        // replay the runtime's own resolution (from the real conversation state) is
        // authoritative and wins — it reads the persisted __activePersonaId even
        // when the switching node isn't on the static first-edge walk.
        FlowPersonaResolution personaResolution = flowPreview == null
                ? null : flowPreview.personaResolution();
        TurPersona persona = replay != null ? replay.persona()
                : (personaResolution != null
                        ? personaResolution.persona() : agent.getDefaultPersona());

        // ONE assembly, shared with the runtime. Null embedding model + null
        // query so the persona contributor skips live few-shot retrieval; null
        // selectedSkillId offers all enabled skills, as a plain turn would.
        TurPromptAssemblyContext assemblyContext = new TurPromptAssemblyContext(
                agent, persona, null, null, base, flowContext, null);
        TurPromptAssembly assembly = promptAssemblyPipeline.assemble(assemblyContext);

        List<TurSystemPromptSegmentDto> segments = new ArrayList<>();
        for (TurPromptSegment seg : assembly.segments()) {
            segments.add(toDto(seg, usesDefault, flowPreview, replay != null));
        }

        // Informational-only segments: not part of assembledText, shown as
        // "materializes at runtime" examples so the operator knows they exist.
        if (persona != null && persona.getFewShotStore() != null) {
            segments.add(new TurSystemPromptSegmentDto(TurPromptSegment.ORIGIN_FEW_SHOT,
                    persona.getName(), "", false, true,
                    "Few-shot examples are retrieved at runtime by similarity to the user's message — not fixed text.",
                    0, null));
        }
        segments.add(new TurSystemPromptSegmentDto("RAG",
                null, "", false, true,
                "When invoked through an SN site in AI (RAG) mode, the agent base prompt is replaced by a retrieval-augmented prompt carrying the matched knowledge chunks.",
                0, null));

        // T608 — best-effort per-turn cost of the assembled system prompt (input
        // tokens only) at the agent's first enabled LLM instance's price. 0.0 for
        // unpriced/local models, so the "$0 fully local" story stays honest.
        TurLLMInstance costInstance = firstEnabledInstance(agent);
        double estimatedCostUsd = estimateInputCost(costInstance, assembly.totalTokens());
        String costModelName = costInstance == null ? null : costInstance.getModelName();

        return new TurSystemPromptPreviewDto(segments, buildTools(agent), assembly.systemText(),
                flowOptions, selectedFlow == null ? null : selectedFlow.getId(),
                assembly.totalTokens(), assembly.stableTokens(), assembly.cacheBreakpointIndex(),
                estimatedCostUsd, costModelName,
                flowPreview == null ? List.of() : flowPreview.flowNodes(),
                flowPreview == null ? null : flowPreview.nodeId(),
                buildHistoryPrefixMessages(agent),
                buildReplayDto(replay, selectedFlow, flowPreview, persona));
    }

    /**
     * T612 — the replay envelope for the returned preview, or {@code null} for a
     * synthesized (non-replay) preview. Reports the real conversation source, the
     * flow/node the addendum was rebuilt at, the resolved persona, and the T427
     * tool-call trace mapped to display DTOs.
     */
    private static TurSystemPromptReplayDto buildReplayDto(ReplayContext replay,
            TurChatFlow selectedFlow, FlowPreview flowPreview, TurPersona persona) {
        if (replay == null) {
            return null;
        }
        List<TurSystemPromptToolCallDto> toolCalls = new ArrayList<>();
        for (TurChatToolCall call : replay.toolCalls()) {
            toolCalls.add(new TurSystemPromptToolCallDto(call.name(), call.argsSummary(),
                    call.status(), call.durationMs() == null ? 0L : call.durationMs()));
        }
        String flowName = replay.flowName() != null ? replay.flowName()
                : (selectedFlow == null ? null : selectedFlow.getName());
        // T618 — a current-state replay is not verbatim (turnIndex null), but it
        // still advertises the captured turns so the picker can switch to one.
        return new TurSystemPromptReplayDto(replay.conversationId(), replay.resolved(),
                flowName, flowPreview == null ? null : flowPreview.nodeId(),
                persona == null ? null : persona.getName(), toolCalls,
                null, false, 0L, replay.availableTurns());
    }

    /** Serializes a conversation's merged slot map to a JSON object string. */
    private String serializeSlots(Map<String, String> slots) {
        if (slots == null || slots.isEmpty()) {
            return "{}";
        }
        try {
            return VARS_MAPPER.writeValueAsString(slots);
        } catch (RuntimeException e) {
            log.debug("[SystemPrompt] replay slot serialization failed, using empty state: {}",
                    e.getMessage());
            return "{}";
        }
    }

    /**
     * T612 — inputs for a replay preview: the source conversation and the runtime
     * facts (resolved persona, governing flow name, tool-call trace) that the
     * static synthesis path cannot know. {@code persona} is authoritative over the
     * flow-graph walk when this is set.
     */
    private record ReplayContext(String conversationId, boolean resolved, String flowName,
            TurPersona persona, List<TurChatToolCall> toolCalls,
            List<TurSystemPromptCapturedTurnDto> availableTurns) {
        ReplayContext {
            toolCalls = toolCalls == null ? List.of() : toolCalls;
            availableTurns = availableTurns == null ? List.of() : availableTurns;
        }
    }

    /**
     * T617 — mirror the runtime history-prefix message list in the preview. Runs
     * the <em>same</em> {@link TurMessageAssemblyPipeline} the assembler uses (no
     * drift), so any message it emits is carried verbatim. A config-level preview
     * has no live conversation, so the pipeline emits nothing here; the two memory
     * sources are then surfaced as informational {@code runtimeOnly} placeholders
     * — the same treatment the persona few-shot and RAG segments get — so the
     * operator can see where the T115 summary and the T30 relevance turns sit in
     * the whole message list. Origins the pipeline already emitted concretely are
     * not duplicated by a placeholder.
     */
    private List<TurSystemPromptMessageDto> buildHistoryPrefixMessages(TurAIAgent agent) {
        List<TurPromptMessage> emitted = messageAssemblyPipeline.assemble(
                new TurMessageAssemblyContext(agent, null, null));
        List<TurSystemPromptMessageDto> messages = new ArrayList<>();
        Set<String> concreteOrigins = new HashSet<>();
        for (TurPromptMessage m : emitted) {
            concreteOrigins.add(m.origin());
            messages.add(new TurSystemPromptMessageDto(m.role(), m.origin(), m.label(),
                    m.content(), m.tokens(), false, null));
        }
        if (!concreteOrigins.contains(TurPromptMessage.ORIGIN_MEMORY_SUMMARY)) {
            messages.add(new TurSystemPromptMessageDto("user",
                    TurPromptMessage.ORIGIN_MEMORY_SUMMARY, "Memory summary", "", 0, true,
                    NOTE_PREFIX + "memorySummary"));
        }
        if (!concreteOrigins.contains(TurPromptMessage.ORIGIN_MEMORY_RELEVANCE)) {
            messages.add(new TurSystemPromptMessageDto("user",
                    TurPromptMessage.ORIGIN_MEMORY_RELEVANCE, "Relevant earlier turns", "", 0, true,
                    NOTE_PREFIX + "memoryRelevance"));
        }
        return messages;
    }

    /** The agent's first enabled LLM instance, or {@code null} when none. */
    private static TurLLMInstance firstEnabledInstance(TurAIAgent agent) {
        if (agent.getLlmInstances() == null) {
            return null;
        }
        return agent.getLlmInstances().stream()
                .filter(i -> i != null && i.getEnabled() == 1)
                .findFirst()
                .orElse(null);
    }

    /**
     * USD cost of sending {@code inputTokens} once at {@code instance}'s configured
     * price (output tokens = 0 — this is the system-prompt input alone). Returns
     * {@code 0.0} when the instance/vendor/price can't be resolved.
     */
    private double estimateInputCost(TurLLMInstance instance, int inputTokens) {
        if (instance == null || instance.getTurLLMVendor() == null) {
            return 0.0;
        }
        return priceService.computeCost(instance.getTurLLMVendor().getId(),
                instance.getModelName(), inputTokens, 0);
    }

    /**
     * Maps one pipeline segment to its preview DTO — carrying the token estimate
     * and stability class through, and attaching the origin-specific note the UI
     * shows.
     */
    private TurSystemPromptSegmentDto toDto(TurPromptSegment seg, boolean usesDefault,
            FlowPreview flowPreview, boolean replaying) {
        String title = seg.title();
        FlowPersonaResolution personaRes = flowPreview == null ? null : flowPreview.personaResolution();
        // The note is an i18n KEY (not prose): the frontend renders it with
        // t(note). The backend still decides WHICH note applies, since only it
        // knows the resolution context (usesDefault / replaying / persona
        // resolution). Keys live under aiAgent.systemPrompt.preview.note.* in the
        // frontend locale bundles; the locale-parity test keeps en/pt in sync.
        String note = switch (seg.origin()) {
            case TurPromptSegment.ORIGIN_PERSONA -> replaying
                    ? NOTE_PREFIX + "personaReplay"
                    : personaNote(personaRes);
            case TurPromptSegment.ORIGIN_AGENT -> usesDefault
                    ? NOTE_PREFIX + "agentFallbackDefault"
                    : null;
            case TurPromptSegment.ORIGIN_MCP -> NOTE_PREFIX + "mcp";
            case TurPromptSegment.ORIGIN_CAPABILITY -> NOTE_PREFIX + "capability";
            case TurPromptSegment.ORIGIN_SKILL -> NOTE_PREFIX + "skill";
            case TurPromptSegment.ORIGIN_FLOW -> replaying
                    ? NOTE_PREFIX + "flowReplay"
                    : NOTE_PREFIX + "flow";
            default -> null;
        };
        if (TurPromptSegment.ORIGIN_FLOW.equals(seg.origin())
                && flowPreview != null && flowPreview.nodeId() != null) {
            title = (title == null ? "" : title) + " → " + flowPreview.nodeId();
        }
        // T609 — flag the agent-prompt regions the active flow neutralizes
        // (welcome / first-interaction / routing scaffolding). Only when a flow
        // governs the previewed turn; the marker-based flow-only case is detected
        // on no-flow turns too, so run the detector on the AGENT segment always.
        String display = seg.displayText();
        List<TurSystemPromptInertRegionDto> inertRegions =
                TurPromptSegment.ORIGIN_AGENT.equals(seg.origin())
                        ? inertRegionDetector.detect(display, flowPreview != null)
                        : List.of();
        return new TurSystemPromptSegmentDto(seg.origin(), title, display,
                true, false, note, seg.tokens(), seg.stability().name(), inertRegions);
    }

    /**
     * Resolves which flow governs the preview: explicit {@link #FLOW_NONE} →
     * none; a matching {@code flowId} → that flow; otherwise (null/blank or
     * unknown id) → the first enabled flow, or null when the agent has none.
     */
    private TurChatFlow resolveSelectedFlow(List<TurChatFlow> flows, String flowId) {
        if (FLOW_NONE.equals(flowId)) {
            return null;
        }
        if (StringUtils.hasText(flowId)) {
            Optional<TurChatFlow> match = flows.stream()
                    .filter(f -> f.getId().equals(flowId))
                    .findFirst();
            if (match.isPresent()) {
                return match.get();
            }
        }
        return flows.stream().filter(f -> f.getEnabled() == 1).findFirst().orElse(null);
    }

    private record FlowPreview(TurAgentChatFlowContext context, String nodeId,
            FlowPersonaResolution personaResolution,
            List<TurSystemPromptFlowNodeDto> flowNodes) {
    }

    /**
     * T607 — the persona the previewed turn actually speaks with, resolved the
     * way {@link com.viglet.turing.genai.persona.TurAgentPersonaResolver} does at
     * runtime.
     *
     * @param persona        the effective persona (a valid flow override, else the
     *                       agent default; may be {@code null}).
     * @param requestedId    the {@code personaId} a {@code persona}-type node on
     *                       the path requested, or {@code null} when none did.
     * @param switchedByFlow {@code true} when a flow persona node overrode the
     *                       default with a persona that is in the catalog and
     *                       usable as a speaker.
     * @param overrideMissing {@code true} when a node requested a persona the
     *                       runtime would silently ignore (not in the agent's
     *                       catalog, or audience-only) — the silent-fallback trap.
     */
    private record FlowPersonaResolution(TurPersona persona, String requestedId,
            boolean switchedByFlow, boolean overrideMissing) {
    }

    /**
     * Result of walking a flow from its start node to the first interactive
     * (anchor) node — the node whose addendum the entry turn renders — capturing
     * any persona override applied by {@code persona}-type nodes on that path.
     */
    private record FlowWalk(ChatFlowNode anchor, String personaOverrideId) {
    }

    /** Guards the transparent-node walk against a pathological/looping flow. */
    private static final int TRANSPARENT_WALK_MAX = 32;

    /** Validates operator-supplied preview variables (T611); read-only. */
    private static final ObjectMapper VARS_MAPPER = new ObjectMapper();

    /**
     * Transparent node types the preview linearly follows (first outgoing edge)
     * while walking to the entry anchor. {@code persona} is handled separately
     * (it also records the override); {@code switch}/{@code subFlow*} are left out
     * because their branch can't be resolved without runtime state — the walk
     * stops there and treats them as the anchor.
     */
    private static final Set<String> PREVIEW_TRANSPARENT_TYPES = Set.of(
            "slot", "writeSlot", "condition", "planningStep", "iteratePlan",
            "functionCall", "webhook", "scheduleAgent");

    /**
     * Synthesizes the selected flow's node runtime context so the pipeline flow
     * contributor renders the addendum exactly as the runtime would, and resolves
     * the flow-aware persona (T607). T611: when {@code nodeId} names a node in the
     * flow, that node governs (with {@code variablesJson} as its collected state)
     * instead of the entry anchor. Returns {@code null} when the flow can't be
     * parsed or has no anchor.
     */
    private FlowPreview buildFlowPreview(TurAIAgent agent, TurChatFlow flow,
            String nodeId, String variablesJson) {
        Optional<ChatFlowGraph> graphOpt = chatFlowEngineService.parseGraph(flow);
        if (graphOpt.isEmpty()) {
            return null;
        }
        ChatFlowGraph graph = graphOpt.get();

        ChatFlowNode anchor;
        String personaOverrideId;
        Optional<ChatFlowNode> picked = StringUtils.hasText(nodeId)
                ? graph.nodeById(nodeId) : Optional.empty();
        if (picked.isPresent()) {
            anchor = picked.get();
            personaOverrideId = personaOverrideUpTo(graph, nodeId);
        } else {
            FlowWalk walk = walkToAnchor(graph);
            anchor = walk.anchor();
            personaOverrideId = walk.personaOverrideId();
        }
        if (anchor == null) {
            return null;
        }

        TurChatFlowState state = new TurChatFlowState();
        state.setFlow(flow);
        state.setCurrentNodeId(anchor.id());
        state.setVariablesJson(sanitizeVariablesJson(variablesJson));
        FlowPersonaResolution personaResolution = resolvePersona(agent, personaOverrideId);
        return new FlowPreview(new TurAgentChatFlowContext(flow, graph, state, false),
                anchor.id(), personaResolution, buildFlowNodes(graph));
    }

    /** The pickable nodes of a flow (excludes structural start/end), for the UI. */
    private static List<TurSystemPromptFlowNodeDto> buildFlowNodes(ChatFlowGraph graph) {
        List<TurSystemPromptFlowNodeDto> nodes = new ArrayList<>();
        for (ChatFlowNode node : graph.nodes()) {
            if (node == null || "start".equals(node.type()) || "end".equals(node.type())) {
                continue;
            }
            String label = StringUtils.hasText(node.label()) ? node.label() : node.id();
            nodes.add(new TurSystemPromptFlowNodeDto(node.id(), label, node.type()));
        }
        return nodes;
    }

    /**
     * Walks the graph from start following first edges until it reaches
     * {@code targetId}, capturing the last {@code persona}-node override on the
     * way — so a mid-conversation node picked by the operator (T611) still
     * reflects a persona switch upstream of it. Best-effort: an override off the
     * first-edge path isn't captured.
     */
    private static String personaOverrideUpTo(ChatFlowGraph graph, String targetId) {
        Optional<ChatFlowNode> startOpt = graph.startNode();
        if (startOpt.isEmpty()) {
            return null;
        }
        String override = null;
        Set<String> visited = new HashSet<>();
        visited.add(startOpt.get().id());
        ChatFlowNode node = firstTarget(graph, startOpt.get()).orElse(null);
        int hops = 0;
        while (node != null && hops < TRANSPARENT_WALK_MAX && visited.add(node.id())) {
            if ("persona".equals(node.type()) && StringUtils.hasText(node.personaId())) {
                override = node.personaId();
            }
            if (node.id().equals(targetId)) {
                break;
            }
            node = firstTarget(graph, node).orElse(null);
            hops++;
        }
        return override;
    }

    /** Passes through a valid JSON object; falls back to empty state otherwise. */
    private static String sanitizeVariablesJson(String variablesJson) {
        if (!StringUtils.hasText(variablesJson)) {
            return "{}";
        }
        try {
            JsonNode node = VARS_MAPPER.readTree(variablesJson);
            return node != null && node.isObject() ? variablesJson : "{}";
        } catch (JacksonException e) {
            log.debug("[SystemPrompt] preview variablesJson not parseable, using empty state: {}",
                    e.getMessage());
            return "{}";
        }
    }

    /**
     * Walks the graph from its start node, skipping {@code persona} and other
     * transparent nodes (following the first outgoing edge) until it reaches the
     * first interactive node — the runtime entry anchor. Records the last
     * {@code persona}-node override seen on that path. Falls back to the first
     * node carrying an instruction when the graph has no start node.
     */
    private static FlowWalk walkToAnchor(ChatFlowGraph graph) {
        Optional<ChatFlowNode> startOpt = graph.startNode();
        if (startOpt.isEmpty()) {
            ChatFlowNode fallback = graph.nodes().stream()
                    .filter(n -> n != null && StringUtils.hasText(n.aiInstruction()))
                    .findFirst()
                    .orElse(null);
            return new FlowWalk(fallback, null);
        }
        ChatFlowNode start = startOpt.get();
        String overrideId = null;
        Set<String> visited = new HashSet<>();
        visited.add(start.id());
        ChatFlowNode node = firstTarget(graph, start).orElse(null);
        int hops = 0;
        while (node != null && hops < TRANSPARENT_WALK_MAX && visited.add(node.id())) {
            if ("persona".equals(node.type())) {
                if (StringUtils.hasText(node.personaId())) {
                    overrideId = node.personaId();
                }
                node = firstTarget(graph, node).orElse(null);
                hops++;
                continue;
            }
            if (PREVIEW_TRANSPARENT_TYPES.contains(node.type())) {
                node = firstTarget(graph, node).orElse(null);
                hops++;
                continue;
            }
            break; // interactive anchor (or a branch node we can't resolve statically)
        }
        // Walked off the end via a transparent chain — fall back to the first-hop
        // anchor so the preview still has a node to render.
        ChatFlowNode anchor = node != null ? node
                : ChatFlowOps.firstInteractiveNode(graph, start).orElse(start);
        return new FlowWalk(anchor, overrideId);
    }

    private static Optional<ChatFlowNode> firstTarget(ChatFlowGraph graph, ChatFlowNode node) {
        List<ChatFlowEdge> outgoing = graph.outgoingEdges(node.id());
        if (outgoing.isEmpty()) {
            return Optional.empty();
        }
        return graph.nodeById(outgoing.get(0).target());
    }

    /**
     * Reconciles a flow persona override id against the agent's catalog exactly
     * as {@link com.viglet.turing.genai.persona.TurAgentPersonaResolver} would:
     * honour it only when the persona is in the catalog and usable as a speaker,
     * otherwise fall back to the default and flag the silent fallback.
     */
    private FlowPersonaResolution resolvePersona(TurAIAgent agent, String overrideId) {
        TurPersona defaultPersona = agent.getDefaultPersona();
        if (!StringUtils.hasText(overrideId)) {
            return new FlowPersonaResolution(defaultPersona, null, false, false);
        }
        TurPersona match = findInCatalog(agent, overrideId);
        if (match != null && match.isUsableAsSpeaker()) {
            return new FlowPersonaResolution(match, overrideId, true, false);
        }
        // Requested but not honourable — runtime quietly uses the default too.
        return new FlowPersonaResolution(defaultPersona, overrideId, false, true);
    }

    private static TurPersona findInCatalog(TurAIAgent agent, String personaId) {
        if (agent.getPersonas() == null) {
            return null;
        }
        return agent.getPersonas().stream()
                .filter(p -> personaId.equals(p.getId()))
                .findFirst()
                .orElse(null);
    }

    /** Origin-note for the PERSONA segment, aware of a flow persona switch. */
    private static String personaNote(FlowPersonaResolution res) {
        if (res != null && res.switchedByFlow()) {
            return NOTE_PREFIX + "personaSwitched";
        }
        if (res != null && res.overrideMissing()) {
            return NOTE_PREFIX + "personaOverrideMissing";
        }
        return NOTE_PREFIX + "personaDefault";
    }

    /**
     * T607 — per-flow persona-override diagnostics for the validator. Walks every
     * flow to its entry anchor and reports any {@code persona}-node override that
     * the runtime would silently ignore (persona not in the agent catalog, or
     * audience-only). Read-only; parses each flow's cached graph.
     */
    @Transactional(readOnly = true)
    public List<FlowPersonaDiagnostic> diagnoseFlowPersonas(TurAIAgent agent) {
        List<TurChatFlow> flows = chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(agent.getId());
        List<FlowPersonaDiagnostic> diagnostics = new ArrayList<>();
        for (TurChatFlow flow : flows) {
            Optional<ChatFlowGraph> graphOpt = chatFlowEngineService.parseGraph(flow);
            if (graphOpt.isEmpty()) {
                continue;
            }
            String overrideId = walkToAnchor(graphOpt.get()).personaOverrideId();
            if (!StringUtils.hasText(overrideId)) {
                continue;
            }
            TurPersona match = findInCatalog(agent, overrideId);
            if (match == null) {
                diagnostics.add(new FlowPersonaDiagnostic(flow.getName(), overrideId, true, false));
            } else if (!match.isUsableAsSpeaker()) {
                diagnostics.add(new FlowPersonaDiagnostic(flow.getName(), match.getName(), false, true));
            }
        }
        return diagnostics;
    }

    /**
     * One flow whose {@code persona} node points at a persona the runtime cannot
     * honour. Consumed by {@link TurSystemPromptValidatorService}.
     *
     * @param flowName       the flow the misconfigured persona node lives in.
     * @param personaRef     the offending persona id (not-in-catalog case) or the
     *                       persona name (audience-only case).
     * @param notInCatalog   the referenced persona id is not in the agent catalog.
     * @param audienceOnly   the referenced persona exists but is audience-only.
     */
    public record FlowPersonaDiagnostic(String flowName, String personaRef,
            boolean notInCatalog, boolean audienceOnly) {
    }

    private List<TurSystemPromptToolDto> buildTools(TurAIAgent agent) {
        List<TurSystemPromptToolDto> tools = new ArrayList<>();
        addCustomTools(agent, tools);
        addMcpTools(agent, tools);
        addNativeTools(agent, tools);
        return tools;
    }

    private void addCustomTools(TurAIAgent agent, List<TurSystemPromptToolDto> tools) {
        if (agent.getCustomTools() == null) {
            return;
        }
        for (TurCustomTool tool : agent.getCustomTools()) {
            tools.add(new TurSystemPromptToolDto(tool.getTitle(),
                    tool.getLlmDescription(), "CUSTOM"));
        }
    }

    private void addMcpTools(TurAIAgent agent, List<TurSystemPromptToolDto> tools) {
        if (agent.getMcpServers() == null) {
            return;
        }
        for (TurMcpServer server : agent.getMcpServers()) {
            if (server.getEnabled() != 1) {
                continue;
            }
            String note = "Tool schemas are discovered from the live MCP server at runtime."
                    + (StringUtils.hasText(server.getLlmInstructions())
                            ? " Its LLM instructions are injected into the prompt above."
                            : "");
            tools.add(new TurSystemPromptToolDto(server.getTitle(), note, "MCP"));
        }
    }

    private void addNativeTools(TurAIAgent agent, List<TurSystemPromptToolDto> tools) {
        if (!StringUtils.hasText(agent.getNativeTools())) {
            return;
        }
        for (String raw : agent.getNativeTools().split(",")) {
            String name = raw.trim();
            if (!name.isEmpty()) {
                tools.add(new TurSystemPromptToolDto(name, null, "NATIVE"));
            }
        }
    }
}
