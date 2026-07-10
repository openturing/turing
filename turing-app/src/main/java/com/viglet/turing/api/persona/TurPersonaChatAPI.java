/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.persona;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.genai.TurAgentChatRequest;
import com.viglet.turing.genai.TurChatProviderErrorMapper;
import com.viglet.turing.genai.tool.TurNativeToolService;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.tenant.TurInfraTenantScope;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * Block AI / §XXXII.1 (T578) — talk directly to a {@link TurPersona}.
 *
 * <p>A persona is otherwise a passive voice profile that only reaches a
 * conversation by being attached to a {@link TurAIAgent}. This endpoint makes
 * the persona <em>actionable in its own right</em>: it drives the shared
 * {@link TurAgentChatExecutor} with a <b>transient, never-persisted</b>
 * {@code TurAIAgent} shell whose only purpose is to carry the persona's voice
 * and a generic tool loadout:
 * <ul>
 *   <li>{@code systemPrompt = null} → the executor falls back to its generic
 *       {@code DEFAULT_SYSTEM_PROMPT};</li>
 *   <li>{@code nativeTools} = a CSV of <em>all</em> discovered native tool names
 *       ({@link TurNativeToolService#getAllTools()}) — there is no {@code "*"}
 *       sentinel, so the CSV must list real names matched by
 *       {@link TurNativeToolService#getToolCallbacks};</li>
 *   <li>no MCP servers, custom tools, skills, RAG or chat flows (the fresh
 *       entity's association collections are empty {@code HashSet}s);</li>
 *   <li>{@code defaultPersona} = the requested persona, fused into the system
 *       prompt by the executor's persona resolver.</li>
 * </ul>
 *
 * <p>The turn is <b>stateless</b> ({@code conversationId = null},
 * {@code flowId = null}): the executor skips every agent-keyed write
 * (flow-state, analytics, chat-memory, spectator bus), and a fresh
 * {@code TurAIAgent} never {@code LazyInitializationException}s on a read. A
 * later iteration can promote this to a persisted hidden per-persona system
 * agent if conversation-scoped statefulness is wanted — none of the MVP
 * requirements need it.
 *
 * <p>Because this bypasses {@link com.viglet.turing.api.agent.TurAIAgentChatAPI}'s
 * per-agent LLM allow-list, the LLM instance is validated here directly against
 * the tenant scope ({@link TurInfraTenantScope#isVisibleToTenant}). The persona
 * itself is {@code @TenantId}-filtered by Hibernate on load, and must satisfy
 * {@link TurPersona#isUsableAsSpeaker()} (an {@code AUDIENCE}-only persona is
 * rejected).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/persona/{personaId}")
@Tag(name = "Persona Chat", description = "Talk directly to a persona to test its voice")
public class TurPersonaChatAPI {

    private final TurPersonaRepository turPersonaRepository;
    private final TurLLMInstanceRepository turLLMInstanceRepository;
    private final TurInfraTenantScope tenantScope;
    private final TurNativeToolService nativeToolService;
    private final TurAgentChatExecutor agentChatExecutor;

    public TurPersonaChatAPI(TurPersonaRepository turPersonaRepository,
            TurLLMInstanceRepository turLLMInstanceRepository,
            TurInfraTenantScope tenantScope,
            TurNativeToolService nativeToolService,
            TurAgentChatExecutor agentChatExecutor) {
        this.turPersonaRepository = turPersonaRepository;
        this.turLLMInstanceRepository = turLLMInstanceRepository;
        this.tenantScope = tenantScope;
        this.nativeToolService = nativeToolService;
        this.agentChatExecutor = agentChatExecutor;
    }

    public record PersonaChatRequest(String llmInstanceId, List<ChatMessageItem> messages) {
    }

    public record ChatMessageItem(String role, String content) {
    }

    public record ChatResponse(String role, String content, String type) {
        /** Backwards-compatible constructor: defaults {@code type} to {@code "token"}. */
        public ChatResponse(String role, String content) {
            this(role, content, "token");
        }
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public Flux<ChatResponse> chat(
            @PathVariable String personaId,
            @RequestBody PersonaChatRequest request,
            HttpServletRequest httpRequest) {
        return doChat(personaId, request, null, httpRequest);
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE,
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Flux<ChatResponse> chatMultipart(
            @PathVariable String personaId,
            @RequestPart("request") PersonaChatRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            HttpServletRequest httpRequest) {
        return doChat(personaId, request, files, httpRequest);
    }

    private Flux<ChatResponse> doChat(String personaId, PersonaChatRequest request,
            List<MultipartFile> files, HttpServletRequest httpRequest) {

        // @TenantId auto-filters by tenant, so a persona from another tenant
        // (or a non-existent id) both fall into the same 404 branch.
        TurPersona persona = turPersonaRepository.findById(personaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Persona not found: " + personaId));

        if (persona.getEnabled() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Persona is disabled: " + personaId);
        }
        if (!persona.isUsableAsSpeaker()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Persona '" + personaId + "' is audience-only and cannot be a speaker");
        }

        String llmInstanceId = request.llmInstanceId();
        if (llmInstanceId == null || llmInstanceId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "An LLM instance id is required to chat with a persona");
        }
        TurLLMInstance turLLMInstance = turLLMInstanceRepository.findById(llmInstanceId)
                .filter(tenantScope::isVisibleToTenant)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "LLM instance not found: " + llmInstanceId));

        TurAIAgent agent = buildTransientAgent(persona);

        int fileCount = files == null ? 0 : files.size();
        log.info("[PersonaChat] Persona '{}' received request with {} messages, {} files, LLM: {}",
                persona.getName(), request.messages().size(), fileCount, turLLMInstance.getTitle());

        List<TurAgentChatExecutor.ChatMessageItem> mapped = request.messages().stream()
                .map(m -> new TurAgentChatExecutor.ChatMessageItem(m.role(), m.content()))
                .toList();

        // Stateless turn: conversationId + flowId null so no flow-state /
        // analytics / chat-memory rows are written (see class Javadoc).
        Flux<ChatResponse> stream = agentChatExecutor.execute(
                        new TurAgentChatRequest(agent, turLLMInstance, mapped, null, null, null, files, null))
                .map(r -> new ChatResponse(r.role(), r.content(), r.type()));

        String personaName = persona.getName();
        java.util.Locale locale = httpRequest.getLocale();
        return stream.onErrorResume(err -> {
            log.error("[PersonaChat] Persona '{}' stream failed: {}", personaName, err.getMessage(), err);
            return Flux.just(new ChatResponse("assistant",
                    TurChatProviderErrorMapper.toUserMessage(err, locale), "token"));
        });
    }

    /**
     * Build the never-persisted {@link TurAIAgent} shell that carries the
     * persona's voice and the full native tool loadout into the shared
     * executor. Deliberately leaves every association collection at its
     * field-initialized empty {@code HashSet} (no MCP / custom tools / flows /
     * skills / RAG) and {@code systemPrompt} null (generic fallback).
     */
    private TurAIAgent buildTransientAgent(TurPersona persona) {
        TurAIAgent agent = new TurAIAgent();
        agent.setTitle("Persona: " + persona.getName());
        agent.setEnabled(1);
        agent.setNativeTools(allNativeToolNamesCsv());
        agent.setDefaultPersona(persona);
        return agent;
    }

    /** CSV of every discovered native tool name (no {@code "*"} sentinel exists). */
    private String allNativeToolNamesCsv() {
        return nativeToolService.getAllTools().stream()
                .map(TurNativeToolService.NativeToolDescriptor::name)
                .collect(Collectors.joining(","));
    }
}
