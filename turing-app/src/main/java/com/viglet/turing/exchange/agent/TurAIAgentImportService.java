/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.exchange.agent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.exchange.TurExchange;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAIAgentSlot;
import com.viglet.turing.persistence.model.agent.TurAgentEvalCase;
import com.viglet.turing.persistence.model.agent.TurAgentEvalSet;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.customtool.TurCustomTool;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.intent.TurIntent;
import com.viglet.turing.persistence.model.intent.TurIntentAction;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurAIAgentSlotRepository;
import com.viglet.turing.persistence.repository.agent.TurAgentEvalSetRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.customtool.TurCustomToolRepository;
import com.viglet.turing.persistence.repository.embedding.TurEmbeddingModelRepository;
import com.viglet.turing.persistence.repository.intent.TurIntentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.mcp.TurMcpServerRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.store.TurStoreInstanceRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Imports the AI-agent half of a {@link TurExchange} envelope: resolves
 * shared entities (persona, LLM, MCP server, store, embedding, custom tool)
 * by ID first then by name, and upserts the agent itself plus its owned
 * children (slots, intents, chat-flows).
 *
 * <p>External files referenced from the envelope ({@code definitionFile} on
 * each chat flow and {@code groovyScriptFile} on each custom tool) are read
 * from the extracted ZIP folder; the service receives the folder as a
 * parameter rather than fetching the ZIP itself, keeping it decoupled from
 * the multipart pipeline in {@link com.viglet.turing.exchange.TurImportExchange}.
 *
 * <p>Persona-ID remapping for chat-flow graphs: when an export's persona
 * UUID doesn't match anything in the target DB (typical cross-environment
 * import), the upsert resolves by name and produces a brand new persisted
 * UUID. The chat-flow JSON carries {@code data.personaId} fields inside
 * its React-Flow node graph that reference the original UUID — every one
 * of those is rewritten before the flow is saved, otherwise the flow loads
 * but the personas don't resolve at runtime.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.8
 */
@Slf4j
@Service
public class TurAIAgentImportService {

    private final TurAIAgentRepository agentRepository;
    private final TurChatFlowRepository chatFlowRepository;
    private final TurAIAgentSlotRepository slotRepository;
    private final TurAgentEvalSetRepository evalSetRepository;
    private final TurIntentRepository intentRepository;
    private final TurPersonaRepository personaRepository;
    private final TurLLMInstanceRepository llmRepository;
    private final TurMcpServerRepository mcpServerRepository;
    private final TurStoreInstanceRepository storeRepository;
    private final TurEmbeddingModelRepository embeddingRepository;
    private final TurCustomToolRepository customToolRepository;

    public TurAIAgentImportService(TurAIAgentRepository agentRepository,
                                   TurChatFlowRepository chatFlowRepository,
                                   TurAIAgentSlotRepository slotRepository,
                                   TurAgentEvalSetRepository evalSetRepository,
                                   TurIntentRepository intentRepository,
                                   TurPersonaRepository personaRepository,
                                   TurLLMInstanceRepository llmRepository,
                                   TurMcpServerRepository mcpServerRepository,
                                   TurStoreInstanceRepository storeRepository,
                                   TurEmbeddingModelRepository embeddingRepository,
                                   TurCustomToolRepository customToolRepository) {
        this.agentRepository = agentRepository;
        this.chatFlowRepository = chatFlowRepository;
        this.slotRepository = slotRepository;
        this.evalSetRepository = evalSetRepository;
        this.intentRepository = intentRepository;
        this.personaRepository = personaRepository;
        this.llmRepository = llmRepository;
        this.mcpServerRepository = mcpServerRepository;
        this.storeRepository = storeRepository;
        this.embeddingRepository = embeddingRepository;
        this.customToolRepository = customToolRepository;
    }

    public record AgentConflict(String id, String name) {}

    public record ImportResult(List<String> importedAgentNames, List<String> skippedAgentNames,
                               int personasResolved, int llmInstancesResolved,
                               int mcpServersResolved, int customToolsResolved,
                               int chatFlowsImported, String error) {

        public static ImportResult error(String error) {
            return new ImportResult(List.of(), List.of(), 0, 0, 0, 0, 0, error);
        }

        public static ImportResult empty() {
            return new ImportResult(List.of(), List.of(), 0, 0, 0, 0, 0, null);
        }
    }

