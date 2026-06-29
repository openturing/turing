/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.handoff;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;

/**
 * T448 / §XXIII.7 — resolves a router agent's {@code delegate_to_agent} tool from
 * its specialist allowlist ({@code specialistAgentIds}). Mirrors the
 * {@code TurSkillRunnerService} delegation seam: gated by a per-agent flag, builds
 * a single {@link TurDelegateAgentToolCallback} the chat tool resolver merges into
 * the router's tool set (so the T436 tool-call events surface the handoff).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurAgentHandoffService {

    private final TurAgentChatExecutor executor;
    private final TurAIAgentRepository agentRepository;
    private final TurLLMInstanceRepository llmRepository;

    // @Lazy breaks the constructor cycle TurAgentChatExecutor → TurChatToolResolver
    // → TurAgentHandoffService → TurAgentChatExecutor (introduced with T448). The
    // executor is only needed at delegation time, so a lazy proxy is correct and
    // lets the full application context start (otherwise Spring Boot 4 fails fast
    // on the circular reference). Surfaced by TurShowcaseSmokeIT (T457).
    public TurAgentHandoffService(@Lazy TurAgentChatExecutor executor,
            TurAIAgentRepository agentRepository, TurLLMInstanceRepository llmRepository) {
        this.executor = executor;
        this.agentRepository = agentRepository;
        this.llmRepository = llmRepository;
    }

    /** True when the agent is a router with at least one resolvable specialist. */
    public boolean isEnabled(TurAIAgent agent) {
        return agent != null && agent.isAgentHandoffEnabled()
                && !resolveAllowed(agent).isEmpty();
    }

    /** The {@code delegate_to_agent} callback (empty when not a router). */
    public ToolCallback[] buildToolCallbacks(TurAIAgent agent) {
        Map<String, String> allowed = resolveAllowed(agent);
        if (!agent.isAgentHandoffEnabled() || allowed.isEmpty()) {
            return new ToolCallback[0];
        }
        return new ToolCallback[] {
                new TurDelegateAgentToolCallback(allowed, executor, agentRepository, llmRepository)
        };
    }

    /**
     * Lowercased id AND title → canonical id, for every listed specialist that
     * exists, is enabled, and is not the router itself (no self-handoff).
     */
    private Map<String, String> resolveAllowed(TurAIAgent router) {
        Map<String, String> allowed = new LinkedHashMap<>();
        String csv = router == null ? null : router.getSpecialistAgentIds();
        if (csv == null || csv.isBlank()) {
            return allowed;
        }
        for (String raw : csv.split(",")) {
            String id = raw.trim();
            if (id.isEmpty() || id.equals(router.getId())) {
                continue;
            }
            agentRepository.findById(id)
                    .filter(a -> a.getEnabled() == 1)
                    .ifPresent(specialist -> {
                        allowed.put(specialist.getId().toLowerCase(), specialist.getId());
                        if (specialist.getTitle() != null && !specialist.getTitle().isBlank()) {
                            allowed.putIfAbsent(specialist.getTitle().trim().toLowerCase(),
                                    specialist.getId());
                        }
                    });
        }
        return allowed;
    }
}
