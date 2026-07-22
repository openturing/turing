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

import com.viglet.turing.exchange.agent.TurAIAgentExportService;
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
import com.viglet.turing.persistence.repository.sn.genai.TurSNSiteGenAiRepository;
import com.viglet.turing.persistence.repository.store.TurStoreInstanceRepository;
import com.viglet.turing.spring.utils.TurPersistenceUtils;
import com.viglet.turing.system.TurGlobalSettingsService;

import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
    private final com.viglet.turing.persistence.repository.se.TurSEInstanceRepository turSEInstanceRepository;
    private final TurPersonaRepository turPersonaRepository;
    private final TurSNSiteGenAiRepository turSNSiteGenAiRepository;
    private final TurGlobalSettingsService turGlobalSettingsService;
    private final TurAIAgentExportService turAIAgentExportService;

    public TurAIAgentAPI(TurAIAgentRepository turAIAgentRepository,
            TurAIAgentMapper turAIAgentMapper,
            TurLLMInstanceRepository turLLMInstanceRepository,
            TurMcpServerRepository turMcpServerRepository,
            TurCustomToolRepository turCustomToolRepository,
            TurEmbeddingModelRepository turEmbeddingModelRepository,
            TurStoreInstanceRepository turStoreInstanceRepository,
            com.viglet.turing.persistence.repository.se.TurSEInstanceRepository turSEInstanceRepository,
            TurPersonaRepository turPersonaRepository,
            TurSNSiteGenAiRepository turSNSiteGenAiRepository,
            TurGlobalSettingsService turGlobalSettingsService,
            TurAIAgentExportService turAIAgentExportService) {
        this.turAIAgentRepository = turAIAgentRepository;
        this.turAIAgentMapper = turAIAgentMapper;
        this.turLLMInstanceRepository = turLLMInstanceRepository;
        this.turMcpServerRepository = turMcpServerRepository;
        this.turCustomToolRepository = turCustomToolRepository;
        this.turEmbeddingModelRepository = turEmbeddingModelRepository;
        this.turStoreInstanceRepository = turStoreInstanceRepository;
        this.turSEInstanceRepository = turSEInstanceRepository;
        this.turPersonaRepository = turPersonaRepository;
        this.turSNSiteGenAiRepository = turSNSiteGenAiRepository;
        this.turGlobalSettingsService = turGlobalSettingsService;
        this.turAIAgentExportService = turAIAgentExportService;
    }

    @Operation(summary = "AI Agent List")
    @GetMapping
    @Secured({"ROLE_ADMIN", "AI_AGENT_VIEW"})
    public List<TurAIAgentDto> turAIAgentList() {
        return turAIAgentMapper
                .toDtoList(this.turAIAgentRepository.findAll(TurPersistenceUtils.orderByTitleIgnoreCase()));
    }

    @Operation(summary = "Export an AI Agent as a ZIP bundle",
            description = "Produces a ZIP containing agent.json (TurExchange envelope), one " +
                    "chat-flows/{slug}.chat-flow.json per chat flow, and one tools/{slug}.groovy " +
                    "per custom tool. API keys (LLM apiKey/apiKeyEncrypted) are never serialized.")
    @GetMapping(value = "/{id}/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Secured({"ROLE_ADMIN", "AI_AGENT_VIEW"})
    public StreamingResponseBody turAIAgentExport(@PathVariable String id,
            HttpServletResponse response) {
        TurAIAgent agent = turAIAgentRepository.findById(id).orElse(null);
        if (agent == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AI Agent not found: " + id);
        }
        Path zipPath = turAIAgentExportService.exportAgentToZip(id);
        String filename = "agent-" + safeFilenamePart(agent.getTitle(), id) + ".zip";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encodedFilename);
        return output -> {
            try {
                Files.copy(zipPath, output);
                output.flush();
            } catch (IOException e) {
                log.error("Failed to stream agent export for {}: {}", id, e.getMessage(), e);
            } finally {
                turAIAgentExportService.cleanup(zipPath);
            }
        };
    }

    private static String safeFilenamePart(String name, String id) {
        String base = (name == null ? "untitled" : name)
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-)|(-$)", "");
        if (base.isEmpty()) base = "untitled";
        return base + "-" + (id == null ? "" : id.substring(0, Math.min(8, id.length())));
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
            // T433 / §X.18.b — per-agent provider-native capability selection.
            agentEdit.setNativeCapabilities(turAIAgent.getNativeCapabilities());
            // T435 / §X.18.d — per-agent REQUEST_OPTION selections.
            agentEdit.setRequestOptionsJson(turAIAgent.getRequestOptionsJson());
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
            // T28 / §III.5 Phase C — per-agent SE binding for the analytics
            // intent index. Null clears the binding so the indexer falls back
            // to the global default (property / first registered SE).
            agentEdit.setAnalyticsSeInstance(resolveSingleRelation(
                    turAIAgent.getAnalyticsSeInstance(), turSEInstanceRepository));
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
                    agentEdit.getLlmInstances(),
                    agentEdit.getTurEmbeddingModelInstance(),
                    agentEdit.getTurStoreInstance()));
            // Rich-content rendering opt-in (```html / ```d2 live preview).
            // Field-by-field copy like the other toggles — omitting it would
            // silently drop the switch on save.
            agentEdit.setRichContentEnabled(turAIAgent.isRichContentEnabled());
            agentEdit.setDiscloseKnowledgeCutoff(turAIAgent.isDiscloseKnowledgeCutoff());
            // T442 — answer-as-an-app generative-UI client tools (opt-in).
            agentEdit.setAnswerAsAppEnabled(turAIAgent.isAnswerAsAppEnabled());
            // T443 — co-browse: agent drives the host page's real search UI (opt-in).
            agentEdit.setCoBrowseEnabled(turAIAgent.isCoBrowseEnabled());
            // T445 — ambient / proactive copilot (opt-in) + its signal threshold.
            agentEdit.setProactiveEnabled(turAIAgent.isProactiveEnabled());
            agentEdit.setProactiveThresholdSignals(turAIAgent.getProactiveThresholdSignals());
            // T446 — cross-conversation personal memory (opt-in).
            agentEdit.setUserMemoryEnabled(turAIAgent.isUserMemoryEnabled());
            // T447 — self-tuning loop (opt-in; opens human-approved suggestions).
            agentEdit.setSelfTuningEnabled(turAIAgent.isSelfTuningEnabled());
            // T448 — agent-to-agent handoff (opt-in) + the specialist allowlist.
            agentEdit.setAgentHandoffEnabled(turAIAgent.isAgentHandoffEnabled());
            agentEdit.setSpecialistAgentIds(turAIAgent.getSpecialistAgentIds());
            // T450 — embeddable action widget (opt-in host-action client tools).
            agentEdit.setActionWidgetEnabled(turAIAgent.isActionWidgetEnabled());
            // T323 — skills opt-in (run_skill delegation to the Default LLM).
            // Field-by-field copy like the other toggles.
            agentEdit.setSkillsEnabled(turAIAgent.isSkillsEnabled());
            // T145 — federate the agent's MCP servers to the native vendor.
            agentEdit.setMcpNativeFederation(turAIAgent.isMcpNativeFederation());
            // T436 — live tool-call events on the chat SSE (opt-in).
            agentEdit.setToolCallEventsEnabled(turAIAgent.isToolCallEventsEnabled());
            // T618 — capture the assembled prompt per turn for verbatim replay (opt-in).
            agentEdit.setPromptCaptureEnabled(turAIAgent.isPromptCaptureEnabled());
            // T438 — frontend ("client") tools (opt-in) + their declarations.
            agentEdit.setClientToolsEnabled(turAIAgent.isClientToolsEnabled());
            agentEdit.setClientToolsJson(turAIAgent.getClientToolsJson());
            // Chat memory configuration — silently dropped before this fix,
            // which is why toggling the UI switch had no effect after save.
            agentEdit.setChatMemoryEnabled(turAIAgent.isChatMemoryEnabled());
            agentEdit.setChatMemoryFlushIntervalMinutes(
                    Math.max(1, turAIAgent.getChatMemoryFlushIntervalMinutes()));
            agentEdit.setChatMemoryMaxMessages(
                    Math.max(1, turAIAgent.getChatMemoryMaxMessages()));
            // Agent-specific Python deps addendum — unioned with the global
            // GLOBAL_PYTHON_REQUIREMENTS by the Code Interpreter dep service
            // before pip install. NULL = global-only, the safe default that
            // preserves pre-2026.2.7 behavior for agents that don't need
            // anything extra.
            agentEdit.setPythonRequirements(turAIAgent.getPythonRequirements());
            // Chat mode (CALL / STREAM). Same field-by-field copy pattern as
            // the rest of this update: if the assignment is missing, Hibernate
            // silently keeps the previously-persisted value and the toggle in
            // the form has no effect after save. NPE-safe — keep current value
            // when the DTO omits the field (older clients).
            if (turAIAgent.getChatMode() != null) {
                agentEdit.setChatMode(turAIAgent.getChatMode());
            }
            // T66 / §VII.6.g — submission retention policy. Same field-by-field
            // copy contract: null DTO value keeps the previously-persisted mode
            // (older clients). The days threshold is copied verbatim (null is a
            // valid "no threshold" state for RETAIN_DAYS).
            if (turAIAgent.getSubmissionRetention() != null) {
                agentEdit.setSubmissionRetention(turAIAgent.getSubmissionRetention());
            }
            agentEdit.setSubmissionRetentionDays(turAIAgent.getSubmissionRetentionDays());
            // T166 / §X.9.d — memory tool scope (CONVERSATION / USER). Same
            // field-by-field copy contract: a null DTO value keeps the
            // previously-persisted scope (older clients).
            if (turAIAgent.getMemoryScope() != null) {
                agentEdit.setMemoryScope(turAIAgent.getMemoryScope());
            }
            // Grounding policy (OPEN / STRICT_RAG). Same field-by-field copy
            // contract: a null DTO value keeps the previously-persisted mode
            // (older clients), never nulling a non-null column.
            if (turAIAgent.getGroundingMode() != null) {
                agentEdit.setGroundingMode(turAIAgent.getGroundingMode());
            }
            // T19/T24 reposition (2026.2.7) — ragBm25Fallback and
            // ragHybridSearch moved to TurSNSiteGenAi; no longer copied
            // here. See TurSNSiteGenAi field javadoc + TurSNSiteAPI for
            // the persistence side, and the SN site GenAI settings form
            // for the UI.
            this.turAIAgentRepository.save(agentEdit);
            return turAIAgentMapper.toDto(agentEdit);
        }).orElse(new TurAIAgentDto());
    }

    @Transactional
    @Operation(summary = "Delete an AI Agent")
    @DeleteMapping("/{id}")
    @Secured({"ROLE_ADMIN", "AI_AGENT_DELETE"})
    public boolean turAIAgentDelete(@PathVariable String id) {
        // SN site GenAI configs may point at this agent via the
        // fk_sn_site_genai_ai_agent FK. Detach them first (null out agent_id)
        // so the site keeps its GenAI config but no longer references a
        // deleted agent — otherwise the FK rejects the agent delete.
        //
        // saveAndFlush (vs. save) is required because the agent delete on the
        // next line is a `@Modifying` JPQL bulk query that bypasses the JPA
        // persistence context: a plain save() schedules the UPDATE but the
        // database doesn't see it until flush, so the FK constraint check
        // fires against the still-old agent_id and rejects the delete.
        var attachedSites = this.turSNSiteGenAiRepository.findByTurAIAgent_Id(id);
        for (var siteGenAi : attachedSites) {
            siteGenAi.setTurAIAgent(null);
            this.turSNSiteGenAiRepository.saveAndFlush(siteGenAi);
        }
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
        // T28 / §III.5 Phase C — same per-agent SE binding propagation on create.
        turAIAgent.setAnalyticsSeInstance(resolveSingleRelation(
                turAIAgent.getAnalyticsSeInstance(), turSEInstanceRepository));
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
                turAIAgent.getLlmInstances(),
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
     * RAG can only be enabled on the agent when (a) at least one LLM instance
     * is bound — without an LLM there is nothing to generate the answer; and
     * (b) an embedding model and an embedding store are resolvable, either
     * set on the agent itself or via the global RAG defaults configured in
     * Global Settings. Silently flipping {@code ragEnabled} back to false
     * here keeps the data model consistent so {@code TurSNSiteGenAiAPI}'s
     * diagnostic walk never has to backtrack — but the admin frontend should
     * still block the toggle on its side for a useful error message.
     */
    private boolean canEnableRag(boolean requested,
            Set<com.viglet.turing.persistence.model.llm.TurLLMInstance> llmInstances,
            TurEmbeddingModel embeddingModel,
            TurStoreInstance storeInstance) {
        if (!requested) {
            return false;
        }
        boolean hasLlm = llmInstances != null && !llmInstances.isEmpty();
        boolean modelOk = embeddingModel != null
                || StringUtils.hasText(turGlobalSettingsService.getDefaultEmbeddingModelId());
        boolean storeOk = storeInstance != null
                || StringUtils.hasText(turGlobalSettingsService.getDefaultEmbeddingStoreId());
        return hasLlm && modelOk && storeOk;
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