    /**
     * Returns the agents in {@code exchange} that already exist in the
     * database (matched by ID), so the import endpoint can prompt the user
     * before overwriting.
     */
    public List<AgentConflict> detectConflicts(TurExchange exchange) {
        if (exchange.getAgents() == null) return List.of();
        return exchange.getAgents().stream()
                .filter(a -> a.getId() != null && agentRepository.findById(a.getId()).isPresent())
                .map(a -> new AgentConflict(a.getId(), a.getTitle()))
                .toList();
    }

    /**
     * Imports every agent in {@code exchange}. {@code overwrite=false} skips
     * any agent already in the DB (matched by ID or title). {@code extractFolder}
     * is the unzipped bundle root — used to read chat-flow definition files
     * and custom-tool Groovy scripts.
     */
    @Transactional
    public ImportResult importAgents(TurExchange exchange, File extractFolder, boolean overwrite) {
        if (exchange.getAgents() == null || exchange.getAgents().isEmpty()) {
            return ImportResult.empty();
        }
        try {
            ResolvedRefs refs = resolveAllReferences(exchange, extractFolder);
            return upsertAgents(exchange, refs, extractFolder, overwrite);
        } catch (Exception e) {
            log.error("Failed to import agents: {}", e.getMessage(), e);
            return ImportResult.error(e.getMessage());
        }
    }

    // ─────────────────── Reference resolution ───────────────────

    /**
     * Resolves every shared entity referenced by the agents. Records old-ID →
     * new-ID mappings for {@link ResolvedRefs#personaIdRemap} so chat-flow
     * graphs can be rewritten before save.
     */
    private ResolvedRefs resolveAllReferences(TurExchange exchange, File extractFolder) {
        ResolvedRefs refs = new ResolvedRefs();
        if (exchange.getPersonas() != null) {
            for (TurPersona p : exchange.getPersonas()) {
                String originalId = p.getId();
                TurPersona resolved = upsertPersona(p);
                refs.personas.put(originalId, resolved);
                if (!originalId.equals(resolved.getId())) {
                    refs.personaIdRemap.put(originalId, resolved.getId());
                }
            }
        }
        if (exchange.getLlm() != null) {
            for (TurLLMInstance llm : exchange.getLlm()) {
                refs.llms.put(llm.getId(), upsertLLM(llm));
            }
        }
        if (exchange.getMcpServers() != null) {
            for (TurMcpServer mcp : exchange.getMcpServers()) {
                refs.mcpServers.put(mcp.getId(), upsertMcpServer(mcp));
            }
        }
        if (exchange.getStore() != null) {
            for (TurStoreInstance s : exchange.getStore()) {
                refs.stores.put(s.getId(), upsertStore(s));
            }
        }
        if (exchange.getEmbeddingModels() != null) {
            for (TurEmbeddingModel em : exchange.getEmbeddingModels()) {
                refs.embeddings.put(em.getId(), upsertEmbedding(em, refs));
            }
        }
        if (exchange.getCustomTools() != null) {
            for (TurCustomToolExchange tool : exchange.getCustomTools()) {
                refs.customTools.put(tool.getId(), upsertCustomTool(tool, extractFolder));
            }
        }
        return refs;
    }

    private TurPersona upsertPersona(TurPersona p) {
        TurPersona target = personaRepository.findById(p.getId())
                .or(() -> personaRepository.findByNameIgnoreCase(p.getName()))
                .orElseGet(TurPersona::new);
        target.setName(p.getName());
        target.setDescription(p.getDescription());
        target.setSystemInstruction(p.getSystemInstruction());
        target.setTone(p.getTone());
        target.setVerbosity(p.getVerbosity());
        target.setLanguageStyle(p.getLanguageStyle());
        target.setMandatoryTerms(p.getMandatoryTerms());
        target.setForbiddenTerms(p.getForbiddenTerms());
        target.setEnabled(p.getEnabled());
        return personaRepository.save(target);
    }

