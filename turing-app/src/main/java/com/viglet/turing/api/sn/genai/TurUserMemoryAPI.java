/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.sn.genai;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurUserMemory;
import com.viglet.turing.sn.TurSNSearchProcess;
import com.viglet.turing.service.usermemory.TurUserMemoryService;

import lombok.extern.slf4j.Slf4j;

/**
 * T446 / §XXIII.5 — visitor-facing endpoints for cross-conversation personal
 * memory. SN-site scoped and unauthenticated (like the slot/click endpoints): the
 * embedded SDK supplies the stable {@code userId}; the agent is resolved from the
 * site's GenAI config so the host never has to know the agent id.
 *
 * <p>Powers both the agent's {@code remember_fact}/{@code recall_user_memory}
 * client-tool handlers and the user-facing "what the assistant remembers about me"
 * panel (list + delete = GDPR posture as a feature). Tenant isolation is enforced
 * by Hibernate's {@code @TenantId} filter on {@link TurUserMemory}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@RestController
@RequestMapping("/api/sn/{siteName}/user-memory")
public class TurUserMemoryAPI {

    private final TurUserMemoryService userMemoryService;
    private final TurSNSearchProcess turSNSearchProcess;

    public TurUserMemoryAPI(TurUserMemoryService userMemoryService,
            TurSNSearchProcess turSNSearchProcess) {
        this.userMemoryService = userMemoryService;
        this.turSNSearchProcess = turSNSearchProcess;
    }

    /** A remembered fact projected for the host (no tenant/agent internals). */
    public record TurUserMemoryDto(String id, String userId, String key, String content,
            long updatedAt) {
        static TurUserMemoryDto of(TurUserMemory m) {
            return new TurUserMemoryDto(m.getId(), m.getUserId(), m.getMemoryKey(), m.getContent(),
                    m.getUpdatedAt());
        }
    }

    public record RememberRequest(String userId, String key, String value) {
    }

    @GetMapping
    public List<TurUserMemoryDto> list(@PathVariable String siteName,
            @RequestParam String userId) {
        String agentId = resolveAgentId(siteName);
        if (agentId == null || userId == null || userId.isBlank()) {
            return List.of();
        }
        return userMemoryService.list(userId, agentId).stream().map(TurUserMemoryDto::of).toList();
    }

    @PostMapping
    public TurUserMemoryDto remember(@PathVariable String siteName,
            @RequestBody RememberRequest request) {
        String agentId = resolveAgentId(siteName);
        if (agentId == null || request == null || isBlank(request.userId())
                || isBlank(request.key())) {
            return null;
        }
        return TurUserMemoryDto.of(userMemoryService.remember(
                request.userId(), agentId, request.key().trim(), request.value()));
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable String siteName, @PathVariable String id,
            @RequestParam String userId) {
        // T651 / §XXXVII.13 — scope the delete to the caller's own (userId, agent)
        // so a row cannot be deleted by guessing its id (IDOR). Returns false when
        // the row is absent or not owned by this userId+agent.
        String agentId = resolveAgentId(siteName);
        if (agentId == null || isBlank(userId)) {
            return false;
        }
        return userMemoryService.delete(id, userId, agentId);
    }

    @DeleteMapping
    public long clear(@PathVariable String siteName, @RequestParam String userId) {
        String agentId = resolveAgentId(siteName);
        if (agentId == null || isBlank(userId)) {
            return 0;
        }
        return userMemoryService.clear(userId, agentId);
    }

    private String resolveAgentId(String siteName) {
        TurAIAgent agent = turSNSearchProcess.getSNSite(siteName)
                .map(site -> site.getTurSNSiteGenAi())
                .map(genAi -> genAi == null ? null : genAi.getTurAIAgent())
                .orElse(null);
        return agent == null ? null : agent.getId();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
