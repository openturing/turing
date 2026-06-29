/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.sn.manifest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.lang3.LocaleUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.viglet.core.manifest.VigletFieldManifest;
import com.viglet.core.manifest.VigletFieldSpec;
import com.viglet.core.manifest.VigletManifestDiff;
import com.viglet.core.manifest.VigletManifestFieldChange;
import com.viglet.core.manifest.VigletManifestResult;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.domain.sn.SnSiteIndexInvalidatedEvent;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.sn.field.TurSNFieldProvisioner;
import com.viglet.turing.sn.template.TurSNTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Field-manifest site provisioning (T382 / §XX.2).
 *
 * <p>Turns "stand up a structured source" into a single idempotent call: given a
 * {@link VigletFieldManifest} (site + locales + ordered field specs) it
 * creates-or-converges the SN site and its field schema. Re-running with the
 * same manifest is a no-op; adding a field/locale is additive. This is the
 * declarative counterpart to console-clicking each {@code TurSNSiteField}, and
 * the foundation for schema-as-code (T386) and LLM-assisted derivation (T387).</p>
 *
 * <p>The manifest model is the product-neutral viglet-core kernel (T393); this
 * service maps each {@link VigletFieldSpec} to Turing's {@link TurSNJobAttributeSpec}
 * via {@link TurSNManifestMapper} and delegates field creation to the shared
 * {@link TurSNFieldProvisioner} so the manifest path and the indexing-time
 * auto-create path share one implementation.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TurSNSiteManifestService {

    private static final String PROVISIONER_USERNAME = "manifest";

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSEInstanceRepository turSEInstanceRepository;
    private final TurSNTemplate turSNTemplate;
    private final TurSNFieldProvisioner turSNFieldProvisioner;
    private final TurSNManifestPlanner turSNManifestPlanner;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Computes — without mutating anything — the diff a manifest would apply
     * against the live site (T386 / §XX.6). The dry-run that makes a schema
     * change reviewable in a PR before it is provisioned.
     */
    @Transactional(readOnly = true)
    public VigletManifestDiff plan(VigletFieldManifest manifest) {
        if (manifest == null || !StringUtils.hasText(manifest.name())) {
            throw new IllegalArgumentException("Manifest 'name' is required");
        }
        validateFields(manifest.fields());
        return turSNManifestPlanner.diff(manifest);
    }

    @Transactional
    public VigletManifestResult provision(VigletFieldManifest manifest) {
        if (manifest == null || !StringUtils.hasText(manifest.name())) {
            throw new IllegalArgumentException("Manifest 'name' is required");
        }
        validateFields(manifest.fields());

        List<Locale> locales = parseLocales(manifest.locales());
        List<String> localesCreated = new ArrayList<>();

        Optional<TurSNSite> existing = turSNSiteRepository.findByNameIgnoreCase(manifest.name());
        TurSNSite site;
        boolean siteCreated;
        if (existing.isPresent()) {
            // Re-read with the GenAI graph: the converge path mutates and reads
            // lazy associations, fetched here in one query (T488 / §XXVIII.3 —
            // repositories are uncached, so findById is already session-attached).
            site = turSNSiteRepository.findByIdWithGenAi(existing.get().getId())
                    .orElseThrow(() -> new IllegalStateException("Site vanished mid-provision"));
            siteCreated = false;
        } else {
            site = createSite(manifest, locales, localesCreated);
            siteCreated = true;
        }

        ensureLocales(site, locales, localesCreated);

        // Schema-as-code diff (T386): reject undeclared breaking changes before
        // touching anything, so a type/cardinality rewrite is never silent.
        VigletManifestDiff diff = turSNManifestPlanner.diff(manifest, site);
        if (!diff.undeclaredBreakingChanges().isEmpty()) {
            throw new TurSNManifestMigrationRequiredException(diff.undeclaredBreakingChanges());
        }

        // Declared breaking changes: drop + recreate the field (destructive, but
        // explicitly authorized by a migration entry).
        List<String> fieldsMigrated = applyBreakingChanges(site, manifest, diff);

        List<String> fieldsCreated = new ArrayList<>();
        List<String> fieldsSkipped = new ArrayList<>();
        applyFieldChanges(site, manifest, fieldsMigrated, fieldsCreated, fieldsSkipped);

        eventPublisher.publishEvent(siteCreated
                ? SnSiteIndexInvalidatedEvent.created(site.getId(), site.getName())
                : SnSiteIndexInvalidatedEvent.updated(site.getId(), site.getName()));

        log.info("[T386] Provisioned site '{}' (created={}, schemaVersion={}): {} field(s) added, "
                        + "{} migrated, {} skipped, {} locale(s) added",
                site.getName(), siteCreated, manifest.schemaVersion(), fieldsCreated.size(),
                fieldsMigrated.size(), fieldsSkipped.size(), localesCreated.size());

        return new VigletManifestResult(site.getId(), site.getName(), siteCreated,
                fieldsCreated, fieldsSkipped, fieldsMigrated, localesCreated, manifest.schemaVersion());
    }

    /**
     * Drops + recreates each field declared as a breaking change (destructive,
     * but explicitly authorized by a migration entry). Returns the names
     * actually migrated.
     */
    private List<String> applyBreakingChanges(TurSNSite site, VigletFieldManifest manifest,
            VigletManifestDiff diff) {
        List<String> fieldsMigrated = new ArrayList<>();
        for (VigletManifestFieldChange change : diff.breakingChanges()) {
            TurSNJobAttributeSpec spec = TurSNManifestMapper.toJobSpec(fieldSpec(manifest, change.field()));
            if (spec != null && turSNFieldProvisioner.recreateField(site, spec)) {
                fieldsMigrated.add(change.field());
            }
        }
        return fieldsMigrated;
    }

    /**
     * Converges every manifest field not already handled by a migration,
     * partitioning the outcomes into {@code fieldsCreated} / {@code fieldsSkipped}.
     */
    private void applyFieldChanges(TurSNSite site, VigletFieldManifest manifest,
            List<String> fieldsMigrated, List<String> fieldsCreated, List<String> fieldsSkipped) {
        if (manifest.fields() == null) {
            return;
        }
        for (VigletFieldSpec field : manifest.fields()) {
            if (fieldsMigrated.contains(field.name())) {
                continue; // already converged by the migration above
            }
            if (turSNFieldProvisioner.ensureField(site, TurSNManifestMapper.toJobSpec(field))) {
                fieldsCreated.add(field.name());
            } else {
                fieldsSkipped.add(field.name());
            }
        }
    }

    private static VigletFieldSpec fieldSpec(VigletFieldManifest manifest, String name) {
        if (manifest.fields() == null) {
            return null;
        }
        return manifest.fields().stream()
                .filter(spec -> spec != null && name.equals(spec.name()))
                .findFirst().orElse(null);
    }

    private TurSNSite createSite(VigletFieldManifest manifest, List<Locale> locales,
            List<String> localesCreated) {
        TurSEInstance seInstance = turSEInstanceRepository.findById(
                Optional.ofNullable(manifest.seInstanceId())
                        .filter(StringUtils::hasText)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "seInstanceId is required to create a new site")))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Search-engine instance not found: " + manifest.seInstanceId()));

        TurSNSite site = new TurSNSite();
        site.setName(manifest.name());
        site.setDescription(manifest.description());
        site.setTurSEInstance(seInstance);
        turSNSiteRepository.save(site);

        // Seeds default UI config + the first locale (core) + the base SE fields.
        Locale seedLocale = locales.isEmpty() ? Locale.US : locales.get(0);
        turSNTemplate.createSNSite(site, PROVISIONER_USERNAME, seedLocale);
        localesCreated.add(seedLocale.toString());
        return site;
    }

    private void ensureLocales(TurSNSite site, List<Locale> locales, List<String> localesCreated) {
        for (Locale locale : locales) {
            if (!turSNSiteLocaleRepository.existsByTurSNSiteAndLanguage(site, locale)) {
                turSNTemplate.createLocale(site, PROVISIONER_USERNAME, locale);
                localesCreated.add(locale.toString());
            }
        }
    }

    private static List<Locale> parseLocales(List<String> codes) {
        if (codes == null) {
            return List.of();
        }
        List<Locale> locales = new ArrayList<>();
        for (String code : codes) {
            if (StringUtils.hasText(code)) {
                locales.add(LocaleUtils.toLocale(code.trim()));
            }
        }
        return locales;
    }

    private static void validateFields(List<VigletFieldSpec> fields) {
        if (fields == null) {
            return;
        }
        for (VigletFieldSpec spec : fields) {
            if (spec == null || !StringUtils.hasText(spec.name())) {
                throw new IllegalArgumentException("Every manifest field must declare a 'name'");
            }
            if (spec.type() == null) {
                throw new IllegalArgumentException(
                        "Manifest field '" + spec.name() + "' must declare a 'type'");
            }
        }
    }
}