    private TurLLMInstance upsertLLM(TurLLMInstance llm) {
        // ApiKey is excluded from the export envelope by design (Jackson
        // strips @Transient / @JsonIgnore). For a brand-new instance the
        // import leaves apiKey blank — the operator re-enters it manually
        // post-import. For an existing instance matched by ID or title, the
        // existing apiKey on the persisted row is preserved (we only copy
        // non-secret fields below).
        TurLLMInstance target = llmRepository.findById(llm.getId())
                .or(() -> llmRepository.findByTitleIgnoreCase(llm.getTitle()))
                .orElseGet(TurLLMInstance::new);
        target.setTitle(llm.getTitle());
        target.setDescription(llm.getDescription());
        target.setIcon(llm.getIcon());
        target.setEnabled(llm.getEnabled());
        target.setUrl(llm.getUrl());
        target.setTurLLMVendor(llm.getTurLLMVendor());
        target.setModelName(llm.getModelName());
        target.setTemperature(llm.getTemperature());
        target.setTopK(llm.getTopK());
        target.setTopP(llm.getTopP());
        target.setRepeatPenalty(llm.getRepeatPenalty());
        target.setSeed(llm.getSeed());
        target.setNumPredict(llm.getNumPredict());
        target.setStop(llm.getStop());
        target.setResponseFormat(llm.getResponseFormat());
        target.setSupportedCapabilities(llm.getSupportedCapabilities());
        target.setTimeout(llm.getTimeout());
        target.setMaxRetries(llm.getMaxRetries());
        target.setContextWindow(llm.getContextWindow());
        target.setProviderOptionsJson(llm.getProviderOptionsJson());
        target.setToolsEnabled(llm.isToolsEnabled());
        return llmRepository.save(target);
    }

    private TurMcpServer upsertMcpServer(TurMcpServer mcp) {
        TurMcpServer target = mcpServerRepository.findById(mcp.getId())
                .or(() -> mcpServerRepository.findByTitleIgnoreCase(mcp.getTitle()))
                .orElseGet(TurMcpServer::new);
        target.setTitle(mcp.getTitle());
        target.setDescription(mcp.getDescription());
        target.setIcon(mcp.getIcon());
        target.setUrl(mcp.getUrl());
        target.setCommand(mcp.getCommand());
        target.setArgs(mcp.getArgs());
        target.setType(mcp.getType());
        target.setConnectionType(mcp.getConnectionType());
        target.setEnabled(mcp.getEnabled());
        return mcpServerRepository.save(target);
    }

    private TurStoreInstance upsertStore(TurStoreInstance s) {
        TurStoreInstance target = storeRepository.findById(s.getId())
                .or(() -> storeRepository.findByTitleIgnoreCase(s.getTitle()))
                .orElseGet(TurStoreInstance::new);
        target.setTitle(s.getTitle());
        target.setDescription(s.getDescription());
        target.setIcon(s.getIcon());
        target.setEnabled(s.getEnabled());
        target.setUrl(s.getUrl());
        target.setCollectionName(s.getCollectionName());
        target.setProviderOptionsJson(s.getProviderOptionsJson());
        target.setTurStoreVendor(s.getTurStoreVendor());
        // credentialEncrypted intentionally not copied — secret stays on
        // the existing row if updating, blank on new rows.
        return storeRepository.save(target);
    }

    private TurEmbeddingModel upsertEmbedding(TurEmbeddingModel em, ResolvedRefs refs) {
        TurEmbeddingModel target = embeddingRepository.findById(em.getId())
                .or(() -> embeddingRepository.findByModelNameIgnoreCase(em.getModelName()))
                .orElseGet(TurEmbeddingModel::new);
        target.setModelName(em.getModelName());
        target.setDescription(em.getDescription());
        target.setIcon(em.getIcon());
        target.setProviderType(em.getProviderType());
        target.setModelReference(em.getModelReference());
        target.setModelPath(em.getModelPath());
        target.setTokenizerPath(em.getTokenizerPath());
        target.setEnabled(em.getEnabled());
        target.setBatchSize(em.getBatchSize());
        // The export carries the linked LLM instance ID; resolve through
        // the already-upserted map so we don't dangle to a non-existent row.
        if (em.getTurLLMInstance() != null && em.getTurLLMInstance().getId() != null) {
            TurLLMInstance resolved = refs.llms.get(em.getTurLLMInstance().getId());
            target.setTurLLMInstance(resolved != null ? resolved : em.getTurLLMInstance());
        }
        return embeddingRepository.save(target);
    }

