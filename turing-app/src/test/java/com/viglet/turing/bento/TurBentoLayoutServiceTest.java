/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.bento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.viglet.turing.persistence.model.bento.TurBentoEmphasis;
import com.viglet.turing.persistence.model.bento.TurBentoLayout;
import com.viglet.turing.persistence.model.bento.TurBentoLayoutScope;
import com.viglet.turing.persistence.repository.bento.TurBentoLayoutRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * T574 / §XXXI.11 — the resolver cascade (user override → global template →
 * default) and the user override lifecycle (save / reset).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurBentoLayoutServiceTest {

    private static final String LIST = "llm";
    private static final String USER = "user-1";
    private static final String GLOBAL_OWNER = "";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TurBentoLayoutRepository repository;

    private TurBentoLayoutService service() {
        return new TurBentoLayoutService(repository, objectMapper);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(String name, String... roles) {
        var authorities = List.of(roles).stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(name, "creds", authorities));
    }

    private TurBentoLayout layout(TurBentoLayoutScope scope, String owner, List<TurBentoLayoutEntry> entries) {
        TurBentoLayout row = new TurBentoLayout();
        row.setScope(scope);
        row.setOwnerId(owner);
        row.setListId(LIST);
        row.setLayoutJson(objectMapper.writeValueAsString(entries));
        return row;
    }

    @Test
    void resolvePrefersUserOverrideWhenPresent() {
        authenticate(USER, "ROLE_USER");
        var userEntries = List.of(new TurBentoLayoutEntry("a", TurBentoEmphasis.LARGE, 0));
        when(repository.findByScopeAndOwnerIdAndListId(TurBentoLayoutScope.USER, USER, LIST))
                .thenReturn(Optional.of(layout(TurBentoLayoutScope.USER, USER, userEntries)));

        TurBentoLayoutResponse response = service().resolve(LIST);

        assertThat(response.source()).isEqualTo(TurBentoLayoutSource.USER);
        assertThat(response.entries()).containsExactlyElementsOf(userEntries);
        assertThat(response.canEditGlobal()).isFalse();
    }

    @Test
    void resolveFallsBackToGlobalTemplateWhenNoUserOverride() {
        authenticate(USER, "ROLE_USER");
        var globalEntries = List.of(new TurBentoLayoutEntry("b", TurBentoEmphasis.MEDIUM, 1));
        when(repository.findByScopeAndOwnerIdAndListId(TurBentoLayoutScope.USER, USER, LIST))
                .thenReturn(Optional.empty());
        when(repository.findByScopeAndOwnerIdAndListId(TurBentoLayoutScope.GLOBAL, GLOBAL_OWNER, LIST))
                .thenReturn(Optional.of(layout(TurBentoLayoutScope.GLOBAL, GLOBAL_OWNER, globalEntries)));

        TurBentoLayoutResponse response = service().resolve(LIST);

        assertThat(response.source()).isEqualTo(TurBentoLayoutSource.GLOBAL);
        assertThat(response.entries()).containsExactlyElementsOf(globalEntries);
    }

    @Test
    void resolveReturnsDefaultWithEmptyEntriesWhenNothingPersisted() {
        authenticate(USER, "ROLE_USER");
        when(repository.findByScopeAndOwnerIdAndListId(TurBentoLayoutScope.USER, USER, LIST))
                .thenReturn(Optional.empty());
        when(repository.findByScopeAndOwnerIdAndListId(TurBentoLayoutScope.GLOBAL, GLOBAL_OWNER, LIST))
                .thenReturn(Optional.empty());

        TurBentoLayoutResponse response = service().resolve(LIST);

        assertThat(response.source()).isEqualTo(TurBentoLayoutSource.DEFAULT);
        assertThat(response.entries()).isEmpty();
    }

    @Test
    void resolveSkipsBlankUserLayoutAndFallsThrough() {
        authenticate(USER, "ROLE_USER");
        TurBentoLayout blank = layout(TurBentoLayoutScope.USER, USER, List.of());
        blank.setLayoutJson(""); // empty array -> filtered out, cascade continues
        when(repository.findByScopeAndOwnerIdAndListId(TurBentoLayoutScope.USER, USER, LIST))
                .thenReturn(Optional.of(blank));
        when(repository.findByScopeAndOwnerIdAndListId(TurBentoLayoutScope.GLOBAL, GLOBAL_OWNER, LIST))
                .thenReturn(Optional.empty());

        assertThat(service().resolve(LIST).source()).isEqualTo(TurBentoLayoutSource.DEFAULT);
    }

    @Test
    void adminAuthoritySurfacesCanEditGlobal() {
        authenticate("admin", "ROLE_ADMIN");
        when(repository.findByScopeAndOwnerIdAndListId(TurBentoLayoutScope.USER, "admin", LIST))
                .thenReturn(Optional.empty());
        when(repository.findByScopeAndOwnerIdAndListId(TurBentoLayoutScope.GLOBAL, GLOBAL_OWNER, LIST))
                .thenReturn(Optional.empty());

        assertThat(service().resolve(LIST).canEditGlobal()).isTrue();
    }

    @Test
    void saveUserPersistsNewOverrideWithSerializedEntries() {
        authenticate(USER, "ROLE_USER");
        var entries = List.of(new TurBentoLayoutEntry("a", TurBentoEmphasis.LARGE, 0),
                new TurBentoLayoutEntry("b", TurBentoEmphasis.SMALL, 1));
        when(repository.findByScopeAndOwnerIdAndListId(TurBentoLayoutScope.USER, USER, LIST))
                .thenReturn(Optional.empty());

        TurBentoLayoutResponse response = service().saveUser(LIST, entries);

        assertThat(response.source()).isEqualTo(TurBentoLayoutSource.USER);
        ArgumentCaptor<TurBentoLayout> saved = ArgumentCaptor.forClass(TurBentoLayout.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getScope()).isEqualTo(TurBentoLayoutScope.USER);
        assertThat(saved.getValue().getOwnerId()).isEqualTo(USER);
        assertThat(saved.getValue().getListId()).isEqualTo(LIST);
        assertThat(saved.getValue().getLayoutJson()).contains("\"itemId\":\"a\"").contains("\"emphasis\":\"LARGE\"");
    }

    @Test
    void saveGlobalPersistsUnderSharedGlobalOwner() {
        authenticate("admin", "ROLE_ADMIN");
        var entries = List.of(new TurBentoLayoutEntry("a", TurBentoEmphasis.LARGE, 0));
        when(repository.findByScopeAndOwnerIdAndListId(TurBentoLayoutScope.GLOBAL, GLOBAL_OWNER, LIST))
                .thenReturn(Optional.empty());

        service().saveGlobal(LIST, entries);

        ArgumentCaptor<TurBentoLayout> saved = ArgumentCaptor.forClass(TurBentoLayout.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getScope()).isEqualTo(TurBentoLayoutScope.GLOBAL);
        assertThat(saved.getValue().getOwnerId()).isEqualTo(GLOBAL_OWNER);
    }

    @Test
    void resetUserDeletesOverrideThenReresolves() {
        authenticate(USER, "ROLE_USER");
        when(repository.findByScopeAndOwnerIdAndListId(TurBentoLayoutScope.USER, USER, LIST))
                .thenReturn(Optional.empty());
        when(repository.findByScopeAndOwnerIdAndListId(TurBentoLayoutScope.GLOBAL, GLOBAL_OWNER, LIST))
                .thenReturn(Optional.empty());

        TurBentoLayoutResponse response = service().resetUser(LIST);

        verify(repository).deleteByScopeAndOwnerIdAndListId(eq(TurBentoLayoutScope.USER), eq(USER), eq(LIST));
        assertThat(response.source()).isEqualTo(TurBentoLayoutSource.DEFAULT);
    }
}
