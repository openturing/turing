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

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.persistence.model.bento.TurBentoLayout;
import com.viglet.turing.persistence.model.bento.TurBentoLayoutScope;
import com.viglet.turing.persistence.repository.bento.TurBentoLayoutRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * T574 / §XXXI.11 — resolver + persistence seam for customizable Bento list
 * layouts. Emphasis and order are stored per list surface in three layers and
 * resolved with a cascade: <b>per-user override → admin global template →
 * built-in featured=idx0 default</b>. An admin save becomes the template every
 * non-customizer follows; a user override is protective (pins their layout) and
 * clears via {@link #resetUser(String)} to re-inherit the template. T575 is the
 * drag/emphasis UI on top of this seam.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurBentoLayoutService {

    /** Sentinel owner for the shared {@code GLOBAL} template — keeps the unique index portable. */
    private static final String GLOBAL_OWNER = "";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final TypeReference<List<TurBentoLayoutEntry>> ENTRY_LIST = new TypeReference<>() {
    };

    private final TurBentoLayoutRepository repository;
    private final ObjectMapper objectMapper;

    public TurBentoLayoutService(TurBentoLayoutRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Resolve the layout the current user should see for {@code listId}, applying
     * the cascade. A {@link TurBentoLayoutSource#DEFAULT} result carries empty
     * entries — the client applies the built-in featured=idx0 default.
     */
    public TurBentoLayoutResponse resolve(String listId) {
        boolean canEditGlobal = isAdmin();
        String user = currentUsername();

        if (user != null) {
            Optional<List<TurBentoLayoutEntry>> userLayout = readEntries(TurBentoLayoutScope.USER, user, listId);
            if (userLayout.isPresent()) {
                return new TurBentoLayoutResponse(listId, TurBentoLayoutSource.USER, canEditGlobal, userLayout.get());
            }
        }

        Optional<List<TurBentoLayoutEntry>> globalLayout = readEntries(TurBentoLayoutScope.GLOBAL, GLOBAL_OWNER, listId);
        if (globalLayout.isPresent()) {
            return new TurBentoLayoutResponse(listId, TurBentoLayoutSource.GLOBAL, canEditGlobal, globalLayout.get());
        }

        return new TurBentoLayoutResponse(listId, TurBentoLayoutSource.DEFAULT, canEditGlobal, List.of());
    }

    /** Persist (or replace) the current user's protective override for {@code listId}. */
    public TurBentoLayoutResponse saveUser(String listId, List<TurBentoLayoutEntry> entries) {
        String user = requireUser();
        upsert(TurBentoLayoutScope.USER, user, listId, entries);
        return new TurBentoLayoutResponse(listId, TurBentoLayoutSource.USER, isAdmin(), entries);
    }

    /**
     * Persist (or replace) the admin global template for {@code listId}. Admin
     * authorization is enforced at the controller; this method assumes it.
     */
    public TurBentoLayoutResponse saveGlobal(String listId, List<TurBentoLayoutEntry> entries) {
        upsert(TurBentoLayoutScope.GLOBAL, GLOBAL_OWNER, listId, entries);
        return new TurBentoLayoutResponse(listId, TurBentoLayoutSource.GLOBAL, isAdmin(), entries);
    }

    /** Clear the current user's override so they re-inherit the template (or default). */
    @Transactional
    public TurBentoLayoutResponse resetUser(String listId) {
        String user = requireUser();
        repository.deleteByScopeAndOwnerIdAndListId(TurBentoLayoutScope.USER, user, listId);
        return resolve(listId);
    }

    private Optional<List<TurBentoLayoutEntry>> readEntries(TurBentoLayoutScope scope, String ownerId, String listId) {
        return repository.findByScopeAndOwnerIdAndListId(scope, ownerId, listId)
                .map(TurBentoLayout::getLayoutJson)
                .filter(json -> json != null && !json.isBlank())
                .map(json -> objectMapper.readValue(json, ENTRY_LIST))
                .filter(list -> !list.isEmpty());
    }

    private void upsert(TurBentoLayoutScope scope, String ownerId, String listId, List<TurBentoLayoutEntry> entries) {
        long now = System.currentTimeMillis();
        TurBentoLayout layout = repository.findByScopeAndOwnerIdAndListId(scope, ownerId, listId)
                .orElseGet(() -> {
                    TurBentoLayout created = new TurBentoLayout();
                    created.setScope(scope);
                    created.setOwnerId(ownerId);
                    created.setListId(listId);
                    created.setCreatedAt(now);
                    return created;
                });
        layout.setLayoutJson(objectMapper.writeValueAsString(entries == null ? List.of() : entries));
        layout.setUpdatedAt(now);
        repository.save(layout);
    }

    private String requireUser() {
        String user = currentUsername();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user required");
        }
        return user;
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        String name = auth.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return null;
        }
        return name;
    }

    private static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (ROLE_ADMIN.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
