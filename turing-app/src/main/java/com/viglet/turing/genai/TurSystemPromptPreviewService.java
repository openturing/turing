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
import com.viglet.turing.genai.persona.TurPersonaPromptComposer;
import com.viglet.turing.genai.persona.TurPersonaStaticPromptCache;
import com.viglet.turing.genai.tool.TurMcpInstructionsProvider;
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
 * Builds a legible, segmented preview of the system prompt an AI Agent sends
 * to the LLM. The runtime ({@link TurChatPromptAssembler} +
 * {@link TurPersonaPromptComposer}) fuses several sources into one opaque
 * system message; this service reverses that into labeled
 * {@link TurSystemPromptSegmentDto segments} so the admin "System Prompt" page
 * can show the operator <em>what comes from where</em>.
 *
 * <p>The operator picks which chat flow governs the previewed turn ({@code
 * flowId}); the selected flow's entry-node addendum is folded into the
 * assembled prompt exactly as the runtime would. Fragments that depend on the
 * live message (persona few-shot retrieval) or on the SN-site RAG path remain
 * runtime-only example segments and stay out of {@code assembledText}.
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

    static final String ORIGIN_PERSONA = "PERSONA";
    static final String ORIGIN_AGENT = "AGENT";
    static final String ORIGIN_MCP = "MCP";
    static final String ORIGIN_FLOW = "FLOW";
    static final String ORIGIN_FEW_SHOT = "FEW_SHOT";
    static final String ORIGIN_RAG = "RAG";

    private final TurMcpInstructionsProvider mcpInstructionsProvider;
    private final TurPersonaStaticPromptCache personaStaticPromptCache;
    private final TurPersonaPromptComposer personaPromptComposer;
    private final TurChatFlowEngineService chatFlowEngineService;
    private final TurChatFlowRepository chatFlowRepository;

    public TurSystemPromptPreviewService(TurMcpInstructionsProvider mcpInstructionsProvider,
            TurPersonaStaticPromptCache personaStaticPromptCache,
            TurPersonaPromptComposer personaPromptComposer,
            TurChatFlowEngineService chatFlowEngineService,
            TurChatFlowRepository chatFlowRepository) {
        this.mcpInstructionsProvider = mcpInstructionsProvider;
        this.personaStaticPromptCache = personaStaticPromptCache;
        this.personaPromptComposer = personaPromptComposer;
        this.chatFlowEngineService = chatFlowEngineService;
        this.chatFlowRepository = chatFlowRepository;
    }

    /** Convenience overload — previews the default flow (first enabled). */
    @Transactional(readOnly = true)
    public TurSystemPromptPreviewDto buildPreview(TurAIAgent agent) {
        return buildPreview(agent, null);
    }

    /**
     * Compose the preview for {@code agent}, with the {@code flowId} flow
     * governing the turn ({@code null} = default to the first enabled flow,
     * {@link #FLOW_NONE} = no flow). {@code @Transactional(readOnly)} keeps a
     * session open so lazy associations resolve.
     */
    @Transactional(readOnly = true)
    public TurSystemPromptPreviewDto buildPreview(TurAIAgent agent, String flowId) {
        boolean usesDefault = !StringUtils.hasText(agent.getSystemPrompt());
        String base = usesDefault ? DEFAULT_SYSTEM_PROMPT.strip() : agent.getSystemPrompt();
        String mcp = mcpInstructionsProvider.buildSystemPromptBlock(agent.getMcpServers());
        TurPersona persona = agent.getDefaultPersona();
        String personaBlock = persona == null ? "" : personaStaticPromptCache.composeStaticBlock(persona);

        List<TurChatFlow> flows = chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(agent.getId());
        List<TurSystemPromptFlowOptionDto> flowOptions = flows.stream()
                .map(f -> new TurSystemPromptFlowOptionDto(f.getId(), f.getName(), f.getEnabled() == 1))
                .toList();
        TurChatFlow selectedFlow = resolveSelectedFlow(flows, flowId);
        FlowAddendum flowAddendum = selectedFlow == null ? null : buildFlowAddendum(selectedFlow);
        String flowText = flowAddendum == null ? "" : flowAddendum.text();

        List<TurSystemPromptSegmentDto> segments = new ArrayList<>();

        // Order mirrors the runtime: the persona block wraps the base, so it
        // is emitted first, then the agent base prompt, then MCP, then the
        // selected flow's addendum.
        if (persona != null && StringUtils.hasText(personaBlock)) {
            segments.add(new TurSystemPromptSegmentDto(ORIGIN_PERSONA,
                    persona.getName(), personaBlock, true, false,
                    "Default persona — applies on every turn unless a chat flow switches it."));
            if (persona.getFewShotStore() != null) {
                segments.add(new TurSystemPromptSegmentDto(ORIGIN_FEW_SHOT,
                        persona.getName(), "", false, true,
                        "Few-shot examples are retrieved at runtime by similarity to the user's message — not fixed text."));
            }
        }

        segments.add(new TurSystemPromptSegmentDto(ORIGIN_AGENT,
                null, base, true, false,
                usesDefault
                        ? "Fallback default — this agent has no system prompt of its own."
                        : null));

        if (StringUtils.hasText(mcp)) {
            segments.add(new TurSystemPromptSegmentDto(ORIGIN_MCP,
                    null, mcp, true, false,
                    "Injected from each enabled MCP server's LLM instructions."));
        }

        if (selectedFlow != null && flowAddendum != null && StringUtils.hasText(flowText)) {
            segments.add(new TurSystemPromptSegmentDto(ORIGIN_FLOW,
                    selectedFlow.getName() + (flowAddendum.nodeId() == null ? "" : " → " + flowAddendum.nodeId()),
                    flowText, true, false,
                    "From the selected chat flow. The runtime picks the active node by conversation state — this shows the entry node."));
        }

        segments.add(new TurSystemPromptSegmentDto(ORIGIN_RAG,
                null, "", false, true,
                "When invoked through an SN site in AI (RAG) mode, the agent base prompt is replaced by a retrieval-augmented prompt carrying the matched knowledge chunks."));

        // assembledText = the real system message for the previewed scenario:
        // persona wraps (base + mcp + selected flow addendum). Pass null
        // embedding model + null query so the composer skips few-shot retrieval.
        String assembledText = personaPromptComposer.compose(persona, null, null, base + mcp + flowText);

        return new TurSystemPromptPreviewDto(segments, buildTools(agent), assembledText,
                flowOptions, selectedFlow == null ? null : selectedFlow.getId());
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

    private record FlowAddendum(String nodeId, String text) {
    }

    /**
     * Builds the selected flow's entry-node addendum exactly as the runtime
     * would, by synthesizing a fresh state at the first interactive node and
     * invoking the flow's guardrail strategy. Falls back to the node's raw
     * {@code aiInstruction} if the strategy can't produce text.
     */
    private FlowAddendum buildFlowAddendum(TurChatFlow flow) {
        Optional<ChatFlowGraph> graphOpt = chatFlowEngineService.parseGraph(flow);
        if (graphOpt.isEmpty()) {
            return null;
        }
        ChatFlowGraph graph = graphOpt.get();
        ChatFlowNode anchor = resolveAnchorNode(graph);
        if (anchor == null) {
            return null;
        }
        try {
            TurChatFlowState state = new TurChatFlowState();
            state.setFlow(flow);
            state.setCurrentNodeId(anchor.id());
            state.setVariablesJson("{}");
            String addendum = chatFlowEngineService.buildSystemPromptAddendum(flow, state, graph);
            if (StringUtils.hasText(addendum)) {
                return new FlowAddendum(anchor.id(), addendum.strip());
            }
        } catch (RuntimeException e) {
            log.debug("[SystemPrompt] flow addendum preview failed for '{}': {}",
                    flow.getName(), e.getMessage());
        }
        return StringUtils.hasText(anchor.aiInstruction())
                ? new FlowAddendum(anchor.id(), anchor.aiInstruction().strip())
                : null;
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
        if (agent.getCustomTools() != null) {
            for (TurCustomTool tool : agent.getCustomTools()) {
                tools.add(new TurSystemPromptToolDto(tool.getTitle(),
                        tool.getLlmDescription(), "CUSTOM"));
            }
        }
        if (agent.getMcpServers() != null) {
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
        if (StringUtils.hasText(agent.getNativeTools())) {
            for (String raw : agent.getNativeTools().split(",")) {
                String name = raw.trim();
                if (!name.isEmpty()) {
                    tools.add(new TurSystemPromptToolDto(name, null, "NATIVE"));
                }
            }
        }
        return tools;
    }
}
