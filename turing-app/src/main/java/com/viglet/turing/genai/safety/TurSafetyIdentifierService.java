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
package com.viglet.turing.genai.safety;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.tenant.TurTenantContext;

import lombok.extern.slf4j.Slf4j;

/**
 * T181 / §X.14.a — resolves the stable, privacy-preserving end-user identifier
 * that the native OpenAI Responses path sends as {@code safety_identifier} on
 * every call. OpenAI uses it to detect and rate-limit per-user abuse on its
 * edge, so abusive traffic from one tenant's user is contained without Turing
 * shipping any raw identity off-box.
 *
 * <p>The value is {@code SHA-256(sub | tenantId | salt)} rendered as lowercase
 * hex — the Keycloak subject is never sent in clear, the tenant id keeps the
 * same human distinct across tenants (and the same tenant's user stable across
 * sessions), and the optional configured {@code salt} makes the hash opaque to
 * anyone who doesn't hold it. Fully fail-open and opt-out:
 *
 * <ul>
 *   <li>{@code turing.safety.identifier.enabled=false} → never emit anything
 *       (byte-for-byte unchanged request);</li>
 *   <li>anonymous / unauthenticated turn (no stable {@code sub}) → emit
 *       nothing rather than a constant hash that would lump every visitor into
 *       one safety bucket;</li>
 *   <li>any hashing error → {@link Optional#empty()} (the call still goes out,
 *       just untagged).</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurSafetyIdentifierService {

    private final TurTenantContext tenantContext;
    private final boolean enabled;
    private final String salt;

    public TurSafetyIdentifierService(TurTenantContext tenantContext,
            @Value("${turing.safety.identifier.enabled:true}") boolean enabled,
            @Value("${turing.safety.identifier.salt:}") String salt) {
        this.tenantContext = tenantContext;
        this.enabled = enabled;
        this.salt = salt == null ? "" : salt;
    }

    /**
     * Resolve the {@code safety_identifier} for the current request thread.
     * <strong>Must be called on the request thread</strong> (e.g. inside the
     * native executor's synchronous {@code tryExecute}) — the reactive worker
     * thread that later issues the provider call has no bound security context.
     */
    public Optional<String> currentSafetyIdentifier() {
        if (!enabled) {
            return Optional.empty();
        }
        Authentication authentication = SecurityContextHolder.getContext() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication();
        return safetyIdentifierFor(authentication);
    }

    /**
     * Test seam — resolve against an explicit {@link Authentication} rather than
     * the thread-bound security context.
     */
    Optional<String> safetyIdentifierFor(Authentication authentication) {
        if (!enabled) {
            return Optional.empty();
        }
        String sub = stableSubject(authentication);
        if (sub == null) {
            return Optional.empty();
        }
        return hash(sub);
    }

    /** The authenticated user's stable id (Keycloak {@code sub}), or null when anonymous. */
    private static String stableSubject(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        String name = authentication.getName();
        return StringUtils.hasText(name) ? name : null;
    }

    private Optional<String> hash(String sub) {
        try {
            String tenantId = tenantContext.resolveCurrentTenant();
            String material = sub + "|" + (tenantId == null ? "" : tenantId) + "|" + salt;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            return Optional.of(HexFormat.of().formatHex(hashed));
        } catch (NoSuchAlgorithmException | RuntimeException e) {
            log.debug("[Safety] could not derive safety_identifier: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
