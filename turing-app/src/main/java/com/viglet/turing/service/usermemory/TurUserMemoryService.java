/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.usermemory;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.agent.TurUserMemory;
import com.viglet.turing.persistence.repository.agent.TurUserMemoryRepository;

/**
 * T446 / §XXIII.5 — cross-conversation personal memory: the assistant's durable
 * facts about an end user, surviving across sessions, tenant-scoped.
 *
 * <p>Writes upsert by {@code (userId, agentId, memoryKey)} so re-remembering the
 * same category overwrites rather than duplicates. Reads/deletes power both the
 * agent's {@code recall_user_memory} client tool and the user-facing "what the
 * assistant remembers about me" panel (GDPR delete).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurUserMemoryService {

    private final TurUserMemoryRepository repository;

    public TurUserMemoryService(TurUserMemoryRepository repository) {
        this.repository = repository;
    }

    /** Insert or update one fact for a user+agent, keyed by {@code memoryKey}. */
    public TurUserMemory remember(String userId, String agentId, String memoryKey, String content) {
        long now = System.currentTimeMillis();
        TurUserMemory memory = repository
                .findByUserIdAndAgentIdAndMemoryKey(userId, agentId, memoryKey)
                .orElseGet(() -> {
                    TurUserMemory fresh = new TurUserMemory();
                    fresh.setUserId(userId);
                    fresh.setAgentId(agentId);
                    fresh.setMemoryKey(memoryKey);
                    fresh.setCreatedAt(now);
                    return fresh;
                });
        memory.setContent(content);
        memory.setUpdatedAt(now);
        return repository.save(memory);
    }

    /** Every remembered fact for a user+agent, most-recently-updated first. */
    public List<TurUserMemory> list(String userId, String agentId) {
        return repository.findByUserIdAndAgentIdOrderByUpdatedAtDesc(userId, agentId);
    }

    /**
     * T651 / §XXXVII.13 — delete one fact by id ONLY when it belongs to the given
     * {@code userId} + {@code agentId} (the resolved caller). Prevents the IDOR
     * where any caller could delete any memory row by guessing its id. Returns
     * {@code true} only when a row was actually removed. {@code @TenantId} still
     * scopes the read to the current tenant on top of this ownership check.
     */
    public boolean delete(String id, String userId, String agentId) {
        return repository.findById(id)
                .filter(m -> userId != null && userId.equals(m.getUserId())
                        && agentId != null && agentId.equals(m.getAgentId()))
                .map(m -> {
                    repository.delete(m);
                    return true;
                })
                .orElse(false);
    }

    /** Delete every fact for a user+agent (GDPR "forget me"). */
    @Transactional
    public long clear(String userId, String agentId) {
        return repository.deleteByUserIdAndAgentId(userId, agentId);
    }

    /**
     * The remembered facts rendered for the agent's {@code recall_user_memory}
     * tool result — a compact bullet list, or a clear "nothing yet" sentinel.
     */
    public String recall(String userId, String agentId) {
        List<TurUserMemory> facts = list(userId, agentId);
        if (facts.isEmpty()) {
            return "Nothing remembered about this user yet.";
        }
        StringBuilder sb = new StringBuilder("What you remember about this user:\n");
        for (TurUserMemory fact : facts) {
            sb.append("- ").append(fact.getMemoryKey()).append(": ")
                    .append(fact.getContent()).append("\n");
        }
        return sb.toString().trim();
    }
}
