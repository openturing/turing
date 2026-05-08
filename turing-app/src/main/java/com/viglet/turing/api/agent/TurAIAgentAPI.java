package com.viglet.turing.api.agent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.dto.agent.TurAIAgentDto;
import com.viglet.turing.persistence.mapper.agent.TurAIAgentMapper;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.customtool.TurCustomToolRepository;
import com.viglet.turing.persistence.repository.embedding.TurEmbeddingModelRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.mcp.TurMcpServerRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.store.TurStoreInstanceRepository;
import com.viglet.turing.spring.utils.TurPersistenceUtils;
import com.viglet.turing.system.TurGlobalSettingsService;

import org.springframework.util.StringUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/ai-agent")
@Tag(name = "AI Agent", description = "AI Agent API")
public class TurAIAgentAPI {
    private final TurAIAgentRepository turAIAgentRepository;
    private final TurAIAgentMapper turAIAgentMapper;
    private final TurLLMInstanceRepository turLLMInstanceRepository;
    private final TurMcpServerRepository turMcpServerRepository;
    private final TurCustomToolRepository turCustomToolRepository;
    private final TurEmbeddingModelRepository turEmbeddingModelRepository;
    private final TurStoreInstanceRepository turStoreInstanceRepository;
    private final TurPersonaRepository turPersonaRepository;
    private final TurGlobalSettingsService turGlobalSettingsService;

    public TurAIAgentAPI(TurAIAgentRepository turAIAgentRepository,
            TurAIAgentMapper turAIAgentMapper,
            TurLLMInstanceRepository turLLMInstanceRepository,
            TurMcpServerRepository turMcpServerRepository,
            TurCustomToolRepository turCustomToolRepository,
            TurEmbeddingModelRepository turEmbeddingModelRepository,
            TurStoreInstanceRepository turStoreInstanceRepository,
            TurPersonaRepository turPersonaRepository,
            TurGlobalSettingsService turGlobalSettingsService) {
        this.turAIAgentRepository = turAIAgentRepository;
        this.turAIAgentMapper = turAIAgentMapper;
        this.turLLMInstanceRepository = turLLMInstanceRepository;
        this.turMcpServerRepository = turMcpServerRepository;
        this.turCustomToolRepository = turCustomToolRepository;
        this.turEmbeddingModelRepository = turEmbeddingModelRepository;
        this.turStoreInstanceRepository = turStoreInstanceRepository;
        this.turPersonaRepository = turPersonaRepository;
        this.turGlobalSettingsService = turGlobalSettingsService;
    }

    @Operation(summary = "AI Agent List")
    @GetMapping
    @Secured({"ROLE_ADMIN", "AI_AGENT_VIEW"})
    public List<TurAIAgentDto> turAIAgentList() {
        return turAIAgentMapper
                .toDtoList(this.turAIAgentRepository.findAll(TurPersistenceUtils.orderByTitleIgnoreCase()));
    }

    @Operation(summary = "AI Agent structure")
    @GetMapping("/structure")
    @Secured({"ROLE_ADMIN", "AI_AGENT_VIEW"})
    public TurAIAgentDto turAIAgentStructure() {
        return turAIAgentMapper.toDto(new TurAIAgent());
    }

    @Operation(summary = "Show an AI Agent")
    @GetMapping("/{id}")
    @Secured({"ROLE_ADMIN", "AI_AGENT_VIEW"})
    public TurAIAgentDto turAIAgentGet(@PathVariable String id) {
        return turAIAgentMapper
                .toDto(this.turAIAgentRepository.findById(id).orElse(new TurAIAgent()));
    }

