/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.mcp.server;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.genai.TurAgentChatRequest;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.persistence.dto.agent.TurChatSessionSlotsDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T249 / §XIII.3 — the {@code list_agents} and {@code invoke_agent} MCP tools.
 * They let an external orchestrator discover the customer's configured AI Agents
 * (chat-flows) and run one headlessly, getting back the final answer plus any
 * captured slots — so a carefully-authored lead-capture / triage flow can be
 * reused from <em>outside</em> the Turing chat widget.
 *
 * <p><b>Reuse, don't fork (§XIII.5)</b>: invocation routes through the same
 * {@link TurAgentChatExecutor} the public AI-Agent chat endpoint uses (same flow
 * engine, persona voice, RAG grounding, tools), and slots are read through the
 * same {@link TurChatFlowEngineService#listSlotsForConversation(String)} the SDK
 * polls. The only adaptation is collecting the streamed {@code Flux} into one
 * synchronous answer for the request/response shape MCP expects.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurMcpAgentToolService {

    /** Cap a headless turn so a stuck model can't hold the MCP request open forever. */
    private static final Duration INVOKE_TIMEOUT = Duration.ofSeconds(120);

    private final TurAIAgentRepository turAIAgentRepository;
    private final TurAgentChatExecutor agentChatExecutor;
    private final TurChatFlowEngineService chatFlowEngineService;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public TurMcpAgentToolService(TurAIAgentRepository turAIAgentRepository,
            TurAgentChatExecutor agentChatExecutor,
            TurChatFlowEngineService chatFlowEngineService) {
        this.turAIAgentRepository = turAIAgentRepository;
        this.agentChatExecutor = agentChatExecutor;
        this.chatFlowEngineService = chatFlowEngineService;
    }

    // ==================== list_agents ====================

    @Tool(name = "list_agents", description = ".")
    public String listAgents() {
        List<Map<String, Object>> agents = new ArrayList<>();
        for (TurAIAgent agent : turAIAgentRepository.findAll()) {
            if (agent.getEnabled() != 1) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", agent.getId());
            entry.put("name", agent.getTitle());
            entry.put("description", agent.getDescription());
            agents.add(entry);
        }
        if (agents.isEmpty()) {
            return "No enabled AI agents are configured on this Turing server.";
        }
        try {
            return "Found %d agent(s).\n%s".formatted(agents.size(),
                    objectMapper.writeValueAsString(agents));
        } catch (Exception e) {
            return "Found %d agent(s).".formatted(agents.size());
        }
    }

    // ==================== invoke_agent ====================

    @Tool(name = "invoke_agent", description = ".")
    public String invokeAgent(String agentId, String message, String conversationId) {
        if (agentId == null || agentId.isBlank()) {
            return "Error: 'agentId' is required (use list_agents to discover agents).";
        }
        if (message == null || message.isBlank()) {
            return "Error: 'message' is required.";
        }

        TurAIAgent agent = turAIAgentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            return "Error: AI Agent not found: " + agentId;
        }
        if (agent.getEnabled() != 1) {
            return "Error: AI Agent is disabled: " + agentId;
        }

        TurLLMInstance llmInstance = agent.getLlmInstances().stream()
                .min(Comparator.comparing(TurLLMInstance::getId))
                .orElse(null);
        if (llmInstance == null) {
            return "Error: AI Agent '" + agentId + "' has no LLM instance configured.";
        }

        String conversation = (conversationId != null && !conversationId.isBlank())
                ? conversationId
                : UUID.randomUUID().toString();

        try {
            List<ChatMessageItem> history = List.of(new ChatMessageItem("user", message));
            String answer = agentChatExecutor
                    .execute(new TurAgentChatRequest(agent, llmInstance, history, null,
                            conversation, null, null, null), (String) null, (String) null)
                    .filter(r -> r.content() != null && !r.content().isBlank())
                    .filter(r -> !"options".equals(r.type()))
                    .map(TurAgentChatExecutor.ChatResponse::content)
                    .collectList()
                    .blockOptional(INVOKE_TIMEOUT)
                    .map(parts -> String.join("", parts))
                    .orElse("");

            return formatResult(answer, conversation);
        } catch (Exception e) {
            log.error("[MCP] invoke_agent failed for agent={}", agentId, e);
            return "Error invoking agent '" + agentId + "': " + e.getMessage();
        }
    }

    private String formatResult(String answer, String conversation) {
        StringBuilder sb = new StringBuilder();
        sb.append(answer == null || answer.isBlank() ? "(no answer produced)" : answer.strip());

        String slots = formatSlots(conversation);
        if (!slots.isBlank()) {
            sb.append("\n\nCaptured slots:\n").append(slots);
        }
        sb.append("\n\nConversation: ").append(conversation);
        return sb.toString();
    }

    private String formatSlots(String conversation) {
        try {
            TurChatSessionSlotsDto dto = chatFlowEngineService.listSlotsForConversation(conversation);
            if (dto == null || dto.slots() == null || dto.slots().isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            dto.slots().forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append('\n'));
            return sb.toString().strip();
        } catch (Exception e) {
            log.debug("[MCP] invoke_agent could not read slots for conversation {}", conversation, e);
            return "";
        }
    }
}
