/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.anthropic.memory;

import java.util.Locale;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurMemoryScope;

import lombok.extern.slf4j.Slf4j;

/**
 * T166 / §X.9.d — resolves the workspace scope id the Anthropic memory tool
 * (T163) should read/write under, given the agent's {@link TurMemoryScope}.
 *
 * <p>{@code CONVERSATION} (default) returns the live conversation id verbatim, so
 * the memory files live and die with that conversation. {@code USER} returns a
 * stable, user-derived id ({@code usermem-<sanitized Keycloak sub>}) so the same
 * memory area is shared across every conversation that authenticated user has
 * with the agent — the agent remembers stable preferences across sessions.
 *
 * <p>The {@code TurAgentWorkspace} keys everything by {@code (agentId,
 * conversationId, key)}; using a synthetic user-derived value in the
 * conversation-id slot is all that's needed to repoint the store — no workspace
 * change. When no stable identity is available (anonymous visitor turn), it falls
 * back to the conversation id so user memory is never written under an
 * unattributable scope.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurAgentMemoryScopeResolver {

    /** Prefix for the synthetic user-scoped id; kept distinct from real conversation ids. */
    static final String USER_SCOPE_PREFIX = "usermem-";

    /**
     * Resolve the effective workspace scope id from the current security context.
     */
    public String resolveScopeId(TurAIAgent agent, String conversationId) {
        return resolveScopeId(agent, conversationId, SecurityContextHolder.getContext() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication());
    }

    /**
     * Test seam — resolve against an explicit {@link Authentication} rather than
     * the thread-bound security context.
     */
    String resolveScopeId(TurAIAgent agent, String conversationId, Authentication authentication) {
        if (agent == null || agent.getMemoryScope() != TurMemoryScope.USER) {
            return conversationId;
        }
        String userId = stableUserId(authentication);
        if (userId == null) {
            log.debug("[Native][Anthropic-Memory] agent '{}' uses USER memory scope but no stable "
                    + "user identity is available — falling back to per-conversation memory",
                    agent.getTitle());
            return conversationId;
        }
        return USER_SCOPE_PREFIX + sanitize(userId);
    }

    /** The authenticated user's stable id (Keycloak {@code sub}), or null when anonymous. */
    private static String stableUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        String name = authentication.getName();
        return StringUtils.hasText(name) ? name : null;
    }

    /** Reduce an id to a safe, bounded path segment ({@code [A-Za-z0-9_-]}). */
    static String sanitize(String value) {
        String cleaned = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }
}