    private TurCustomTool upsertCustomTool(TurCustomToolExchange tool, File extractFolder) {
        TurCustomTool target = customToolRepository.findById(tool.getId())
                .or(() -> customToolRepository.findByTitleIgnoreCase(tool.getTitle()))
                .orElseGet(TurCustomTool::new);
        target.setTitle(tool.getTitle());
        target.setDescription(tool.getDescription());
        target.setDescriptionMetaPrompt(tool.getDescriptionMetaPrompt());
        target.setIcon(tool.getIcon());
        target.setLlmDescription(tool.getLlmDescription());
        target.setLlmDescriptionMetaPrompt(tool.getLlmDescriptionMetaPrompt());
        target.setGroovyMetaPrompt(tool.getGroovyMetaPrompt());
        target.setParametersJson(tool.getParametersJson());
        target.setReturnType(tool.getReturnType());
        target.setEnabled(tool.getEnabled());
        // Load Groovy script from the sibling file in the ZIP — the metadata
        // envelope only carries the filename reference to keep the JSON
        // diff-friendly.
        String script = readSiblingFile(extractFolder, tool.getGroovyScriptFile());
        if (script != null) {
            target.setGroovyScript(script);
        }
        return customToolRepository.save(target);
    }

    // ─────────────────── Agent upsert ───────────────────

    private ImportResult upsertAgents(TurExchange exchange, ResolvedRefs refs,
                                      File extractFolder, boolean overwrite) {
        List<String> imported = new java.util.ArrayList<>();
        List<String> skipped = new java.util.ArrayList<>();
        int chatFlowsCount = 0;
        for (TurAIAgentExchange a : exchange.getAgents()) {
            TurAIAgent existing = agentRepository.findById(a.getId())
                    .or(() -> agentRepository.findByTitleIgnoreCase(a.getTitle()))
                    .orElse(null);
            if (existing != null && !overwrite) {
                log.info("Skipping agent '{}' — already exists and overwrite=false", a.getTitle());
                skipped.add(a.getTitle());
                continue;
            }
            TurAIAgent agent = existing != null ? existing : new TurAIAgent();
            applyAgentScalars(agent, a);
            wireAgentReferences(agent, a, refs);
            agent = agentRepository.save(agent);
            replaceChildren(agent, a, refs, extractFolder);
            chatFlowsCount += a.getChatFlows() == null ? 0 : a.getChatFlows().size();
            imported.add(agent.getTitle());
            log.info("Imported agent '{}' (id={})", agent.getTitle(), agent.getId());
        }
        int personasCount = refs.personas.size();
        int llmsCount = refs.llms.size();
        int mcpsCount = refs.mcpServers.size();
        int toolsCount = refs.customTools.size();
        return new ImportResult(imported, skipped, personasCount, llmsCount, mcpsCount,
                toolsCount, chatFlowsCount, null);
    }

    private static void applyAgentScalars(TurAIAgent agent, TurAIAgentExchange a) {
        agent.setTitle(a.getTitle());
        agent.setDescription(a.getDescription());
        agent.setIcon(a.getIcon());
        agent.setSystemPrompt(a.getSystemPrompt());
        agent.setSystemPromptMetaPrompt(a.getSystemPromptMetaPrompt());
        agent.setEnabled(a.getEnabled());
        agent.setRagEnabled(a.isRagEnabled());
        agent.setChatMemoryEnabled(a.isChatMemoryEnabled());
        agent.setChatMemoryFlushIntervalMinutes(a.getChatMemoryFlushIntervalMinutes());
        agent.setChatMemoryMaxMessages(a.getChatMemoryMaxMessages());
        agent.setChatMemoryRelevanceEnabled(a.isChatMemoryRelevanceEnabled());
        agent.setChatMemoryRelevanceTopK(a.getChatMemoryRelevanceTopK());
        agent.setChatMemoryRecentN(a.getChatMemoryRecentN());
        // T115 / §IX.3.e — compression knobs. Interval is null-guarded so
        // pre-T115 exports keep the entity default (PT1H) instead of nulling a
        // non-null column.
        agent.setChatMemoryCompressionEnabled(a.isChatMemoryCompressionEnabled());
        agent.setChatMemoryCompressionThresholdTokens(a.getChatMemoryCompressionThresholdTokens());
        agent.setChatMemoryCompressionLlmId(a.getChatMemoryCompressionLlmId());
        if (a.getChatMemoryCompressionInterval() != null && !a.getChatMemoryCompressionInterval().isBlank()) {
            agent.setChatMemoryCompressionInterval(a.getChatMemoryCompressionInterval());
        }
        // T309 — null-guarded so pre-T309 exports keep the entity default (async on).
        if (a.getChatMemoryCompressionAsync() != null) {
            agent.setChatMemoryCompressionAsync(a.getChatMemoryCompressionAsync());
        }
        agent.setNativeTools(a.getNativeTools());
        agent.setPythonRequirements(a.getPythonRequirements());
        // T66 / §VII.6.g — null-guarded so pre-T66 exports keep the entity
        // default (RETAIN_FOREVER) instead of nulling a non-null column.
        if (a.getSubmissionRetention() != null) {
            agent.setSubmissionRetention(a.getSubmissionRetention());
        }
        agent.setSubmissionRetentionDays(a.getSubmissionRetentionDays());
    }

