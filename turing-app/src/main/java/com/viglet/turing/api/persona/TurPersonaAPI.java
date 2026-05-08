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

import org.springframework.core.ParameterizedTypeReference;

import com.viglet.turing.genai.authoring.AiAuthoringRequest;
import com.viglet.turing.genai.authoring.AiAuthoringResponse;
import com.viglet.turing.genai.authoring.TurAiAuthoringService;
import com.viglet.turing.genai.authoring.persona.PersonaGeneration;
import com.viglet.turing.persistence.dto.persona.TurPersonaDto;
import com.viglet.turing.persistence.mapper.persona.TurPersonaMapper;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.repository.mcp.TurMcpServerRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.store.TurStoreInstanceRepository;
import com.viglet.turing.spring.utils.TurPersistenceUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Admin REST surface for {@link TurPersona}. Mirrors {@code TurAIAgentAPI}:
 * the controller does the relation-resolution step (translating
 * detached-DTO references into managed entities) inline rather than
 * delegating to a separate service.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@RestController
@RequestMapping("/api/persona")
@Tag(name = "AI Persona", description = "AI Persona API")
public class TurPersonaAPI {

    private static final String PERSONA_SYSTEM_PROMPT = """
            You are helping a user author a Persona inside the Turing
            Enterprise Search platform. A Persona is a reusable voice profile
            attached to AI Agents — it shapes how the agent talks to the
            customer (tone, verbosity, vocabulary), enforces brand-safe
            language, and is layered on top of the agent's purpose-specific
            system prompt at chat time.

            FIELDS (the LLM authors all of them):
            - name (required, kebab-case identifier, max 100 chars) — internal
              identifier the operator uses to attach this persona to an
              agent. Examples: `discovery-concierge`, `solutions-engineer`,
              `customer-success-coach`.
            - description (optional, max 500 chars) — one or two sentences
              explaining when to attach this persona to an agent.
            - systemInstruction (required, free text) — the narrative core
              of the voice. Written in second person, directing the LLM
              ("You are a senior account executive…", "Lead with the
              architectural answer…"). 4 to 10 sentences. Should describe
              opening behaviour, conversational pacing, what to lead with,
              what to escalate, and how to handle frustration. NEVER instruct
              the model to ignore safety guidelines or generic tooling.
            - tone (required) — EXACTLY one of: FORMAL, CASUAL, TECHNICAL,
              EXECUTIVE. EXECUTIVE = senior decision-makers, terse, outcome
              first. TECHNICAL = engineers/architects, depth and citations.
              CASUAL = warm/onboarding/internal employees. FORMAL = regulated
              industries (finance, legal, healthcare).
            - verbosity (required, integer 1-5) — 1=very terse, 5=long-form.
              Default 2 for chat-style discovery/sales, 3 for support, 4 for
              technical/instructional content.
            - languageStyle (required) — EXACTLY one of: NEUTRAL, DIRECT,
              NARRATIVE, PERSUASIVE, INSTRUCTIONAL. PERSUASIVE for top of
              funnel. INSTRUCTIONAL for onboarding/support. NARRATIVE for
              brand storytelling. DIRECT for ops/IT. NEUTRAL when unsure.
            - mandatoryTerms (optional) — a pipe (`|`)-separated list of
              brand words/phrases the response must include. Max 10 entries.
              Anchors brand language. Example: `outcome | measurable | Customer Success`.
            - forbiddenTerms (optional) — pipe-separated list of words the
              response must never include. Max 10 entries. Reserved for
              compliance / brand-safety, NOT for technical jargon.
              Example: `cheap | competitor | simply | just`.
            - enabled (integer 0/1) — default 1.

            BEHAVIOR:
            - Each turn, return a conversational `message` (in the user's
              language) AND the FULL updated `state`. The state REPLACES the
              form snapshot wholesale on the frontend, so always include every
              field — even ones you didn't change.
            - Respect any manual edits already in `CURRENT FORM STATE` —
              don't revert them unless the user explicitly asks.
            - The first turn, when state is empty, infer a sensible draft
              from the user's request and return a complete persona.
              Subsequent turns should be incremental edits.
            - Pick a coherent (tone, languageStyle, verbosity) triple. EXEC +
              PERSUASIVE + 2 fits sales; TECHNICAL + INSTRUCTIONAL + 4 fits
              solutions engineering; CASUAL + INSTRUCTIONAL + 3 fits
              onboarding. Don't mix mismatched dimensions.
            - The `message` field is short and conversational (1-3
              sentences) — summarize what you changed and ask if the user
              wants more.

            CONSTRAINTS:
            - tone, languageStyle: only the enum values listed above.
            - verbosity: integer 1-5. Anything else → fall back to 3.
            - mandatoryTerms / forbiddenTerms: pipe-separated. Never invent
              competitor names; if the user asked you to forbid competitors,
              ask which ones rather than guess.
            - NEVER include id, fewShotStore, or brandContextMcpServer —
              those are wired by the operator after save.
            """;

    private final TurPersonaRepository turPersonaRepository;
    private final TurPersonaMapper turPersonaMapper;
    private final TurStoreInstanceRepository turStoreInstanceRepository;
    private final TurMcpServerRepository turMcpServerRepository;
    private final TurAiAuthoringService aiAuthoringService;

