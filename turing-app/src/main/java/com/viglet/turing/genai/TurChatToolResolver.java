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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.persona.TurPersonaBrandContextProvider;
import com.viglet.turing.genai.skill.activation.TurRunSkillToolCallback;
import com.viglet.turing.genai.skill.activation.TurSkillRunnerService;
import com.viglet.turing.genai.tool.TurCustomToolCallbackService;
import com.viglet.turing.genai.tool.TurMcpToolCallbackService;
import com.viglet.turing.genai.tool.TurNativeToolService;
import com.viglet.turing.genai.tool.TurToolCallbackPipeline;
import com.viglet.turing.genai.tool.TurToolPreFilterService;
import com.viglet.turing.observability.TurChatPipelineObservation;
import com.viglet.turing.observability.TurMeterNames;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.persona.TurPersona;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the union of native + MCP + custom + persona-brand tool
 * callbacks attached to an agent, decorates them through the
 * {@link TurToolCallbackPipeline} (mandatory) and runs the §IV.2 / T29
 * BM25 pre-filter when a user message is in hand.
 *
 * <p>Extracted from {@link TurAgentChatExecutor} in T33 to keep the
 * executor's orchestrator method focused on the per-turn dance. The
 * collaborator owns the {@link TurMeterNames#STAGE_CHAT_SETUP_TOOLS}
 * substage timing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurChatToolResolver {

    private final TurNativeToolService nativeToolService;
    private final TurMcpToolCallbackService mcpToolCallbackService;
    private final TurCustomToolCallbackService customToolCallbackService;
    private final TurToolCallbackPipeline toolCallbackPipeline;
    private final TurToolPreFilterService toolPreFilterService;
    private final TurPersonaBrandContextProvider personaBrandContextProvider;
    private final TurChatPipelineObservation chatPipelineObservation;
    private final TurSkillRunnerService skillRunnerService;

    public TurChatToolResolver(TurNativeToolService nativeToolService,
            TurMcpToolCallbackService mcpToolCallbackService,
            TurCustomToolCallbackService customToolCallbackService,
            TurToolCallbackPipeline toolCallbackPipeline,
            TurToolPreFilterService toolPreFilterService,
            TurPersonaBrandContextProvider personaBrandContextProvider,
            TurChatPipelineObservation chatPipelineObservation,
            TurSkillRunnerService skillRunnerService) {
        this.nativeToolService = nativeToolService;
        this.mcpToolCallbackService = mcpToolCallbackService;
        this.customToolCallbackService = customToolCallbackService;
        this.toolCallbackPipeline = toolCallbackPipeline;
        this.toolPreFilterService = toolPreFilterService;
        this.personaBrandContextProvider = personaBrandContextProvider;
        this.chatPipelineObservation = chatPipelineObservation;
        this.skillRunnerService = skillRunnerService;
    }

    /**
     * Legacy overload — no user message, no pre-filter. Kept for the
     * public executor API at
     * {@link TurAgentChatExecutor#resolveToolCallbacks(TurAIAgent, TurPersona)}
     * and any non-chat surface that doesn't have a user message in hand.
     */
    public ToolCallback[] resolve(TurAIAgent agent, TurPersona activePersona) {
        return resolve(agent, activePersona, null, null, null);
    }

    /**
     * Resolve + pipeline-decorate + (optionally) BM25 pre-filter the
     * tool callbacks visible to an agent on this turn.
     *
     * <p>Records the
     * {@link TurMeterNames#STAGE_CHAT_SETUP_TOOLS} substage timing so the
     * executor's parent {@link TurMeterNames#STAGE_CHAT_SETUP} aggregate
     * still adds up correctly across the per-collaborator breakdown.
     */
    public ToolCallback[] resolve(TurAIAgent agent, TurPersona activePersona,
            String userMessage, ChatFlowNode activeNode) {
        return resolve(agent, activePersona, userMessage, activeNode, null);
    }

    /**
     * T325 overload — same as
     * {@link #resolve(TurAIAgent, TurPersona, String, ChatFlowNode)} but with an
     * optional {@code selectedSkillId} that pins the turn to a single skill (the
     * "skill mode"). When blank/null the full enabled skill set is offered for
     * progressive disclosure (legacy); when set, only the pinned skill's
     * {@code run_skill} tool is exposed.
     *
     * @since 2026.3.1
     */
    public ToolCallback[] resolve(TurAIAgent agent, TurPersona activePersona,
            String userMessage, ChatFlowNode activeNode, String selectedSkillId) {
        long t0 = System.currentTimeMillis();
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
        // T323 / §IX.4.d — skill delegation. When the operator enabled skills on
        // this agent AND the delegation layer is available (storage + Code
        // Interpreter DOCKER + a Default LLM), add the single `run_skill` tool so
        // the model can delegate a task to a skill. The skill runs on the Global
        // Settings Default LLM, not this agent's model. Empty for default-off
        // agents or when the layer is unavailable.
        // T325 / §IX.4.d — honour an optional skill-mode pin: when the caller
        // selected a single skill for this turn, offer only that one; otherwise
        // the full enabled set is offered for progressive disclosure (legacy).
        ToolCallback[] skillCallbacks =
                agent.isSkillsEnabled() && skillRunnerService.isAvailable()
                        ? skillRunnerService.buildToolCallbacks(
                                skillRunnerService.offeredSkills(selectedSkillId))
                        : new ToolCallback[0];
        ToolCallback[] decorated = toolCallbackPipeline.decorate(
                Stream.of(nativeCallbacks, mcpCallbacks, customCallbacks, personaBrandCallbacks,
                        skillCallbacks)
                        .flatMap(Arrays::stream)
                        .toArray(ToolCallback[]::new));
        // Belt-and-suspenders: native tools (operator-curated) and the
        // active flow node's requiredTools (flow-author declared) bypass
        // the BM25 filter. Persona brand-context tool also bypasses
        // implicitly via its name being in alwaysKeep would be nicer, but
        // its name isn't exposed at this layer — the filter's small-pool
        // bypass + the fact that brand tools have rich descriptions keeps
        // them in the top-K in practice.
        Set<String> alwaysKeep = new HashSet<>();
        alwaysKeep.addAll(selectedNativeTools);
        // The skill delegation tool is infrastructure (one per turn, not a
        // domain tool) — keep it regardless of BM25 relevance to the message.
        if (skillCallbacks.length > 0) {
            alwaysKeep.add(TurRunSkillToolCallback.TOOL_NAME);
        }
        if (activeNode != null) {
            for (String required : activeNode.requiredTools()) {
                if (required != null && !required.isBlank()) {
                    alwaysKeep.add(required.trim());
                }
            }
        }
        ToolCallback[] filtered = toolPreFilterService.filter(decorated, userMessage, alwaysKeep);
        long elapsed = System.currentTimeMillis() - t0;
        chatPipelineObservation.recordMillis(TurMeterNames.STAGE_CHAT_SETUP_TOOLS, elapsed);
        log.info("[AgentExec] Agent '{}': {} tool callbacks", agent.getTitle(), filtered.length);
        if (log.isDebugEnabled()) {
            for (ToolCallback cb : filtered) {
                log.debug("[AgentExec] Available tool: '{}'", cb.getToolDefinition().name());
            }
        }
        return filtered;
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
     * {@code REGEN_HISTORY_TURN_BUDGET}.
     *
     * @since 2026.2.7
     */
    public static boolean forbidsToolCalls(ChatFlowNode node) {
        return node != null && Boolean.FALSE.equals(node.toolsEnabled());
    }

    static Set<String> parseNativeTools(String nativeTools) {
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
}
