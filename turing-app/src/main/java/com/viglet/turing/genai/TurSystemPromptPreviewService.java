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
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;

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

    public TurSystemPromptPreviewService(TurPromptAssemblyPipeline promptAssemblyPipeline,
            TurChatFlowEngineService chatFlowEngineService,
            TurChatFlowRepository chatFlowRepository) {
        this.promptAssemblyPipeline = promptAssemblyPipeline;
        this.chatFlowEngineService = chatFlowEngineService;
        this.chatFlowRepository = chatFlowRepository;
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
        TurPersona persona = agent.getDefaultPersona();

        List<TurChatFlow> flows = chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(agent.getId());
        List<TurSystemPromptFlowOptionDto> flowOptions = flows.stream()
                .map(f -> new TurSystemPromptFlowOptionDto(f.getId(), f.getName(), f.getEnabled() == 1))
                .toList();
        TurChatFlow selectedFlow = resolveSelectedFlow(flows, flowId);

        // Synthesize the selected flow's entry-node state so the pipeline's flow
        // contributor produces the exact addendum the runtime would at that node
        // — no separate lookalike build.
        FlowPreview flowPreview = selectedFlow == null ? null : buildFlowPreview(selectedFlow);
        TurAgentChatFlowContext flowContext = flowPreview == null ? null : flowPreview.context();

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

        return new TurSystemPromptPreviewDto(segments, buildTools(agent), assembly.systemText(),
                flowOptions, selectedFlow == null ? null : selectedFlow.getId(),
                assembly.totalTokens(), assembly.stableTokens(), assembly.cacheBreakpointIndex());
    }

    /**
     * Maps one pipeline segment to its preview DTO — carrying the token estimate
     * and stability class through, and attaching the origin-specific note the UI
     * shows.
     */
    private TurSystemPromptSegmentDto toDto(TurPromptSegment seg, boolean usesDefault,
            FlowPreview flowPreview) {
        String title = seg.title();
        String note = switch (seg.origin()) {
            case TurPromptSegment.ORIGIN_PERSONA ->
                "Default persona — applies on every turn unless a chat flow switches it.";
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

    private record FlowPreview(TurAgentChatFlowContext context, String nodeId) {
    }

    /**
     * Synthesizes the selected flow's entry-node runtime context so the pipeline
     * flow contributor renders the addendum exactly as the runtime would. Returns
     * {@code null} when the flow can't be parsed or has no interactive anchor.
     */
    private FlowPreview buildFlowPreview(TurChatFlow flow) {
        Optional<ChatFlowGraph> graphOpt = chatFlowEngineService.parseGraph(flow);
        if (graphOpt.isEmpty()) {
            return null;
        }
        ChatFlowGraph graph = graphOpt.get();
        ChatFlowNode anchor = resolveAnchorNode(graph);
        if (anchor == null) {
            return null;
        }
        TurChatFlowState state = new TurChatFlowState();
        state.setFlow(flow);
        state.setCurrentNodeId(anchor.id());
        state.setVariablesJson("{}");
        return new FlowPreview(new TurAgentChatFlowContext(flow, graph, state, false), anchor.id());
    }

    private static ChatFlowNode resolveAnchorNode(ChatFlowGraph graph) {
        Optional<ChatFlowNode> start = graph.startNode();
        if (start.isPresent()) {
            ChatFlowNode anchor = ChatFlowOps.firstInteractiveNode(graph, start.get()).orElse(start.get());
            if (anchor != null) {
                return anchor;
            }
        }
        // Fallback: first node that carries an instruction.
        return graph.nodes().stream()
                .filter(n -> n != null && StringUtils.hasText(n.aiInstruction()))
                .findFirst()
                .orElse(null);
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
