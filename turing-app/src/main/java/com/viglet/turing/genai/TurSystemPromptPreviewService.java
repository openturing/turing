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
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.flow.ChatFlowEdge;
import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.genai.prompt.TurPromptAssembly;
import com.viglet.turing.genai.prompt.TurPromptAssemblyContext;
import com.viglet.turing.genai.prompt.TurPromptAssemblyPipeline;
import com.viglet.turing.genai.prompt.TurPromptSegment;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptFlowOptionDto;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptPreviewDto;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptSegmentDto;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptToolDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.model.customtool.TurCustomTool;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.service.llm.price.TurLLMPriceService;

import lombok.extern.slf4j.Slf4j;

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

    private final TurPromptAssemblyPipeline promptAssemblyPipeline;
    private final TurChatFlowEngineService chatFlowEngineService;
    private final TurChatFlowRepository chatFlowRepository;
    private final TurLLMPriceService priceService;

    public TurSystemPromptPreviewService(TurPromptAssemblyPipeline promptAssemblyPipeline,
            TurChatFlowEngineService chatFlowEngineService,
            TurChatFlowRepository chatFlowRepository,
            TurLLMPriceService priceService) {
        this.promptAssemblyPipeline = promptAssemblyPipeline;
        this.chatFlowEngineService = chatFlowEngineService;
        this.chatFlowRepository = chatFlowRepository;
        this.priceService = priceService;
    }

    /** Convenience overload — previews the default flow (first enabled). */
    @Transactional(readOnly = true)
    public TurSystemPromptPreviewDto buildPreview(TurAIAgent agent) {
        return buildPreviewInternal(agent, null);
    }

    /**
     * Compose the preview for {@code agent}, with the {@code flowId} flow
     * governing the turn ({@code null} = default to the first enabled flow,
     * {@link #FLOW_NONE} = no flow). {@code @Transactional(readOnly)} keeps a
     * session open so lazy associations resolve.
     */
    @Transactional(readOnly = true)
    public TurSystemPromptPreviewDto buildPreview(TurAIAgent agent, String flowId) {
        return buildPreviewInternal(agent, flowId);
    }

    private TurSystemPromptPreviewDto buildPreviewInternal(TurAIAgent agent, String flowId) {
        boolean usesDefault = !StringUtils.hasText(agent.getSystemPrompt());
        String base = usesDefault ? DEFAULT_SYSTEM_PROMPT.strip() : agent.getSystemPrompt();

        List<TurChatFlow> flows = chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(agent.getId());
        List<TurSystemPromptFlowOptionDto> flowOptions = flows.stream()
                .map(f -> new TurSystemPromptFlowOptionDto(f.getId(), f.getName(), f.getEnabled() == 1))
                .toList();
        TurChatFlow selectedFlow = resolveSelectedFlow(flows, flowId);

        // Synthesize the selected flow's entry-node state so the pipeline's flow
        // contributor produces the exact addendum the runtime would at that node
        // — no separate lookalike build.
        FlowPreview flowPreview = selectedFlow == null ? null : buildFlowPreview(agent, selectedFlow);
        TurAgentChatFlowContext flowContext = flowPreview == null ? null : flowPreview.context();

        // T607 — resolve the persona the way the runtime does: a persona-type
        // node on the path to the entry anchor switches the active voice via
        // __activePersonaId (mirroring TurAgentPersonaResolver). Fall back to the
        // agent default when no flow governs the turn or none applies.
        FlowPersonaResolution personaResolution = flowPreview == null
                ? null : flowPreview.personaResolution();
        TurPersona persona = personaResolution != null
                ? personaResolution.persona() : agent.getDefaultPersona();

        // ONE assembly, shared with the runtime. Null embedding model + null
        // query so the persona contributor skips live few-shot retrieval; null
        // selectedSkillId offers all enabled skills, as a plain turn would.
        TurPromptAssemblyContext assemblyContext = new TurPromptAssemblyContext(
                agent, persona, null, null, base, flowContext, null);
        TurPromptAssembly assembly = promptAssemblyPipeline.assemble(assemblyContext);

        List<TurSystemPromptSegmentDto> segments = new ArrayList<>();
        for (TurPromptSegment seg : assembly.segments()) {
            segments.add(toDto(seg, usesDefault, flowPreview));
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
                estimatedCostUsd, costModelName);
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
            FlowPreview flowPreview) {
        String title = seg.title();
        FlowPersonaResolution personaRes = flowPreview == null ? null : flowPreview.personaResolution();
        String note = switch (seg.origin()) {
            case TurPromptSegment.ORIGIN_PERSONA -> personaNote(personaRes);
            case TurPromptSegment.ORIGIN_AGENT -> usesDefault
                    ? "Fallback default — this agent has no system prompt of its own."
                    : null;
            case TurPromptSegment.ORIGIN_MCP ->
                "Injected from each enabled MCP server's LLM instructions.";
            case TurPromptSegment.ORIGIN_CAPABILITY ->
                "Opt-in capability guidance appended to the agent prompt.";
            case TurPromptSegment.ORIGIN_SKILL ->
                "Skill catalog (progressive disclosure) — the run_skill delegation surface.";
            case TurPromptSegment.ORIGIN_FLOW ->
                "From the selected chat flow. The runtime picks the active node by conversation state — this shows the entry node.";
            default -> null;
        };
        if (TurPromptSegment.ORIGIN_FLOW.equals(seg.origin())
                && flowPreview != null && flowPreview.nodeId() != null) {
            title = (title == null ? "" : title) + " → " + flowPreview.nodeId();
        }
        return new TurSystemPromptSegmentDto(seg.origin(), title, seg.displayText(),
                true, false, note, seg.tokens(), seg.stability().name());
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
            FlowPersonaResolution personaResolution) {
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
     * Synthesizes the selected flow's entry-node runtime context so the pipeline
     * flow contributor renders the addendum exactly as the runtime would, and
     * resolves the flow-aware persona (T607). Returns {@code null} when the flow
     * can't be parsed or has no interactive anchor.
     */
    private FlowPreview buildFlowPreview(TurAIAgent agent, TurChatFlow flow) {
        Optional<ChatFlowGraph> graphOpt = chatFlowEngineService.parseGraph(flow);
        if (graphOpt.isEmpty()) {
            return null;
        }
        ChatFlowGraph graph = graphOpt.get();
        FlowWalk walk = walkToAnchor(graph);
        if (walk.anchor() == null) {
            return null;
        }
        TurChatFlowState state = new TurChatFlowState();
        state.setFlow(flow);
        state.setCurrentNodeId(walk.anchor().id());
        state.setVariablesJson("{}");
        FlowPersonaResolution personaResolution = resolvePersona(agent, walk.personaOverrideId());
        return new FlowPreview(new TurAgentChatFlowContext(flow, graph, state, false),
                walk.anchor().id(), personaResolution);
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
            return "Switched by the selected flow's persona node — this is the voice for the "
                    + "previewed turn, not the agent's default persona.";
        }
        if (res != null && res.overrideMissing()) {
            return "The selected flow has a persona node pointing at a persona not in this "
                    + "agent's catalog — the runtime silently falls back to the default (shown "
                    + "here). Fix the node's persona or add it to the agent.";
        }
        return "Default persona — applies on every turn unless a chat flow switches it.";
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
