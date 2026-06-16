/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.tenant;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import com.viglet.core.tenancy.VigletTenantPurger;
import com.viglet.turing.service.storage.TurStorageService;

import lombok.extern.slf4j.Slf4j;

/**
 * T396 / §XIV.9 — the {@link VigletTenantPurger} SPI wiping Turing's physical
 * tenant footprint (the bytes outside the registry rows) when the shared
 * {@code VigletTenantTeardownService} tears a tenant down. The shared service
 * invokes this inside {@code tenantContext.runAs(tenantId, …)} so the storage
 * scoping decorator targets the tenant's prefix.
 *
 * <p>Covered surfaces (in-process): object storage prefix ({@code tenants/<id>/}),
 * the per-tenant Lucene directory, and caches. Best-effort throughout — a failure
 * to remove some bytes must not abort the registry deletion. The external Solr/ES
 * core drop + Keycloak attribute deletion still require the live engines and are
 * layered on in deployment-specific runbooks.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurVigletTenantPurger implements VigletTenantPurger {

    private static final String LUCENE_BASE = "./store/lucene-vector";

    private final TurStorageService storageService;
    private final CacheManager cacheManager;

    public TurVigletTenantPurger(TurStorageService storageService, CacheManager cacheManager) {
        this.storageService = storageService;
        this.cacheManager = cacheManager;
    }

    @Override
    public void purge(String tenantId, boolean dryRun) {
        if (dryRun) {
            return; // the shared service computes the plan without invoking us mutatively
        }
        purgeStorage(tenantId);
        purgeLucene(tenantId);
        evictCaches();
    }

    private void purgeStorage(String tenantId) {
        if (!storageService.isEnabled()) {
            return;
        }
        // Already running in the tenant's context (the shared teardown wraps us in
        // runAs), so the scoping decorator targets this tenant's prefix.
        try {
            storageService.deleteObjectsWithPrefix("");
        } catch (RuntimeException e) {
            log.warn("[TenantTeardown] storage purge failed for '{}': {}", tenantId, e.getMessage());
        }
    }

    private void purgeLucene(String tenantId) {
        Path dir = Path.of(LUCENE_BASE, tenantId);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // best-effort
                }
            });
        } catch (Exception e) {
            log.warn("[TenantTeardown] Lucene purge failed for '{}': {}", tenantId, e.getMessage());
        }
    }

    private void evictCaches() {
        // Coarse: clear all caches (tenant-keyed entries can't be enumerated per
        // tenant). Teardown is rare, so a full cold-cache is an acceptable cost.
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
    }
}
