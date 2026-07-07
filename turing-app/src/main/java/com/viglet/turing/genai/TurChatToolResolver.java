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
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.clienttool.TurClientToolService;
import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
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
    private final TurClientToolService clientToolService;
    private final com.viglet.turing.genai.handoff.TurAgentHandoffService agentHandoffService;

    public TurChatToolResolver(TurNativeToolService nativeToolService,
            TurMcpToolCallbackService mcpToolCallbackService,
            TurCustomToolCallbackService customToolCallbackService,
            TurToolCallbackPipeline toolCallbackPipeline,
            TurToolPreFilterService toolPreFilterService,
            TurPersonaBrandContextProvider personaBrandContextProvider,
            TurChatPipelineObservation chatPipelineObservation,
            TurSkillRunnerService skillRunnerService,
            TurClientToolService clientToolService,
            com.viglet.turing.genai.handoff.TurAgentHandoffService agentHandoffService) {
        this.nativeToolService = nativeToolService;
        this.mcpToolCallbackService = mcpToolCallbackService;
        this.customToolCallbackService = customToolCallbackService;
        this.toolCallbackPipeline = toolCallbackPipeline;
        this.toolPreFilterService = toolPreFilterService;
        this.personaBrandContextProvider = personaBrandContextProvider;
        this.chatPipelineObservation = chatPipelineObservation;
        this.skillRunnerService = skillRunnerService;
        this.clientToolService = clientToolService;
        this.agentHandoffService = agentHandoffService;
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
    private ToolCallback[] mcpCallbacks(TurAIAgent agent, Set<String> excludeMcpServerIds) {
        Set<TurMcpServer> servers = agent.getMcpServers();
        if (servers == null || servers.isEmpty()) {
            return new ToolCallback[0];
        }
        // T145 / §X.5.b — when a native turn federates some of the agent's MCP
        // servers to the vendor (OpenAI remote mcp / Anthropic connector), those
        // servers are excluded here so they aren't ALSO wired as Turing MCP
        // client tools. COMMAND (stdio) servers — never federated — stay.
        Set<TurMcpServer> effective = servers;
        if (excludeMcpServerIds != null && !excludeMcpServerIds.isEmpty()) {
            effective = servers.stream()
                    .filter(s -> s != null && !excludeMcpServerIds.contains(s.getId()))
                    .collect(java.util.stream.Collectors.toSet());
            if (effective.isEmpty()) {
                return new ToolCallback[0];
            }
        }
        return mcpToolCallbackService.getToolCallbacks(effective);
    }

    private ToolCallback[] customCallbacks(TurAIAgent agent) {
        return agent.getCustomTools() == null || agent.getCustomTools().isEmpty()
                ? new ToolCallback[0]
                : customToolCallbackService.getToolCallbacks(agent.getCustomTools());
    }

    /**
     * T323 / §IX.4.d — skill delegation. When the operator enabled skills on
     * this agent AND the delegation layer is available (storage + Code
     * Interpreter DOCKER + a Default LLM), add the single {@code run_skill} tool
     * so the model can delegate a task to a skill. The skill runs on the Global
     * Settings Default LLM, not this agent's model. Empty for default-off
     * agents or when the layer is unavailable.
     *
     * <p>T325 / §IX.4.d — honour an optional skill-mode pin: when the caller
     * selected a single skill for this turn, offer only that one; otherwise the
     * full enabled set is offered for progressive disclosure (legacy).
     */
    private ToolCallback[] skillCallbacks(TurAIAgent agent, String selectedSkillId) {
        return agent.isSkillsEnabled() && skillRunnerService.isAvailable()
                ? skillRunnerService.buildToolCallbacks(
                        skillRunnerService.offeredSkills(selectedSkillId))
                : new ToolCallback[0];
    }

    /**
     * Belt-and-suspenders: native tools (operator-curated) and the active flow
     * node's requiredTools (flow-author declared) bypass the BM25 filter. The
     * skill delegation tool is infrastructure (one per turn, not a domain tool)
     * — kept regardless of BM25 relevance to the message.
     */
    private Set<String> buildAlwaysKeep(Set<String> selectedNativeTools,
            ToolCallback[] skillCallbacks, ChatFlowNode activeNode) {
        Set<String> alwaysKeep = new HashSet<>();
        alwaysKeep.addAll(selectedNativeTools);
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
        return alwaysKeep;
    }

    /**
     * Matches the "trigger words" line of an MCP server's {@code llmInstructions}
     * — {@code Palavras-gatilho: …} / {@code Trigger words: …} /
     * {@code Keywords: …} — capturing the comma/semicolon list that follows.
     * Single-line (the list lives on one line); locale-insensitive on the label.
     */
    private static final Pattern TRIGGER_LINE = Pattern.compile(
            "(?im)(?:palavras?[ -]?gatilhos?|gatilhos?|trigger\\s*words?|keywords?)\\s*:?\\s*(.+)");

    /**
     * T424 / §XXI part (b) — names of the tools belonging to attached MCP
     * servers whose declared trigger words (or title tokens) appear in the
     * user message. These are unioned into the pre-filter's always-keep set so
     * an intent-matched server's tools are never dropped, steering weak models
     * to the right namespace instead of a semantically-close built-in.
     *
     * <p>Per-server so a multi-server agent only boosts the server the message
     * actually hit. The {@code getToolCallbacks(Set.of(server))} call lists the
     * matched server's tools (cheap on the already-open MCP client); it only
     * fires on a trigger match, so unrelated turns pay nothing.
     *
     * <p>Package-private for unit testing in isolation.
     */
    Set<String> triggerBoostedMcpToolNames(TurAIAgent agent, String userMessage) {
        Set<TurMcpServer> servers = agent.getMcpServers();
        if (servers == null || servers.isEmpty() || userMessage == null || userMessage.isBlank()) {
            return Set.of();
        }
        String haystack = userMessage.toLowerCase(Locale.ROOT);
        Set<String> boosted = new HashSet<>();
        for (TurMcpServer server : servers) {
            if (server != null && server.getEnabled() == 1 && serverTriggersMatch(server, haystack)) {
                addServerToolNames(server, boosted);
                log.info("[ToolBoost] MCP server '{}' triggers matched — boosting its tools",
                        server.getTitle());
            }
        }
        return boosted;
    }

    /** Collects the (non-blank) tool names exposed by one MCP server into {@code sink}. */
    private void addServerToolNames(TurMcpServer server, Set<String> sink) {
        for (ToolCallback cb : mcpToolCallbackService.getToolCallbacks(Set.of(server))) {
            try {
                String name = cb.getToolDefinition().name();
                if (name != null && !name.isBlank()) {
                    sink.add(name);
                }
            } catch (RuntimeException e) {
                log.debug("[ToolBoost] could not read MCP tool name: {}", e.getMessage());
            }
        }
    }

    /** True when any of {@code server}'s trigger phrases / title tokens occur in {@code lowerMessage}. */
    private static boolean serverTriggersMatch(TurMcpServer server, String lowerMessage) {
        for (String trigger : extractTriggers(server)) {
            if (trigger.length() >= 3 && lowerMessage.contains(trigger)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Trigger phrases for a server: the comma/semicolon list from a
     * {@link #TRIGGER_LINE} in {@code llmInstructions} (lowercased), plus the
     * distinctive tokens (≥ 4 chars) of the server title as a fallback so a
     * server without an explicit trigger line still boosts on its own name.
     */
    private static Set<String> extractTriggers(TurMcpServer server) {
        Set<String> triggers = new HashSet<>();
        String instructions = server.getLlmInstructions();
        if (instructions != null && !instructions.isBlank()) {
            Matcher m = TRIGGER_LINE.matcher(instructions);
            if (m.find()) {
                for (String part : m.group(1).split("[,;]")) {
                    String t = part.trim().toLowerCase(Locale.ROOT);
                    if (t.length() >= 3) {
                        triggers.add(t);
                    }
                }
            }
        }
        String title = server.getTitle();
        if (title != null && !title.isBlank()) {
            for (String token : title.toLowerCase(Locale.ROOT).split("\\s+")) {
                String clean = token.replaceAll("[^\\p{L}\\p{Nd}]", "");
                if (clean.length() >= 4) {
                    triggers.add(clean);
                }
            }
        }
        return triggers;
    }

    public ToolCallback[] resolve(TurAIAgent agent, TurPersona activePersona,
            String userMessage, ChatFlowNode activeNode, String selectedSkillId) {
        return resolve(agent, activePersona, userMessage, activeNode, selectedSkillId, Set.of());
    }

    /**
     * T145 / §X.5.b overload — same as
     * {@link #resolve(TurAIAgent, TurPersona, String, ChatFlowNode, String)} but
     * excludes the given MCP server ids from the Turing MCP <em>client</em> path.
     * The native chat executor passes the ids of the agent's MCP servers it
     * federated to the vendor this turn, so they aren't double-wired (vendor
     * connector + Turing client). Empty set = no exclusion (every caller below
     * the native path).
     */
    public ToolCallback[] resolve(TurAIAgent agent, TurPersona activePersona,
            String userMessage, ChatFlowNode activeNode, String selectedSkillId,
            Set<String> excludeMcpServerIds) {
        long t0 = System.currentTimeMillis();
        Set<String> selectedNativeTools = parseNativeTools(agent.getNativeTools());
        ToolCallback[] nativeCallbacks = selectedNativeTools.isEmpty()
                ? new ToolCallback[0]
                : nativeToolService.getToolCallbacks(selectedNativeTools);
        ToolCallback[] mcpCallbacks = mcpCallbacks(agent, excludeMcpServerIds);
        ToolCallback[] customCallbacks = customCallbacks(agent);
        ToolCallback[] personaBrandCallbacks =
                personaBrandContextProvider.getToolCallbacks(activePersona);
        ToolCallback[] skillCallbacks = skillCallbacks(agent, selectedSkillId);
        // T438 — frontend ("client") tools. Advertised to the model so it can
        // call them; the call parks the turn (it is never run server-side).
        ToolCallback[] clientCallbacks = clientToolService.isEnabled(agent)
                ? clientToolService.buildToolCallbacks(agent)
                : new ToolCallback[0];
        // T448 — agent-to-agent handoff: the router's delegate_to_agent tool.
        ToolCallback[] handoffCallbacks = agentHandoffService.buildToolCallbacks(agent);
        ToolCallback[] decorated = toolCallbackPipeline.decorate(
                Stream.of(nativeCallbacks, mcpCallbacks, customCallbacks, personaBrandCallbacks,
                        skillCallbacks, clientCallbacks, handoffCallbacks)
                        .flatMap(Arrays::stream)
                        .toArray(ToolCallback[]::new));
        Set<String> alwaysKeep = buildAlwaysKeep(selectedNativeTools, skillCallbacks, activeNode);
        // Client tools are author-declared (not domain-relevance ranked) — keep
        // them past the BM25 pre-filter so the model always sees what it may call.
        alwaysKeep.addAll(clientToolService.namesFor(agent));
        // T448 — keep delegate_to_agent past the pre-filter so the router always
        // sees it (it's configured, not domain-relevance ranked).
        if (handoffCallbacks.length > 0) {
            alwaysKeep.add(com.viglet.turing.genai.handoff.TurDelegateAgentToolCallback.TOOL_NAME);
        }
        // T424 / §XXI part (b) — trigger boost: when the user message hits an
        // attached MCP server's declared trigger words (or its title), promote
        // that server's tools into the always-keep set so the pre-filter can
        // never drop them and the model is steered to that namespace.
        alwaysKeep.addAll(triggerBoostedMcpToolNames(agent, userMessage));
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
