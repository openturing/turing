/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.anthropic.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurMemoryScope;

class TurAgentMemoryScopeResolverTest {

    private final TurAgentMemoryScopeResolver resolver = new TurAgentMemoryScopeResolver();

    private static TurAIAgent agent(TurMemoryScope scope) {
        TurAIAgent agent = new TurAIAgent();
        agent.setTitle("Agent");
        agent.setMemoryScope(scope);
        return agent;
    }

    private static Authentication authenticated(String name) {
        return new UsernamePasswordAuthenticationToken(name, "creds",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void conversationScopeReturnsConversationIdVerbatim() {
        // Even with an authenticated user, conversation scope ignores identity.
        assertThat(resolver.resolveScopeId(agent(TurMemoryScope.CONVERSATION), "conv-1",
                authenticated("sub-abc"))).isEqualTo("conv-1");
    }

    @Test
    void userScopeDerivesStableIdFromAuthenticatedSubject() {
        String scopeId = resolver.resolveScopeId(agent(TurMemoryScope.USER), "conv-1",
                authenticated("Sub-ABC-123"));
        assertThat(scopeId).isEqualTo("usermem-sub-abc-123");
        // Stable across conversations — same user, different conversation id.
        assertThat(resolver.resolveScopeId(agent(TurMemoryScope.USER), "conv-2",
                authenticated("Sub-ABC-123"))).isEqualTo(scopeId);
    }

    @Test
    void userScopeFallsBackToConversationWhenAnonymous() {
        Authentication anon = new AnonymousAuthenticationToken("key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        assertThat(resolver.resolveScopeId(agent(TurMemoryScope.USER), "conv-1", anon))
                .isEqualTo("conv-1");
    }

    @Test
    void userScopeFallsBackToConversationWhenNoAuthentication() {
        assertThat(resolver.resolveScopeId(agent(TurMemoryScope.USER), "conv-1", null))
                .isEqualTo("conv-1");
    }

    @Test
    void sanitizeReducesToSafeSegment() {
        assertThat(TurAgentMemoryScopeResolver.sanitize("auth0|User@Example.com"))
                .isEqualTo("auth0_user_example_com");
    }
}
