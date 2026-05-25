/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.chatanalytics;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.chatanalytics.TurAnalyticsIntent;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.chatanalytics.TurAnalyticsIntentRepository;
import com.viglet.turing.service.chatanalytics.TurAnalyticsIntentIndexer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * T28 / §III.5 — agent-scoped CRUD for the analytics intent catalog
 * consumed by the chat enricher's non-LLM classifier strategies. Each
 * row carries a label + a newline-separated bag of representative user
 * utterances; see {@link TurAnalyticsIntent} for the data shape.
 *
 * <h2>Endpoints</h2>
 *
 * <ul>
 *   <li>{@code GET    /api/ai-agent/{agentId}/analytics-intent} — list
 *       all rows for the agent (enabled + disabled), sorted by label.</li>
 *   <li>{@code GET    /api/ai-agent/{agentId}/analytics-intent/{id}}</li>
 *   <li>{@code POST   /api/ai-agent/{agentId}/analytics-intent} — create.</li>
 *   <li>{@code PUT    /api/ai-agent/{agentId}/analytics-intent/{id}} — update.</li>
 *   <li>{@code DELETE /api/ai-agent/{agentId}/analytics-intent/{id}}</li>
 * </ul>
 *
 * <p>Write endpoints rely on the repository's {@code @CacheEvict}
 * chain + the JPA {@code @EntityListeners} on {@link TurAnalyticsIntent}
 * to wipe both the Spring catalog cache and the Lucene MLT index cache
 * — admins see edits reflected on the next classifier cycle without a
 * restart, same hook pattern T27 uses for the chat-flow router cache.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@RestController
@RequestMapping("/api/ai-agent/{agentId}/analytics-intent")
@Tag(name = "Analytics Intent",
        description = "T28 — per-agent intent catalog for the chat analytics classifier")
@RequiredArgsConstructor
public class TurAnalyticsIntentAPI {

    private final TurAnalyticsIntentRepository intentRepository;
    private final TurAIAgentRepository agentRepository;
    private final TurAnalyticsIntentIndexer indexer;

    @GetMapping
    @Operation(summary = "List analytics intents for an agent")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_READ" })
    public List<TurAnalyticsIntent> list(@PathVariable String agentId) {
        return intentRepository.findByTurAIAgent_IdOrderByLabelAsc(agentId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one analytics intent")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_READ" })
    public ResponseEntity<TurAnalyticsIntent> get(@PathVariable String agentId,
            @PathVariable String id) {
        return intentRepository.findById(id)
                .filter(intent -> matchesAgent(intent, agentId))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping
    @Transactional
    @Operation(summary = "Create a new analytics intent")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_WRITE" })
    public ResponseEntity<TurAnalyticsIntent> create(@PathVariable String agentId,
            @RequestBody TurAnalyticsIntent payload) {
        TurAIAgent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        payload.setId(null);
        payload.setTurAIAgent(agent);
        TurAnalyticsIntent saved = intentRepository.save(payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    @Operation(summary = "Update an analytics intent")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_WRITE" })
    public ResponseEntity<TurAnalyticsIntent> update(@PathVariable String agentId,
            @PathVariable String id,
            @RequestBody TurAnalyticsIntent payload) {
        return intentRepository.findById(id)
                .filter(intent -> matchesAgent(intent, agentId))
                .map(existing -> {
                    existing.setLabel(payload.getLabel());
                    existing.setSamples(payload.getSamples());
                    existing.setDescription(payload.getDescription());
                    existing.setEnabled(payload.getEnabled());
                    return ResponseEntity.ok(intentRepository.save(existing));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    @Operation(summary = "Delete an analytics intent")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_WRITE" })
    public ResponseEntity<Void> delete(@PathVariable String agentId, @PathVariable String id) {
        return intentRepository.findById(id)
                .filter(intent -> matchesAgent(intent, agentId))
                .map(existing -> {
                    intentRepository.delete(existing);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * T28 Phase B bootstrap path — wipes the agent's slice in the SE
     * standalone index and re-pushes every enabled catalog row.
     * Required after the SE binding goes live on a catalog that
     * existed before, or after a Solr/ES schema change the incremental
     * JPA hook can't recover from. Returns the number of rows the SE
     * accepted; {@code 0} when no SE is bound at all.
     */
    @PostMapping("/reindex")
    @Operation(summary = "Reindex the agent's analytics intent catalog into the configured SE")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_WRITE" })
    public ResponseEntity<Map<String, Object>> reindex(@PathVariable String agentId) {
        if (agentRepository.findById(agentId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        int indexed = indexer.reindexAgent(agentId);
        return ResponseEntity.ok(Map.of(
                "agentId", agentId,
                "indexed", indexed,
                "indexName", TurAnalyticsIntentIndexer.indexNameFor(agentId)));
    }

    private static boolean matchesAgent(TurAnalyticsIntent intent, String agentId) {
        return intent.getTurAIAgent() != null
                && agentId.equals(intent.getTurAIAgent().getId());
    }
}