    private static void wireAgentReferences(TurAIAgent agent, TurAIAgentExchange a, ResolvedRefs refs) {
        agent.setPersonas(resolveSet(a.getPersonaIds(), refs.personas::get));
        if (a.getDefaultPersonaId() != null) {
            agent.setDefaultPersona(refs.personas.get(a.getDefaultPersonaId()));
        }
        agent.setLlmInstances(resolveSet(a.getLlmInstanceIds(), refs.llms::get));
        agent.setMcpServers(resolveSet(a.getMcpServerIds(), refs.mcpServers::get));
        agent.setCustomTools(resolveSet(a.getCustomToolIds(), refs.customTools::get));
        if (a.getStoreInstanceId() != null) {
            agent.setTurStoreInstance(refs.stores.get(a.getStoreInstanceId()));
        }
        if (a.getEmbeddingModelInstanceId() != null) {
            agent.setTurEmbeddingModelInstance(refs.embeddings.get(a.getEmbeddingModelInstanceId()));
        }
    }

    private static <T> java.util.Set<T> resolveSet(List<String> ids, Function<String, T> resolver) {
        java.util.Set<T> result = new HashSet<>();
        if (ids == null) return result;
        for (String id : ids) {
            T entity = resolver.apply(id);
            if (entity != null) {
                result.add(entity);
            }
        }
        return result;
    }

    // ─────────────────── Children (slots, intents, chat flows) ───────────────────

    private void replaceChildren(TurAIAgent agent, TurAIAgentExchange a,
                                 ResolvedRefs refs, File extractFolder) {
        replaceSlots(agent, a);
        replaceIntents(agent, a);
        replaceChatFlows(agent, a, refs, extractFolder);
        replaceEvalSets(agent, a);
    }

    /**
     * T285 — wholesale replace the agent's golden eval sets (cases inline).
     * Fresh ids are assigned so a cross-environment import never collides with
     * an existing set/case row; cascade persists the cases.
     */
    private void replaceEvalSets(TurAIAgent agent, TurAIAgentExchange a) {
        evalSetRepository.deleteAll(evalSetRepository.findByTurAIAgent_IdOrderByNameAsc(agent.getId()));
        if (a.getEvalSets() == null) {
            return;
        }
        for (TurAgentEvalSet set : a.getEvalSets()) {
            set.setId(null);
            set.setTurAIAgent(agent);
            if (set.getCases() != null) {
                for (TurAgentEvalCase c : set.getCases()) {
                    c.setId(null);
                    c.setTurAgentEvalSet(set);
                }
            }
            evalSetRepository.save(set);
        }
    }

    private void replaceSlots(TurAIAgent agent, TurAIAgentExchange a) {
        slotRepository.deleteAll(slotRepository.findByTurAIAgent_IdOrderByNameAsc(agent.getId()));
        if (a.getSlots() == null) return;
        for (TurAIAgentSlot s : a.getSlots()) {
            s.setTurAIAgent(agent);
            slotRepository.save(s);
        }
    }

    private void replaceIntents(TurAIAgent agent, TurAIAgentExchange a) {
        intentRepository.deleteAll(intentRepository.findByTurAIAgent_IdOrderByTitleAsc(agent.getId()));
        if (a.getIntents() == null) return;
        for (TurIntent i : a.getIntents()) {
            i.setTurAIAgent(agent);
            if (i.getActions() != null) {
                for (TurIntentAction action : i.getActions()) {
                    action.setTurIntent(i);
                }
            }
            intentRepository.save(i);
        }
    }

