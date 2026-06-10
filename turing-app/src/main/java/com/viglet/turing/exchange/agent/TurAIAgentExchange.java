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

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAIAgentSlot;
import com.viglet.turing.persistence.model.agent.TurAgentEvalSet;
import com.viglet.turing.persistence.model.intent.TurIntent;

import lombok.Getter;
import lombok.Setter;

/**
 * Export wrapper for {@link TurAIAgent}. Children that are exclusively owned
 * by one agent (slots, intents, chat-flow metadata) are inlined. References
 * to shared entities (personas, LLM instances, MCP servers, custom tools,
 * embedding model, store) are carried as ID-only pointers — the full entities
 * live at the root of the envelope so the same instance can be deduped across
 * multiple agents in the same export.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.8
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TurAIAgentExchange {

    // ─── core agent fields ────────────────────────────────────────────
    private String id;
    private String title;
    private String description;
    private String icon;
    private String systemPrompt;
    private String systemPromptMetaPrompt;
    private int enabled;
    private boolean ragEnabled;
    private boolean chatMemoryEnabled;
    private int chatMemoryFlushIntervalMinutes;
    private int chatMemoryMaxMessages;
    // T30 / §IV.4 — conversation-relevance retrieval (since 2026.3.1)
    private boolean chatMemoryRelevanceEnabled;
    private int chatMemoryRelevanceTopK;
    private int chatMemoryRecentN;
    // T115 / §IX.3.e — workspace-backed memory compression (since 2026.3.1)
    private boolean chatMemoryCompressionEnabled;
    private int chatMemoryCompressionThresholdTokens;
    private String chatMemoryCompressionLlmId;
    private String chatMemoryCompressionInterval;
    // T309 / §IX.3.f — run compression off the hot path (default true).
    private Boolean chatMemoryCompressionAsync;
    private String nativeTools;
    private String pythonRequirements;
    // T66 / §VII.6.g — submission retention policy travels with the agent.
    private com.viglet.turing.persistence.model.agent.TurSubmissionRetention submissionRetention;
    private Integer submissionRetentionDays;

    // ─── reference IDs (entities live at envelope root) ───────────────
    private List<String> personaIds = new ArrayList<>();
    private String defaultPersonaId;
    private List<String> llmInstanceIds = new ArrayList<>();
    private List<String> mcpServerIds = new ArrayList<>();
    private List<String> customToolIds = new ArrayList<>();
    private String embeddingModelInstanceId;
    private String storeInstanceId;

    // ─── owned children (cascade-saved on import) ─────────────────────
    private List<TurAIAgentSlot> slots = new ArrayList<>();
    private List<TurIntent> intents = new ArrayList<>();
    private List<TurChatFlowExchange> chatFlows = new ArrayList<>();
    // T285 / §XV.1 — golden eval sets (with their cases) travel with the agent.
    private List<TurAgentEvalSet> evalSets = new ArrayList<>();

    /**
     * Copies the agent's scalar fields and reference IDs. The caller is
     * responsible for populating {@link #slots}, {@link #intents} and
     * {@link #chatFlows} from the corresponding repositories — those aren't
     * back-mapped on the {@code TurAIAgent} entity.
     */
    public static TurAIAgentExchange fromEntity(TurAIAgent agent) {
        TurAIAgentExchange e = new TurAIAgentExchange();
        e.id = agent.getId();
        e.title = agent.getTitle();
        e.description = agent.getDescription();
        e.icon = agent.getIcon();
        e.systemPrompt = agent.getSystemPrompt();
        e.systemPromptMetaPrompt = agent.getSystemPromptMetaPrompt();
        e.enabled = agent.getEnabled();
        e.ragEnabled = agent.isRagEnabled();
        e.chatMemoryEnabled = agent.isChatMemoryEnabled();
        e.chatMemoryFlushIntervalMinutes = agent.getChatMemoryFlushIntervalMinutes();
        e.chatMemoryMaxMessages = agent.getChatMemoryMaxMessages();
        e.chatMemoryRelevanceEnabled = agent.isChatMemoryRelevanceEnabled();
        e.chatMemoryRelevanceTopK = agent.getChatMemoryRelevanceTopK();
        e.chatMemoryRecentN = agent.getChatMemoryRecentN();
        e.chatMemoryCompressionEnabled = agent.isChatMemoryCompressionEnabled();
        e.chatMemoryCompressionThresholdTokens = agent.getChatMemoryCompressionThresholdTokens();
        e.chatMemoryCompressionLlmId = agent.getChatMemoryCompressionLlmId();
        e.chatMemoryCompressionInterval = agent.getChatMemoryCompressionInterval();
        e.chatMemoryCompressionAsync = agent.isChatMemoryCompressionAsync();
        e.nativeTools = agent.getNativeTools();
        e.pythonRequirements = agent.getPythonRequirements();
        e.submissionRetention = agent.getSubmissionRetention();
        e.submissionRetentionDays = agent.getSubmissionRetentionDays();

        if (agent.getPersonas() != null) {
            agent.getPersonas().forEach(p -> e.personaIds.add(p.getId()));
        }
        if (agent.getDefaultPersona() != null) {
            e.defaultPersonaId = agent.getDefaultPersona().getId();
        }
        if (agent.getLlmInstances() != null) {
            agent.getLlmInstances().forEach(l -> e.llmInstanceIds.add(l.getId()));
        }
        if (agent.getMcpServers() != null) {
            agent.getMcpServers().forEach(m -> e.mcpServerIds.add(m.getId()));
        }
        if (agent.getCustomTools() != null) {
            agent.getCustomTools().forEach(t -> e.customToolIds.add(t.getId()));
        }
        if (agent.getTurEmbeddingModelInstance() != null) {
            e.embeddingModelInstanceId = agent.getTurEmbeddingModelInstance().getId();
        }
        if (agent.getTurStoreInstance() != null) {
            e.storeInstanceId = agent.getTurStoreInstance().getId();
        }
        return e;
    }
}