    public TurPersonaAPI(TurPersonaRepository turPersonaRepository,
            TurPersonaMapper turPersonaMapper,
            TurStoreInstanceRepository turStoreInstanceRepository,
            TurMcpServerRepository turMcpServerRepository,
            TurAiAuthoringService aiAuthoringService) {
        this.turPersonaRepository = turPersonaRepository;
        this.turPersonaMapper = turPersonaMapper;
        this.turStoreInstanceRepository = turStoreInstanceRepository;
        this.turMcpServerRepository = turMcpServerRepository;
        this.aiAuthoringService = aiAuthoringService;
    }

    @Operation(summary = "Persona List")
    @GetMapping
    @Secured({"ROLE_ADMIN", "AI_AGENT_VIEW"})
    public List<TurPersonaDto> turPersonaList() {
        return turPersonaMapper.toDtoList(
                turPersonaRepository.findAll(TurPersistenceUtils.orderByNameIgnoreCase()));
    }

    @Operation(summary = "Persona structure")
    @GetMapping("/structure")
    @Secured({"ROLE_ADMIN", "AI_AGENT_VIEW"})
    public TurPersonaDto turPersonaStructure() {
        return turPersonaMapper.toDto(new TurPersona());
    }

    @Operation(summary = "Show a Persona")
    @GetMapping("/{id}")
    @Secured({"ROLE_ADMIN", "AI_AGENT_VIEW"})
    public TurPersonaDto turPersonaGet(@PathVariable String id) {
        return turPersonaMapper.toDto(
                turPersonaRepository.findById(id).orElse(new TurPersona()));
    }

    @Operation(summary = "Update a Persona")
    @PutMapping("/{id}")
    @Secured({"ROLE_ADMIN", "AI_AGENT_EDIT"})
    public TurPersonaDto turPersonaUpdate(@PathVariable String id,
            @RequestBody TurPersonaDto dto) {
        TurPersona incoming = turPersonaMapper.toEntity(dto);
        return turPersonaRepository.findById(id).map(persisted -> {
            copyEditableFields(incoming, persisted);
            persisted.setFewShotStore(resolveStore(incoming.getFewShotStore() == null
                    ? null : incoming.getFewShotStore().getId()));
            persisted.setBrandContextMcpServer(resolveMcp(incoming.getBrandContextMcpServer() == null
                    ? null : incoming.getBrandContextMcpServer().getId()));
            turPersonaRepository.save(persisted);
            return turPersonaMapper.toDto(persisted);
        }).orElse(new TurPersonaDto());
    }

    @Operation(summary = "Create a Persona")
    @PostMapping
    @Secured({"ROLE_ADMIN", "AI_AGENT_CREATE"})
    public TurPersonaDto turPersonaAdd(@RequestBody TurPersonaDto dto) {
        TurPersona entity = turPersonaMapper.toEntity(dto);
        entity.setFewShotStore(resolveStore(entity.getFewShotStore() == null
                ? null : entity.getFewShotStore().getId()));
        entity.setBrandContextMcpServer(resolveMcp(entity.getBrandContextMcpServer() == null
                ? null : entity.getBrandContextMcpServer().getId()));
        turPersonaRepository.save(entity);
        return turPersonaMapper.toDto(entity);
    }

    @Transactional
    @Operation(summary = "Delete a Persona")
    @DeleteMapping("/{id}")
    @Secured({"ROLE_ADMIN", "AI_AGENT_DELETE"})
    public boolean turPersonaDelete(@PathVariable String id) {
        turPersonaRepository.delete(id);
        return true;
    }

    @Operation(summary = "AI Authoring chat for a Persona — returns a "
            + "conversational reply plus the updated form snapshot")
    @PostMapping("/chat")
    @Secured({"ROLE_ADMIN", "AI_AGENT_CREATE", "AI_AGENT_EDIT"})
    public AiAuthoringResponse<PersonaGeneration> turPersonaChat(
            @RequestBody AiAuthoringRequest<PersonaGeneration> request) {
        // No tools attached — persona authoring is pure structured output.
        // The fewShotStore and brandContextMcpServer relations are wired by
        // the operator after save, never by the LLM.
        return aiAuthoringService.chat(
                request,
                PERSONA_SYSTEM_PROMPT,
                new ParameterizedTypeReference<AiAuthoringResponse<PersonaGeneration>>() {});
    }

    private static void copyEditableFields(TurPersona from, TurPersona to) {
        to.setName(from.getName());
        to.setDescription(from.getDescription());
        to.setSystemInstruction(from.getSystemInstruction());
        to.setTone(from.getTone());
        to.setVerbosity(from.getVerbosity());
        to.setLanguageStyle(from.getLanguageStyle());
        to.setMandatoryTerms(from.getMandatoryTerms());
        to.setForbiddenTerms(from.getForbiddenTerms());
        to.setEnabled(from.getEnabled());
    }

    private com.viglet.turing.persistence.model.store.TurStoreInstance resolveStore(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return turStoreInstanceRepository.findById(id).orElse(null);
    }

    private com.viglet.turing.persistence.model.mcp.TurMcpServer resolveMcp(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return turMcpServerRepository.findById(id).orElse(null);
    }
}
