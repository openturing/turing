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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

import com.viglet.turing.domain.agent.TurAIAgentRepositoryPort;
import com.viglet.turing.genai.authoring.AiAuthoringRequest;
import com.viglet.turing.genai.authoring.AiAuthoringResponse;
import com.viglet.turing.genai.authoring.chatflow.ChatFlowAuthoringSkill;
import com.viglet.turing.genai.authoring.chatflow.ChatFlowGeneration;
import com.viglet.turing.genai.eval.TurAgentEvalGateService;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.flow.TurChatFlowLinterService;
import com.viglet.turing.service.chatanalytics.TurChatFlowFunnelService;
import com.viglet.turing.genai.flow.TurChatFlowVariantService;
import com.viglet.turing.genai.flow.TurFormLabelTidyService;
import com.viglet.turing.genai.flow.TurTriggerConflictService;
import com.viglet.turing.persistence.dto.agent.TurAIAgentSlotDto;
import com.viglet.turing.persistence.dto.agent.TurChatFlowDto;
import com.viglet.turing.persistence.dto.agent.TurChatFlowImportDto;
import com.viglet.turing.persistence.dto.agent.TurChatFlowLintIssueDto;
import com.viglet.turing.persistence.dto.agent.TurChatFlowRecipeDto;
import com.viglet.turing.persistence.dto.agent.TurChatFlowSubmissionDto;
import com.viglet.turing.persistence.dto.agent.TurChatFlowTriggerConflictDto;
import com.viglet.turing.persistence.dto.agent.TurChatFlowVariantRequest;
import com.viglet.turing.persistence.dto.agent.TurChatFlowVariantResponse;
import com.viglet.turing.persistence.dto.agent.TurFormLabelTidyRequest;
import com.viglet.turing.persistence.dto.agent.TurFormLabelTidyResponse;
import com.viglet.turing.service.chatflow.TurChatFlowRecipeService;
import com.viglet.turing.persistence.dto.persona.TurPersonaDto;
import com.viglet.turing.persistence.mapper.agent.TurChatFlowMapper;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAIAgentSlot;
import com.viglet.turing.persistence.model.agent.TurAIAgentSlotType;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurAIAgentSlotRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TurChatFlowRepository turChatFlowRepository;
    private final TurChatFlowMapper turChatFlowMapper;
    private final TurAIAgentRepository turAIAgentRepository;
    private final TurAIAgentRepositoryPort turAIAgentRepositoryPort;
    private final TurChatFlowEngineService chatFlowEngineService;
    private final ChatFlowAuthoringSkill chatFlowAuthoringSkill;
    private final TurPersonaRepository turPersonaRepository;
    private final TurAIAgentSlotRepository turAIAgentSlotRepository;
    private final TurTriggerConflictService triggerConflictService;
    private final TurChatFlowLinterService chatFlowLinterService;
    private final TurChatFlowRecipeService chatFlowRecipeService;
    private final TurChatFlowVariantService chatFlowVariantService;
    private final TurChatFlowFunnelService chatFlowFunnelService;
    private final TurAgentEvalGateService agentEvalGateService;
    private final TurFormLabelTidyService formLabelTidyService;

    public TurChatFlowAPI(TurChatFlowRepository turChatFlowRepository,
            TurChatFlowMapper turChatFlowMapper,
            TurAIAgentRepository turAIAgentRepository,
            TurAIAgentRepositoryPort turAIAgentRepositoryPort,
            TurChatFlowEngineService chatFlowEngineService,
            ChatFlowAuthoringSkill chatFlowAuthoringSkill,
            TurPersonaRepository turPersonaRepository,
            TurAIAgentSlotRepository turAIAgentSlotRepository,
            TurTriggerConflictService triggerConflictService,
            TurChatFlowLinterService chatFlowLinterService,
            TurChatFlowRecipeService chatFlowRecipeService,
            TurChatFlowVariantService chatFlowVariantService,
            TurChatFlowFunnelService chatFlowFunnelService,
            TurAgentEvalGateService agentEvalGateService,
            TurFormLabelTidyService formLabelTidyService) {
        this.turChatFlowRepository = turChatFlowRepository;
        this.turChatFlowMapper = turChatFlowMapper;
        this.turAIAgentRepository = turAIAgentRepository;
        this.turAIAgentRepositoryPort = turAIAgentRepositoryPort;
        this.chatFlowEngineService = chatFlowEngineService;
        this.chatFlowAuthoringSkill = chatFlowAuthoringSkill;
        this.turPersonaRepository = turPersonaRepository;
        this.turAIAgentSlotRepository = turAIAgentSlotRepository;
        this.triggerConflictService = triggerConflictService;
        this.chatFlowLinterService = chatFlowLinterService;
        this.chatFlowRecipeService = chatFlowRecipeService;
        this.chatFlowVariantService = chatFlowVariantService;
        this.chatFlowFunnelService = chatFlowFunnelService;
        this.agentEvalGateService = agentEvalGateService;
        this.formLabelTidyService = formLabelTidyService;
    }

    /** Load the agent entity — only for write paths that need the JPA-managed reference. */
    private TurAIAgent loadAgent(String agentId) {
        return turAIAgentRepository.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI Agent not found"));
    }

    /** Validate the agent exists via the port — for read-only paths that don't need the entity. */
    private void requireAgentExists(String agentId) {
        if (turAIAgentRepositoryPort.findById(agentId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AI Agent not found");
        }
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

    /**
     * Imports a Chat Flow exported via the editor's "Export JSON" action. The
     * payload may bundle a {@code personas} array — each persona whose name
     * is not already in the catalog is auto-created and attached to the
     * target AI agent. {@code personaId} references inside the graph's
     * {@code definitionJson} are rewritten to point at the resolved (existing
     * or freshly persisted) persona UUIDs before the flow itself is saved,
     * so the editor opens to a fully wired graph with no manual rebinding.
     *
     * @since 2026.2.7
     */
    @Operation(summary = "Import a Chat Flow (and any missing personas) from an export JSON")
    @PostMapping("/import")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_CREATE" })
    public TurChatFlowDto turChatFlowImport(@PathVariable String agentId,
            @RequestBody TurChatFlowImportDto payload) {
        if (payload == null || payload.getChatFlow() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chatFlow is required");
        }
        TurAIAgent agent = loadAgent(agentId);

        // Resolve every embedded persona — reuse the catalog entry when the name
        // already exists (case-insensitive), otherwise create a fresh one. The
        // remap below lets us translate the export's transient persona ids into
        // the real persisted ids before saving the flow's graph, and the
        // {@code personaNameById} map caches the human-readable label so the
        // diagram opens with "Switches voice to: Captain Nova" instead of a uuid.
        Map<String, String> personaIdRemap = new HashMap<>();
        Map<String, String> personaNameById = new HashMap<>();
        List<TurPersonaDto> incomingPersonas = payload.getPersonas();
        if (incomingPersonas != null) {
            for (TurPersonaDto incoming : incomingPersonas) {
                if (incoming == null || incoming.getName() == null
                        || incoming.getName().isBlank()) {
                    continue;
                }
                TurPersona resolved = turPersonaRepository
                        .findByNameIgnoreCase(incoming.getName())
                        .orElseGet(() -> createPersonaFromImport(incoming));
                if (incoming.getId() != null && !incoming.getId().isBlank()) {
                    personaIdRemap.put(incoming.getId(), resolved.getId());
                }
                personaNameById.put(resolved.getId(), resolved.getName());
                agent.getPersonas().add(resolved);
            }
            // Persist the agent once — even when every persona already existed,
            // this guarantees the EAGER personas Set is the same fetched on the
            // next read (avoids a stale agent cache hiding newly-attached voices).
            turAIAgentRepository.save(agent);
        }

        // Slots — auto-create any embedded slot whose name is not yet declared on this agent
        // so the editor opens with the outputVariable dropdown already populated.
        resolveSlots(agent, payload.getSlots());

        TurChatFlowDto incomingFlow = payload.getChatFlow();
        // No sub-flow remap on the single-flow path — the bundle endpoint is the one that wires
        // multiple flows together. Empty maps are no-ops inside rewriteGraph.
        String rewrittenJson = rewriteGraph(incomingFlow.getDefinitionJson(),
                personaIdRemap, personaNameById, Map.of(), Map.of());
        incomingFlow.setDefinitionJson(rewrittenJson);

        TurChatFlow entity = turChatFlowMapper.toEntity(incomingFlow);
        entity.setId(null);
        entity.setTurAIAgent(agent);
        turChatFlowRepository.save(entity);
        return turChatFlowMapper.toDto(entity);
    }

    /**
     * Imports a bundle of chat flows in a single transaction. Each item in the list mirrors the
     * single-flow import shape ({@link TurChatFlowImportDto} = chat-flow + embedded personas), but
     * sub-flow references across items are auto-wired: when item A's graph contains a
     * {@code subFlowId} that matches item B's transient {@code id}, the import rewrites the
     * reference to point at the real UUID assigned to B. Personas are de-duplicated by name across
     * the whole bundle (case-insensitive) and attached to the target agent once.
     *
     * @since 2026.2.7
     */
    @Operation(summary = "Import a bundle of Chat Flows (with auto-wiring of sub-flow references)")
    @PostMapping("/import-bundle")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_CREATE" })
    public List<TurChatFlowDto> turChatFlowImportBundle(@PathVariable String agentId,
            @RequestBody List<TurChatFlowImportDto> bundle) {
        if (bundle == null || bundle.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bundle is required");
        }
        TurAIAgent agent = loadAgent(agentId);

        // 1) De-duplicate personas across the whole bundle (by name, case-insensitive) and attach
        //    every resolved persona to the agent. Same lookup-or-create policy as the single-flow
        //    import — we never overwrite an existing persona's settings.
        Map<String, String> personaIdRemap = new HashMap<>();
        Map<String, String> personaNameById = new HashMap<>();
        attachBundlePersonas(agent, bundle, personaIdRemap, personaNameById);

        // 1b) Slots — de-duplicate by name across every item in the bundle and auto-create any
        //     missing entry on the target agent. Existing slots are left untouched.
        for (TurChatFlowImportDto item : bundle) {
            if (item != null) {
                resolveSlots(agent, item.getSlots());
            }
        }

        // 2) Pre-assign a real UUID to each flow so the sub-flow remap is complete before any
        //    graph is rewritten. Doing this BEFORE saving (rather than insert-then-update) lets us
        //    save each entity exactly once with its final, rewritten graph.
        Map<String, String> subFlowIdRemap = new HashMap<>();
        Map<String, String> subFlowNameById = new HashMap<>();
        List<TurChatFlow> entities = assignBundleFlowEntities(agent, bundle, subFlowIdRemap, subFlowNameById);

        // 3) Rewrite each flow's graph with the now-complete persona + sub-flow remaps, then save.
        return rewriteAndSaveBundleFlows(entities, personaIdRemap, personaNameById,
                subFlowIdRemap, subFlowNameById);
    }

    /**
     * Phase 1 of {@link #turChatFlowImportBundle}: de-duplicates personas across the bundle
     * (by name, case-insensitive), attaches each resolved persona to the agent, and fills the
     * id / name remap maps in place. Saves the agent once if any persona was attached.
     *
     * @since 2026.3.1
     */
    private void attachBundlePersonas(TurAIAgent agent, List<TurChatFlowImportDto> bundle,
            Map<String, String> personaIdRemap, Map<String, String> personaNameById) {
        boolean anyPersonaTouched = false;
        for (TurChatFlowImportDto item : bundle) {
            if (item == null || item.getPersonas() == null) {
                continue;
            }
            for (TurPersonaDto incoming : item.getPersonas()) {
                if (resolveAndAttachPersona(incoming, agent, personaIdRemap, personaNameById)) {
                    anyPersonaTouched = true;
                }
            }
        }
        if (anyPersonaTouched) {
            turAIAgentRepository.save(agent);
        }
    }

    /**
     * Looks up (or creates) the persona for a single import entry and attaches it to the agent,
     * recording the id / name remap. Returns {@code false} for a blank/unnamed entry that was
     * skipped, {@code true} when a persona was resolved and attached.
     *
     * @since 2026.3.1
     */
    private boolean resolveAndAttachPersona(TurPersonaDto incoming, TurAIAgent agent,
            Map<String, String> personaIdRemap, Map<String, String> personaNameById) {
        if (incoming == null || incoming.getName() == null || incoming.getName().isBlank()) {
            return false;
        }
        TurPersona resolved = turPersonaRepository
                .findByNameIgnoreCase(incoming.getName())
                .orElseGet(() -> createPersonaFromImport(incoming));
        if (incoming.getId() != null && !incoming.getId().isBlank()) {
            personaIdRemap.put(incoming.getId(), resolved.getId());
        }
        personaNameById.put(resolved.getId(), resolved.getName());
        agent.getPersonas().add(resolved);
        return true;
    }

    /**
     * Phase 2 of {@link #turChatFlowImportBundle}: maps each import item to a {@link TurChatFlow}
     * entity with a freshly assigned UUID (preserving {@code null} placeholders for malformed
     * items so the rewrite phase keeps positional alignment), and fills the sub-flow remap maps.
     *
     * @since 2026.3.1
     */
    private List<TurChatFlow> assignBundleFlowEntities(TurAIAgent agent, List<TurChatFlowImportDto> bundle,
            Map<String, String> subFlowIdRemap, Map<String, String> subFlowNameById) {
        List<TurChatFlow> entities = new ArrayList<>();
        for (TurChatFlowImportDto item : bundle) {
            if (item == null || item.getChatFlow() == null) {
                entities.add(null);
                continue;
            }
            TurChatFlowDto flow = item.getChatFlow();
            String placeholderId = flow.getId();
            String newId = UUID.randomUUID().toString();
            TurChatFlow entity = turChatFlowMapper.toEntity(flow);
            entity.setId(newId);
            entity.setTurAIAgent(agent);
            if (placeholderId != null && !placeholderId.isBlank()) {
                subFlowIdRemap.put(placeholderId, newId);
            }
            if (entity.getName() != null) {
                subFlowNameById.put(newId, entity.getName());
            }
            entities.add(entity);
        }
        return entities;
    }

    /**
     * Phase 3 of {@link #turChatFlowImportBundle}: rewrites each flow's graph with the complete
     * persona + sub-flow remaps, persists it, and returns the saved DTOs.
     *
     * @since 2026.3.1
     */
    private List<TurChatFlowDto> rewriteAndSaveBundleFlows(List<TurChatFlow> entities,
            Map<String, String> personaIdRemap, Map<String, String> personaNameById,
            Map<String, String> subFlowIdRemap, Map<String, String> subFlowNameById) {
        List<TurChatFlowDto> results = new ArrayList<>();
        for (TurChatFlow entity : entities) {
            if (entity == null) {
                continue;
            }
            String rewrittenJson = rewriteGraph(entity.getDefinitionJson(),
                    personaIdRemap, personaNameById,
                    subFlowIdRemap, subFlowNameById);
            entity.setDefinitionJson(rewrittenJson);
            turChatFlowRepository.save(entity);
            results.add(turChatFlowMapper.toDto(entity));
        }
        return results;
    }

    /**
     * T96 / §VII.11.f — installs a Turing Recipe onto the agent. Looks up the
     * bundled recipe by id, delegates the heavy lifting to the existing
     * {@link #turChatFlowImportBundle} pipeline (sub-flow auto-wiring,
     * persona / slot de-duplication, graph rewriting), and then stamps the
     * resulting flows with {@code installedFromRecipe} + {@code installedRecipeVersion}
     * so the editor can show the "Installed from {recipe} v{version}" badge
     * and future "upgrade available" UX has the source pointer it needs.
     *
     * @since 2026.3.1
     */
    @Operation(summary = "Install a Turing Recipe onto the agent")
    @PostMapping("/install-recipe/{recipeId}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_CREATE" })
    public List<TurChatFlowDto> turChatFlowInstallRecipe(@PathVariable String agentId,
            @PathVariable String recipeId) {
        TurChatFlowRecipeDto recipe = chatFlowRecipeService.get(recipeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Recipe '" + recipeId + "' not found"));
        if (recipe.getBundle() == null || recipe.getBundle().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Recipe '" + recipeId + "' carries no flow bundle");
        }
        // Reuse the existing bundle-import machinery so persona / slot
        // de-duplication and sub-flow wiring are handled exactly the same
        // way they would for a hand-crafted export — no parallel pipeline.
        List<TurChatFlowDto> created = turChatFlowImportBundle(agentId, recipe.getBundle());
        // Tag the audit trail. Done as a second pass so we don't pollute
        // the generic bundle importer with recipe-specific concerns.
        for (TurChatFlowDto dto : created) {
            if (dto == null || dto.getId() == null) continue;
            turChatFlowRepository.findById(dto.getId()).ifPresent(entity -> {
                entity.setInstalledFromRecipe(recipe.getId());
                entity.setInstalledRecipeVersion(recipe.getVersion());
                turChatFlowRepository.save(entity);
                // Mirror the audit columns onto the response DTO so the
                // frontend's "Installed from …" badge renders immediately.
                dto.setInstalledFromRecipe(recipe.getId());
                dto.setInstalledRecipeVersion(recipe.getVersion());
            });
        }
        return created;
    }

    /**
     * Auto-creates any embedded slot whose name is not already declared on the
     * target agent (case-insensitive match — slot names are unique per agent).
     * Existing slots are left untouched: an import never overwrites the type or
     * description of a slot the operator has already curated.
     *
     * @since 2026.2.7
     */
    private void resolveSlots(TurAIAgent agent, List<TurAIAgentSlotDto> slots) {
        if (slots == null || slots.isEmpty()) {
            return;
        }
        for (TurAIAgentSlotDto incoming : slots) {
            if (incoming == null || incoming.getName() == null
                    || incoming.getName().isBlank()) {
                continue;
            }
            if (turAIAgentSlotRepository
                    .findByTurAIAgent_IdAndName(agent.getId(), incoming.getName())
                    .isEmpty()) {
                TurAIAgentSlot entity = new TurAIAgentSlot();
                entity.setName(incoming.getName());
                entity.setDescription(incoming.getDescription());
                entity.setType(incoming.getType() != null ? incoming.getType() : TurAIAgentSlotType.STRING);
                entity.setTurAIAgent(agent);
                turAIAgentSlotRepository.save(entity);
            }
        }
    }

    /** Copies the editable fields from an imported persona DTO into a fresh entity. */
    private TurPersona createPersonaFromImport(TurPersonaDto dto) {
        TurPersona entity = new TurPersona();
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setSystemInstruction(dto.getSystemInstruction());
        entity.setTone(dto.getTone());
        // Default verbosity matches TurPersonaAPI's authoring contract — 3 is
        // the safe middle of the 1-5 scale when the export omitted the field.
        entity.setVerbosity(dto.getVerbosity() == 0 ? 3 : dto.getVerbosity());
        entity.setLanguageStyle(dto.getLanguageStyle());
        entity.setMandatoryTerms(dto.getMandatoryTerms());
        entity.setForbiddenTerms(dto.getForbiddenTerms());
        // 0 in the export usually means "absent field" (int default), not
        // "disabled" — coerce to enabled so newly-imported personas show up.
        entity.setEnabled(dto.getEnabled() == 0 ? 1 : dto.getEnabled());
        return turPersonaRepository.save(entity);
    }

    /**
     * Walks the chat-flow graph's persona-switch and sub-flow nodes and remaps each
     * {@code personaId} / {@code subFlowId} via the supplied lookup tables, also writing the
     * resolved display name into {@code personaName} / {@code subFlowName} so the canvas opens
     * with "Switches voice to: Captain Nova" and "Sub Flow: Venusian Resort Booking" right after
     * import. Done with a Map round-trip (instead of
     * {@link tools.jackson.databind.node.ObjectNode} mutations) so the implementation is resilient
     * to small shape differences in older exports — unrelated fields are preserved as-is.
     */
    @SuppressWarnings("unchecked")
    private static String rewriteGraph(String definitionJson,
            Map<String, String> personaRemap, Map<String, String> personaNameById,
            Map<String, String> subFlowRemap, Map<String, String> subFlowNameById) {
        if (definitionJson == null || definitionJson.isBlank()
                || (personaRemap.isEmpty() && personaNameById.isEmpty()
                        && subFlowRemap.isEmpty() && subFlowNameById.isEmpty())) {
            return definitionJson;
        }
        try {
            Map<String, Object> root = OBJECT_MAPPER.readValue(definitionJson, Map.class);
            Object nodesObj = root.get("nodes");
            if (nodesObj instanceof List<?> nodes) {
                for (Object n : nodes) {
                    if (!(n instanceof Map<?, ?> nodeRaw)) {
                        continue;
                    }
                    Object dataObj = nodeRaw.get("data");
                    if (dataObj instanceof Map<?, ?> dataRaw) {
                        Map<String, Object> data = (Map<String, Object>) dataRaw;
                        rewriteIdField(data, "personaId", "personaName", personaRemap, personaNameById);
                        rewriteIdField(data, "subFlowId", "subFlowName", subFlowRemap, subFlowNameById);
                    }
                }
            }
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (JacksonException e) {
            // Don't fail import if the graph is unparseable — fall back to the
            // original. The user can re-bind references manually in the editor.
            return definitionJson;
        }
    }

    /**
     * Remap a single reference field on a node's data map, and cache its resolved display name in
     * the sibling {@code …Name} field. Translates {@code data[idField]} via {@code remap} (no-op
     * when the id isn't in the remap — the import's id was already real) and writes
     * {@code nameById[resolvedId]} into {@code data[nameField]} when known.
     */
    private static void rewriteIdField(Map<String, Object> data,
            String idField, String nameField,
            Map<String, String> remap, Map<String, String> nameById) {
        Object idValue = data.get(idField);
        if (!(idValue instanceof String oldId) || oldId.isBlank()) {
            return;
        }
        String resolvedId = remap.getOrDefault(oldId, oldId);
        if (!resolvedId.equals(oldId)) {
            data.put(idField, resolvedId);
        }
        String resolvedName = nameById.get(resolvedId);
        if (resolvedName != null && !resolvedName.isBlank()) {
            data.put(nameField, resolvedName);
        }
    }

    @Operation(summary = "Update a Chat Flow of an Agent")
    @PutMapping("/{id}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public TurChatFlowDto turChatFlowUpdate(@PathVariable String agentId,
            @PathVariable String id,
            @RequestBody TurChatFlowDto turChatFlowDto) {
        // T287 / §XV.3 — pre-publish gate. When a golden set is marked
        // blocking and the latest eval run is red/regressed, refuse the
        // publish. Default sets are non-blocking (warn-only, surfaced in the
        // Lint panel), so this never bites until an author opts in.
        if (agentEvalGateService.shouldBlockPublish(agentId)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Publish blocked: the agent's eval gate is failing. "
                            + "Fix the regression or re-run the gate to a green baseline.");
        }
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
                    if (incoming.getCaptureMode() != null) {
                        existing.setCaptureMode(incoming.getCaptureMode());
                    }
                    // T53 / §VII.4.g — opt-in abandonment handoff offer (null/blank = legacy close).
                    existing.setAbandonHandoffMessage(incoming.getAbandonHandoffMessage());
                    existing.setTriggerDescription(incoming.getTriggerDescription());
                    if (incoming.getTriggerMode() != null) {
                        existing.setTriggerMode(incoming.getTriggerMode());
                    }
                    existing.setExperimentKey(incoming.getExperimentKey());
                    existing.setVariantLabel(incoming.getVariantLabel());
                    existing.setTrafficWeight(incoming.getTrafficWeight());
                    existing.setExperimentStartsAt(incoming.getExperimentStartsAt());
                    existing.setExperimentEndsAt(incoming.getExperimentEndsAt());
                    existing.setBanditEnabled(incoming.getBanditEnabled());
                    existing.setAutoPromote(incoming.getAutoPromote());
                    existing.setSlotInheritanceJson(incoming.getSlotInheritanceJson());
                    turChatFlowRepository.save(existing);
                    return turChatFlowMapper.toDto(existing);
                }).orElse(new TurChatFlowDto());
    }

    /**
     * T91 / §VII.11.a — surfaces pairs of trigger descriptions on this agent
     * that overlap enough for the procedural router to score them ambiguously.
     * The admin UI uses this to warn an author before the LLM-router fallback
     * silently masks a real intent collision.
     *
     * @since 2026.3.1
     */
    @Operation(summary = "Trigger description conflicts across this agent's chat flows")
    @GetMapping("/trigger-conflicts")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public List<TurChatFlowTriggerConflictDto> turChatFlowTriggerConflicts(
            @PathVariable String agentId) {
        requireAgentExists(agentId);
        return triggerConflictService.detect(agentId);
    }

    /**
     * T94 / §VII.11.d — static-analysis warnings for a single chat flow.
     * Powers the sidebar panel in the admin editor. Returns an empty list
     * when the flow is clean.
     *
     * @since 2026.3.1
     */
    @Operation(summary = "Static-analysis warnings for a single chat flow")
    @GetMapping("/{id}/lint")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public List<TurChatFlowLintIssueDto> turChatFlowLint(@PathVariable String agentId,
            @PathVariable String id) {
        return turChatFlowRepository.findById(id)
                .filter(flow -> flow.getTurAIAgent() != null
                        && agentId.equals(flow.getTurAIAgent().getId()))
                .map(flow -> chatFlowLinterService.lint(agentId, flow))
                .orElse(List.of());
    }

    /**
     * T97 / §VII.11.g — variant generator. Asks the default LLM to rewrite
     * the source flow's user-facing copy per the author's instruction and
     * returns an UNPERSISTED candidate. The author reviews it in the
     * variant dialog and saves through the standard create endpoint when
     * happy; nothing is written to the database here.
     *
     * @since 2026.3.1
     */
    @Operation(summary = "Generate a tone/length variant of an existing chat flow via LLM")
    @PostMapping("/{id}/variant")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_CREATE", "AI_AGENT_EDIT" })
    public TurChatFlowVariantResponse turChatFlowVariant(@PathVariable String agentId,
            @PathVariable String id,
            @RequestBody TurChatFlowVariantRequest request) {
        return turChatFlowRepository.findById(id)
                .filter(flow -> flow.getTurAIAgent() != null
                        && agentId.equals(flow.getTurAIAgent().getId()))
                .map(flow -> chatFlowVariantService.generate(flow, request))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Chat flow not found"));
    }

    /**
     * T236 / §VII.13.g — opt-in "tidy labels" polish on the T234 form
     * conversion. The editor's convert-to-form dialog sends the fields it
     * derived client-side (whose labels are raw question instructions like
     * "Qual o seu e-mail corporativo?") and gets back the same fields with
     * short, clean form labels ("E-mail"). Nothing is persisted — the dialog
     * applies the tidied labels only when the author confirms the conversion.
     *
     * @since 2026.3.1
     */
    @Operation(summary = "Tidy the labels of a converted form via the default LLM (T236)")
    @PostMapping("/tidy-labels")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_CREATE", "AI_AGENT_EDIT" })
    public TurFormLabelTidyResponse turChatFlowTidyLabels(@PathVariable String agentId,
            @RequestBody TurFormLabelTidyRequest request) {
        requireAgentExists(agentId);
        return formLabelTidyService.tidy(request == null ? null : request.fields());
    }

    /**
     * T85 / §VII.10.b — funnel statistics for a single chat flow. Returns
     * per-node cursor + completion counts so the admin editor can render
     * a turn-by-turn drop-off view. 404 when the flow id is unknown or
     * doesn't belong to the agent in the path.
     *
     * @since 2026.3.1
     */
    @Operation(summary = "Per-node funnel statistics for a chat flow")
    @GetMapping("/{id}/funnel")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public TurChatFlowFunnelService.FunnelReport turChatFlowFunnel(@PathVariable String agentId,
            @PathVariable String id) {
        return turChatFlowRepository.findById(id)
                .filter(flow -> flow.getTurAIAgent() != null
                        && agentId.equals(flow.getTurAIAgent().getId()))
                .flatMap(flow -> chatFlowFunnelService.computeFunnel(flow.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Chat flow not found"));
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
                    // Bulk-DML delete in the repository bypasses JPA entity
                    // callbacks; wipe the flow-derived caches here so the engine
                    // doesn't keep scoring against (or prompting from) a stale flow.
                    chatFlowEngineService.evictFlowDerivedCaches();
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
        requireAgentExists(agentId);
        return ChatFlowAuthoringSkill.isFirstTurn(request)
                ? chatFlowAuthoringSkill.create(request)
                : chatFlowAuthoringSkill.revise(request);
    }
}