    private void replaceChatFlows(TurAIAgent agent, TurAIAgentExchange a,
                                  ResolvedRefs refs, File extractFolder) {
        chatFlowRepository.deleteAll(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(agent.getId()));
        if (a.getChatFlows() == null) return;
        for (TurChatFlowExchange fe : a.getChatFlows()) {
            TurChatFlow flow = new TurChatFlow();
            flow.setName(fe.getName());
            flow.setDescription(fe.getDescription());
            flow.setEnabled(fe.getEnabled());
            flow.setGuardrailMethod(fe.getGuardrailMethod());
            if (fe.getCaptureMode() != null) {
                flow.setCaptureMode(fe.getCaptureMode());
            }
            flow.setTriggerDescription(fe.getTriggerDescription());
            flow.setTriggerMode(fe.getTriggerMode());
            flow.setExperimentKey(fe.getExperimentKey());
            flow.setVariantLabel(fe.getVariantLabel());
            flow.setTrafficWeight(fe.getTrafficWeight());
            flow.setExperimentStartsAt(fe.getExperimentStartsAt());
            flow.setExperimentEndsAt(fe.getExperimentEndsAt());
            flow.setBanditEnabled(fe.getBanditEnabled());
            flow.setAutoPromote(fe.getAutoPromote());
            flow.setTurAIAgent(agent);
            String rawDefinition = readSiblingFile(extractFolder, fe.getDefinitionFile());
            flow.setDefinitionJson(rewritePersonaIds(rawDefinition, refs.personaIdRemap));
            chatFlowRepository.save(flow);
        }
    }

    /**
     * Rewrites every {@code data.personaId} occurrence inside the React-Flow
     * graph from old UUID → new UUID. Necessary because the export's persona
     * UUID may not match the upserted persona's UUID when the import resolved
     * by name (cross-environment promotion).
     */
    private static String rewritePersonaIds(String definitionJson, Map<String, String> remap) {
        if (definitionJson == null || definitionJson.isBlank() || remap.isEmpty()) {
            return definitionJson;
        }
        try {
            ObjectMapper mapper = JsonMapper.builder().build();
            Map<String, Object> graph = mapper.readValue(definitionJson, new TypeReference<>() {});
            Object nodesObj = graph.get("nodes");
            if (nodesObj instanceof List<?> nodes) {
                for (Object n : nodes) {
                    if (n instanceof Map<?, ?> node) {
                        rewritePersonaIdInNode(node, remap);
                    }
                }
            }
            return mapper.writeValueAsString(graph);
        } catch (Exception e) {
            log.warn("Failed to rewrite personaIds in chat-flow graph; keeping original. ({})",
                    e.getMessage());
            return definitionJson;
        }
    }

    @SuppressWarnings("unchecked")
    private static void rewritePersonaIdInNode(Map<?, ?> node, Map<String, String> remap) {
        Object data = node.get("data");
        if (!(data instanceof Map<?, ?>)) return;
        Map<String, Object> dataMap = (Map<String, Object>) data;
        Object pid = dataMap.get("personaId");
        if (pid instanceof String s) {
            String mapped = remap.get(s);
            if (mapped != null) {
                dataMap.put("personaId", mapped);
            }
        }
    }

    private static String readSiblingFile(File extractFolder, String relativePath) {
        if (extractFolder == null || relativePath == null || relativePath.isBlank()) return null;
        File file = new File(extractFolder, relativePath);
        if (!file.isFile()) {
            log.warn("Sibling file '{}' not found inside extract folder '{}'",
                    relativePath, extractFolder.getAbsolutePath());
            return null;
        }
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to read sibling file '{}': {}", relativePath, e.getMessage());
            return null;
        }
    }

    // ─────────────────── Internal state ───────────────────

    private static final class ResolvedRefs {
        final Map<String, TurPersona> personas = new LinkedHashMap<>();
        final Map<String, TurLLMInstance> llms = new LinkedHashMap<>();
        final Map<String, TurMcpServer> mcpServers = new LinkedHashMap<>();
        final Map<String, TurStoreInstance> stores = new LinkedHashMap<>();
        final Map<String, TurEmbeddingModel> embeddings = new LinkedHashMap<>();
        final Map<String, TurCustomTool> customTools = new LinkedHashMap<>();
        /** original-personaId → newly-assigned-personaId, when name-lookup resolved a different UUID. */
        final Map<String, String> personaIdRemap = new HashMap<>();
    }
}
