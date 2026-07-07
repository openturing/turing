/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.rag.TurRagBm25CoreProvisioner;
import com.viglet.turing.persistence.model.rag.TurRagBm25Core;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.rag.TurRagBm25CoreRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.genai.TurRagContextBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * T24b / §III.2 — admin API for the per-locale BM25 cores backing
 * production hybrid RAG retrieval.
 *
 * <p>Endpoints are SN-site-scoped because the locale set comes from
 * {@link TurSNSiteLocale}: there's no meaningful "list cores for a store"
 * surface in the admin UI — admins reason about retrieval per site, since
 * that's where they bind an SE instance and toggle {@code ragBm25Source}.
 *
 * <h2>Lifecycle covered</h2>
 *
 * <ul>
 *   <li>{@code GET /api/sn/{siteId}/rag/cores} — status table for the
 *       form's "Production hybrid (Search Engine)" section. One row per
 *       SN site locale; rows without an SE-side core yet are returned
 *       as {@link TurRagBm25Core.Status#NOT_PROVISIONED}.</li>
 *   <li>{@code POST /api/sn/{siteId}/rag/cores/provision} — creates the
 *       SE-side cores for every locale missing a {@code PROVISIONED} row.
 *       Idempotent: re-running it after partial failure picks up where
 *       the last attempt left off.</li>
 *   <li>{@code DELETE /api/sn/{siteId}/rag/cores} — drops every SE-side
 *       core registered for this site's store + the local rows. Used
 *       when admin reverts {@code ragBm25Source} to EMBEDDED.</li>
 * </ul>
 *
 * <p>Indexing the cores after provisioning is handled by the existing
 * RAG reindex flow ({@code POST /api/sn/{id}/genai/reindex}) — that path
 * already enumerates the SN site's locales and now also pushes chunks to
 * the matching BM25 core when {@code ragBm25Source = SE_INSTANCE}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Slf4j
@RestController
@RequestMapping("/api/sn/{siteId}/rag/cores")
@Tag(name = "RAG BM25 Cores", description = "T24b production hybrid: per-locale BM25 cores")
@RequiredArgsConstructor
public class TurRagBm25CoreAPI {

    // --- S1192: extracted duplicated literals ---
    private static final String LANGUAGE = "language";


    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurRagBm25CoreRepository ragBm25CoreRepository;
    private final TurRagBm25CoreProvisioner provisioner;
    private final TurGlobalSettingsService globalSettingsService;
    private final TurRagContextBuilder ragContextBuilder;
    private final com.viglet.turing.tenant.TurTenantCoreNaming turTenantCoreNaming;

    /**
     * Admin status row for one (site locale, BM25 core) pair. Sent to the
     * frontend as a JSON object — fields match the columns of the status
     * table in {@code sn.site.genai.form.tsx}.
     */
    public record CoreStatusDto(
            String locale,
            String coreName,
            String status,
            long docCount,
            String lastError,
            boolean provisioned) {
    }

    @Operation(summary = "List BM25 cores for an SN site (one row per locale)")
    @GetMapping
    @Transactional(readOnly = true)
    @Secured({ "ROLE_ADMIN", "SN_VIEW" })
    public ResponseEntity<List<CoreStatusDto>> listCores(@PathVariable String siteId) {
        return buildCoreStatus(siteId);
    }

    /**
     * Builds the per-locale core status table. Extracted so the transactional
     * {@code provisionCores} endpoint can reuse it without a {@code this}-call to
     * another {@code @Transactional} method (which would bypass the proxy).
     */
    private ResponseEntity<List<CoreStatusDto>> buildCoreStatus(String siteId) {
        Optional<TurSNSite> siteOpt = turSNSiteRepository.findById(siteId);
        if (siteOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        TurSNSite site = siteOpt.get();
        String storeInstanceId = globalSettingsService.getDefaultEmbeddingStoreId();
        if (storeInstanceId == null || storeInstanceId.isBlank()) {
            return ResponseEntity.ok(List.of());
        }

        List<TurSNSiteLocale> locales = turSNSiteLocaleRepository.findByTurSNSite(
                Sort.by(Sort.Direction.ASC, LANGUAGE), site);
        List<CoreStatusDto> rows = new ArrayList<>(locales.size());
        for (TurSNSiteLocale loc : locales) {
            String tag = loc.getLanguage().toLanguageTag();
            Optional<TurRagBm25Core> coreOpt = ragBm25CoreRepository
                    .findByTurStoreInstance_IdAndLocale(storeInstanceId, loc.getLanguage());
            if (coreOpt.isPresent()) {
                TurRagBm25Core core = coreOpt.get();
                rows.add(new CoreStatusDto(
                        tag,
                        core.getCoreName(),
                        core.getStatus().name(),
                        core.getDocCount(),
                        core.getLastError(),
                        true));
            } else {
                rows.add(new CoreStatusDto(
                        tag,
                        turTenantCoreNaming.scoped(TurRagBm25CoreProvisioner.coreNameFor(
                                resolveStoreInstance(), loc.getLanguage())),
                        TurRagBm25Core.Status.NOT_PROVISIONED.name(),
                        0L,
                        null,
                        false));
            }
        }
        return ResponseEntity.ok(rows);
    }

    @Operation(summary = "Provision missing BM25 cores for every SN site locale")
    @PostMapping("/provision")
    @Transactional
    @Secured({ "ROLE_ADMIN", "SN_EDIT" })
    public ResponseEntity<List<CoreStatusDto>> provisionCores(@PathVariable String siteId) {
        Optional<TurSNSite> siteOpt = turSNSiteRepository.findById(siteId);
        if (siteOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        TurSNSite site = siteOpt.get();
        TurSNSiteGenAi genAi = site.getTurSNSiteGenAi();
        if (genAi == null || genAi.getRagSeInstance() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(List.of());
        }
        String storeInstanceId = globalSettingsService.getDefaultEmbeddingStoreId();
        if (storeInstanceId == null || storeInstanceId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(List.of());
        }
        var storeInstance = resolveStoreInstance();
        if (storeInstance == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(List.of());
        }

        List<TurSNSiteLocale> locales = turSNSiteLocaleRepository.findByTurSNSite(
                Sort.by(Sort.Direction.ASC, LANGUAGE), site);
        for (TurSNSiteLocale loc : locales) {
            try {
                // Idempotent — already-PROVISIONED rows return without
                // a re-create call, ERROR rows are retried.
                provisioner.provision(storeInstance, loc.getLanguage(), genAi.getRagSeInstance());
            } catch (RuntimeException e) {
                log.warn("[RagCoresAPI] Failed to provision locale {} for site {}: {}",
                        loc.getLanguage().toLanguageTag(), siteId, e.getMessage());
                // Keep going — the row is now in ERROR state with the
                // captured message; the next provision retries it.
            }
        }
        // Return the updated status table so the UI can refresh in one
        // round-trip without a follow-up GET.
        return buildCoreStatus(siteId);
    }

    @Operation(summary = "Deprovision every BM25 core registered for this SN site's store")
    @DeleteMapping
    @Transactional
    @Secured({ "ROLE_ADMIN", "SN_EDIT" })
    public ResponseEntity<Void> deprovisionCores(@PathVariable String siteId) {
        Optional<TurSNSite> siteOpt = turSNSiteRepository.findById(siteId);
        if (siteOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        TurSNSite site = siteOpt.get();
        String storeInstanceId = globalSettingsService.getDefaultEmbeddingStoreId();
        if (storeInstanceId == null || storeInstanceId.isBlank()) {
            return ResponseEntity.noContent().build();
        }
        var storeInstance = resolveStoreInstance();
        if (storeInstance == null) {
            return ResponseEntity.noContent().build();
        }
        List<TurSNSiteLocale> locales = turSNSiteLocaleRepository.findByTurSNSite(
                Sort.by(Sort.Direction.ASC, LANGUAGE), site);
        for (TurSNSiteLocale loc : locales) {
            try {
                provisioner.deprovision(storeInstance, loc.getLanguage());
            } catch (RuntimeException e) {
                // Best-effort — surface the failure in the next status
                // GET (row stays in DELETING with lastError populated).
                log.warn("[RagCoresAPI] Failed to deprovision locale {} for site {}: {}",
                        loc.getLanguage().toLanguageTag(), siteId, e.getMessage());
            }
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Resolves the {@code TurStoreInstance} entity from the global-settings
     * default. Returns {@code null} when the store can't be loaded —
     * callers translate to a 400. Routed through {@link TurRagContextBuilder}
     * so the GenAI infra resolution path stays in one place.
     */
    private com.viglet.turing.persistence.model.store.TurStoreInstance resolveStoreInstance() {
        return ragContextBuilder.buildFromGlobalSettings()
                .map(TurRagContextBuilder.RagInfrastructure::storeInstance)
                .orElse(null);
    }
}