    @Operation(summary = "Update an AI Agent")
    @PutMapping("/{id}")
    @Secured({"ROLE_ADMIN", "AI_AGENT_EDIT"})
    public TurAIAgentDto turAIAgentUpdate(@PathVariable String id,
            @RequestBody TurAIAgentDto turAIAgentDto) {
        TurAIAgent turAIAgent = turAIAgentMapper.toEntity(turAIAgentDto);
        return turAIAgentRepository.findById(id).map(agentEdit -> {
            agentEdit.setTitle(turAIAgent.getTitle());
            agentEdit.setDescription(turAIAgent.getDescription());
            agentEdit.setIcon(turAIAgent.getIcon());
            agentEdit.setSystemPrompt(turAIAgent.getSystemPrompt());
            agentEdit.setSystemPromptMetaPrompt(turAIAgent.getSystemPromptMetaPrompt());
            agentEdit.setEnabled(turAIAgent.getEnabled());
            agentEdit.setNativeTools(turAIAgent.getNativeTools());
            agentEdit.setLlmInstances(resolveRelations(
                    turAIAgent.getLlmInstances(), turLLMInstanceRepository));
            agentEdit.setMcpServers(resolveRelations(
                    turAIAgent.getMcpServers(), turMcpServerRepository));
            agentEdit.setCustomTools(resolveRelations(
                    turAIAgent.getCustomTools(), turCustomToolRepository));
            agentEdit.setTurEmbeddingModelInstance(resolveSingleRelation(
                    turAIAgent.getTurEmbeddingModelInstance(), turEmbeddingModelRepository));
            agentEdit.setTurStoreInstance(resolveSingleRelation(
                    turAIAgent.getTurStoreInstance(), turStoreInstanceRepository));
            // Personas: catalog (M2M) + default (M2O). The default, when set,
            // must belong to the catalog — otherwise the runtime resolver
            // would silently fall back to null on every turn. We enforce it
            // here once instead of every chat request.
            //
            // We resolve the default by id directly from the repo (rather than
            // through resolveSingleRelation) because TurPersona doesn't
            // implement Persistable, and the generic helper falls through to
            // returning the detached DTO — Hibernate's M2O FK update is
            // sensitive enough that a managed entity is the safe path.
            Set<TurPersona> personaCatalog = resolveRelations(
                    turAIAgent.getPersonas(), turPersonaRepository);
            agentEdit.setPersonas(personaCatalog);
            TurPersona defaultPersona = null;
            if (turAIAgent.getDefaultPersona() != null
                    && turAIAgent.getDefaultPersona().getId() != null) {
                defaultPersona = turPersonaRepository
                        .findById(turAIAgent.getDefaultPersona().getId())
                        .orElse(null);
            }
            if (defaultPersona != null && !containsById(personaCatalog, defaultPersona.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Default persona must be one of the agent's allowed personas");
            }
            agentEdit.setDefaultPersona(defaultPersona);
            agentEdit.setRagEnabled(canEnableRag(turAIAgent.isRagEnabled(),
                    agentEdit.getTurEmbeddingModelInstance(),
                    agentEdit.getTurStoreInstance()));
            // Chat memory configuration — silently dropped before this fix,
            // which is why toggling the UI switch had no effect after save.
            agentEdit.setChatMemoryEnabled(turAIAgent.isChatMemoryEnabled());
            agentEdit.setChatMemoryFlushIntervalMinutes(
                    Math.max(1, turAIAgent.getChatMemoryFlushIntervalMinutes()));
            agentEdit.setChatMemoryMaxMessages(
                    Math.max(1, turAIAgent.getChatMemoryMaxMessages()));
            this.turAIAgentRepository.save(agentEdit);
            return turAIAgentMapper.toDto(agentEdit);
        }).orElse(new TurAIAgentDto());
    }

    @Transactional
    @Operation(summary = "Delete an AI Agent")
    @DeleteMapping("/{id}")
    @Secured({"ROLE_ADMIN", "AI_AGENT_DELETE"})
    public boolean turAIAgentDelete(@PathVariable String id) {
        this.turAIAgentRepository.delete(id);
        return true;
    }

    @Operation(summary = "Create an AI Agent")
    @PostMapping
    @Secured({"ROLE_ADMIN", "AI_AGENT_CREATE"})
    public TurAIAgentDto turAIAgentAdd(@RequestBody TurAIAgentDto turAIAgentDto) {
        TurAIAgent turAIAgent = turAIAgentMapper.toEntity(turAIAgentDto);
        turAIAgent.setLlmInstances(resolveRelations(
                turAIAgent.getLlmInstances(), turLLMInstanceRepository));
        turAIAgent.setMcpServers(resolveRelations(
                turAIAgent.getMcpServers(), turMcpServerRepository));
        turAIAgent.setCustomTools(resolveRelations(
                turAIAgent.getCustomTools(), turCustomToolRepository));
        turAIAgent.setTurEmbeddingModelInstance(resolveSingleRelation(
                turAIAgent.getTurEmbeddingModelInstance(), turEmbeddingModelRepository));
        turAIAgent.setTurStoreInstance(resolveSingleRelation(
                turAIAgent.getTurStoreInstance(), turStoreInstanceRepository));
        Set<TurPersona> personaCatalog = resolveRelations(
                turAIAgent.getPersonas(), turPersonaRepository);
        turAIAgent.setPersonas(personaCatalog);
        TurPersona defaultPersona = null;
        if (turAIAgent.getDefaultPersona() != null
                && turAIAgent.getDefaultPersona().getId() != null) {
            defaultPersona = turPersonaRepository
                    .findById(turAIAgent.getDefaultPersona().getId())
                    .orElse(null);
        }
        if (defaultPersona != null && !containsById(personaCatalog, defaultPersona.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Default persona must be one of the agent's allowed personas");
        }
        turAIAgent.setDefaultPersona(defaultPersona);
        turAIAgent.setRagEnabled(canEnableRag(turAIAgent.isRagEnabled(),
                turAIAgent.getTurEmbeddingModelInstance(),
                turAIAgent.getTurStoreInstance()));
        this.turAIAgentRepository.save(turAIAgent);
        return turAIAgentMapper.toDto(turAIAgent);
    }

    /**
     * True when {@code catalog} contains a persona with the given id.
     * Defensive against null entries (in case the resolver returns a
     * managed entity with a null id from a partial relation).
     */
    private static boolean containsById(Set<TurPersona> catalog, String id) {
        if (catalog == null || id == null) return false;
        return catalog.stream().anyMatch(p -> p != null && id.equals(p.getId()));
    }

    /**
     * Resolves a single ManyToOne relation. If the incoming entity has an id,
     * fetches the managed instance from the repo. Returns null if not found or
     * if the input was null.
     */
    private <T> T resolveSingleRelation(
            T incoming,
            org.springframework.data.jpa.repository.JpaRepository<T, String> repo) {
        if (incoming == null) {
            return null;
        }
        if (incoming instanceof org.springframework.data.domain.Persistable) {
            var persistable = (org.springframework.data.domain.Persistable<?>) incoming;
            String id = (String) persistable.getId();
            if (id == null) {
                return null;
            }
            return repo.findById(id).orElse(null);
        }
        return incoming;
    }

    /**
     * RAG can only be enabled on the agent when an embedding model and an
     * embedding store are resolvable — either set on the agent itself, or via
     * the global RAG defaults configured in Global Settings.
     */
    private boolean canEnableRag(boolean requested,
            TurEmbeddingModel embeddingModel,
            TurStoreInstance storeInstance) {
        if (!requested) {
            return false;
        }
        boolean modelOk = embeddingModel != null
                || StringUtils.hasText(turGlobalSettingsService.getDefaultEmbeddingModelId());
        boolean storeOk = storeInstance != null
                || StringUtils.hasText(turGlobalSettingsService.getDefaultEmbeddingStoreId());
        return modelOk && storeOk;
    }

    private <T> Set<T> resolveRelations(
            Set<T> incoming,
            org.springframework.data.jpa.repository.JpaRepository<T, String> repo) {
        if (incoming == null || incoming.isEmpty()) {
            return new HashSet<>();
        }
        return incoming.stream()
                .flatMap(entity -> {
                    if (entity instanceof org.springframework.data.domain.Persistable) {
                        var persistable = (org.springframework.data.domain.Persistable<?>) entity;
                        String id = (String) persistable.getId();
                        return repo.findById(id).stream();
                    }
                    return java.util.stream.Stream.of(entity);
                })
                .collect(Collectors.toSet());
    }
}
