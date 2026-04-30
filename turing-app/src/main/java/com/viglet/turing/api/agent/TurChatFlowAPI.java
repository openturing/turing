/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.agent;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.genai.authoring.AiAuthoringRequest;
import com.viglet.turing.genai.authoring.AiAuthoringResponse;
import com.viglet.turing.genai.authoring.chatflow.ChatFlowAuthoringSkill;
import com.viglet.turing.genai.authoring.chatflow.ChatFlowGeneration;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.persistence.dto.agent.TurChatFlowDto;
import com.viglet.turing.persistence.dto.agent.TurChatFlowSubmissionDto;
import com.viglet.turing.persistence.mapper.agent.TurChatFlowMapper;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Agent-scoped REST API for chat flows. Each AI agent owns its own set of
 * flows; the Phase B advisor will resolve a flow id to its definition through
 * this repository at chat time.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@RestController
@RequestMapping("/api/ai-agent/{agentId}/chat-flow")
@Tag(name = "Chat Flow", description = "AI Agent Chat Flow API")
public class TurChatFlowAPI {
    private final TurChatFlowRepository turChatFlowRepository;
    private final TurChatFlowMapper turChatFlowMapper;
    private final TurAIAgentRepository turAIAgentRepository;
    private final TurChatFlowEngineService chatFlowEngineService;
    private final ChatFlowAuthoringSkill chatFlowAuthoringSkill;

    public TurChatFlowAPI(TurChatFlowRepository turChatFlowRepository,
            TurChatFlowMapper turChatFlowMapper,
            TurAIAgentRepository turAIAgentRepository,
            TurChatFlowEngineService chatFlowEngineService,
            ChatFlowAuthoringSkill chatFlowAuthoringSkill) {
        this.turChatFlowRepository = turChatFlowRepository;
        this.turChatFlowMapper = turChatFlowMapper;
        this.turAIAgentRepository = turAIAgentRepository;
        this.chatFlowEngineService = chatFlowEngineService;
        this.chatFlowAuthoringSkill = chatFlowAuthoringSkill;
    }

    private TurAIAgent loadAgent(String agentId) {
        return turAIAgentRepository.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI Agent not found"));
    }

    @Operation(summary = "Chat Flow List for an Agent")
    @GetMapping
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public List<TurChatFlowDto> turChatFlowList(@PathVariable String agentId) {
        return turChatFlowMapper
                .toDtoList(turChatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(agentId));
    }

    @Operation(summary = "Chat Flow structure")
    @GetMapping("/structure")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public TurChatFlowDto turChatFlowStructure(@PathVariable String agentId) {
        return turChatFlowMapper.toDto(new TurChatFlow());
    }

    @Operation(summary = "Show a Chat Flow of an Agent")
    @GetMapping("/{id}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public TurChatFlowDto turChatFlowGet(@PathVariable String agentId, @PathVariable String id) {
        return turChatFlowRepository.findById(id)
                .filter(flow -> flow.getTurAIAgent() != null
                        && agentId.equals(flow.getTurAIAgent().getId()))
                .map(turChatFlowMapper::toDto)
                .orElse(new TurChatFlowDto());
    }

    @Operation(summary = "Create a Chat Flow for an Agent")
    @PostMapping
    @Secured({ "ROLE_ADMIN", "AI_AGENT_CREATE" })
    public TurChatFlowDto turChatFlowAdd(@PathVariable String agentId,
            @RequestBody TurChatFlowDto turChatFlowDto) {
        TurAIAgent agent = loadAgent(agentId);
        TurChatFlow entity = turChatFlowMapper.toEntity(turChatFlowDto);
        // The UUID generator preserves any pre-assigned id, including the
        // empty string sent by some clients. Force null so a fresh UUID is
        // generated on insert.
        entity.setId(null);
        entity.setTurAIAgent(agent);
        turChatFlowRepository.save(entity);
        return turChatFlowMapper.toDto(entity);
    }

    @Operation(summary = "Update a Chat Flow of an Agent")
    @PutMapping("/{id}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public TurChatFlowDto turChatFlowUpdate(@PathVariable String agentId,
            @PathVariable String id,
            @RequestBody TurChatFlowDto turChatFlowDto) {
        TurChatFlow incoming = turChatFlowMapper.toEntity(turChatFlowDto);
        return turChatFlowRepository.findById(id)
                .filter(flow -> flow.getTurAIAgent() != null
                        && agentId.equals(flow.getTurAIAgent().getId()))
                .map(existing -> {
                    existing.setName(incoming.getName());
                    existing.setDescription(incoming.getDescription());
                    existing.setDefinitionJson(incoming.getDefinitionJson());
                    existing.setEnabled(incoming.getEnabled());
                    if (incoming.getGuardrailMethod() != null) {
                        existing.setGuardrailMethod(incoming.getGuardrailMethod());
                    }
                    existing.setTriggerDescription(incoming.getTriggerDescription());
                    if (incoming.getTriggerMode() != null) {
                        existing.setTriggerMode(incoming.getTriggerMode());
                    }
                    turChatFlowRepository.save(existing);
                    return turChatFlowMapper.toDto(existing);
                }).orElse(new TurChatFlowDto());
    }

    @Operation(summary = "Submissions of a Chat Flow (states that reached an end node)")
    @GetMapping("/{id}/submissions")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public List<TurChatFlowSubmissionDto> turChatFlowSubmissions(@PathVariable String agentId,
            @PathVariable String id) {
        return turChatFlowRepository.findById(id)
                .filter(flow -> flow.getTurAIAgent() != null
                        && agentId.equals(flow.getTurAIAgent().getId()))
                .map(chatFlowEngineService::listSubmissions)
                .orElse(List.of());
    }

    @Transactional
    @Operation(summary = "Delete a Chat Flow of an Agent")
    @DeleteMapping("/{id}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_DELETE" })
    public boolean turChatFlowDelete(@PathVariable String agentId, @PathVariable String id) {
        return turChatFlowRepository.findById(id)
                .filter(flow -> flow.getTurAIAgent() != null
                        && agentId.equals(flow.getTurAIAgent().getId()))
                .map(flow -> {
                    turChatFlowRepository.delete(id);
                    return true;
                }).orElse(false);
    }

    /**
     * AI Authoring chat for chat flows.
     * <p>
     * Routes to {@link ChatFlowAuthoringSkill#create} on the very first
     * turn (no prior assistant messages, no graph yet) — that path uses a
     * detailed system prompt that documents every node type, every
     * type-specific field, edge wiring rules, and guardrail / trigger
     * policies, then post-processes for layout + validation. Subsequent
     * turns route to {@link ChatFlowAuthoringSkill#revise}, a slimmer
     * prompt focused on incremental edits to the existing state.
     */
    @Operation(summary = "AI Authoring chat for a Chat Flow — first turn invokes the planning skill, "
            + "subsequent turns do incremental edits.")
    @PostMapping("/chat")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_CREATE", "AI_AGENT_EDIT" })
    public AiAuthoringResponse<ChatFlowGeneration> turChatFlowChat(
            @PathVariable String agentId,
            @RequestBody AiAuthoringRequest<ChatFlowGeneration> request) {
        loadAgent(agentId);
        return ChatFlowAuthoringSkill.isFirstTurn(request)
                ? chatFlowAuthoringSkill.create(request)
                : chatFlowAuthoringSkill.revise(request);
    }
}
