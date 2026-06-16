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
 * {@link TurSNSiteManifest} (site + locales + ordered field specs) it
 * creates-or-converges the SN site and its field schema. Re-running with the
 * same manifest is a no-op; adding a field/locale is additive. This is the
 * declarative counterpart to console-clicking each {@code TurSNSiteField}, and
 * the foundation for schema-as-code (T386) and LLM-assisted derivation (T387).</p>
 *
 * <p>Field creation is delegated to the shared {@link TurSNFieldProvisioner} so
 * the manifest path and the indexing-time auto-create path share one
 * implementation.</p>
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
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public TurSNManifestResult provision(TurSNSiteManifest manifest) {
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
            // Re-read uncached: the converge path mutates and reads lazy
            // associations, which a cached entity can break in mappers.
            site = turSNSiteRepository.findByIdNoCache(existing.get().getId())
                    .orElseThrow(() -> new IllegalStateException("Site vanished mid-provision"));
            siteCreated = false;
        } else {
            site = createSite(manifest, locales, localesCreated);
            siteCreated = true;
        }

        ensureLocales(site, locales, localesCreated);

        List<String> fieldsCreated = new ArrayList<>();
        List<String> fieldsSkipped = new ArrayList<>();
        if (manifest.fields() != null) {
            for (TurSNJobAttributeSpec spec : manifest.fields()) {
                if (turSNFieldProvisioner.ensureField(site, spec)) {
                    fieldsCreated.add(spec.getName());
                } else {
                    fieldsSkipped.add(spec.getName());
                }
            }
        }

        eventPublisher.publishEvent(siteCreated
                ? SnSiteIndexInvalidatedEvent.created(site.getId(), site.getName())
                : SnSiteIndexInvalidatedEvent.updated(site.getId(), site.getName()));

        log.info("[T382] Provisioned site '{}' (created={}): {} field(s) added, {} skipped, {} locale(s) added",
                site.getName(), siteCreated, fieldsCreated.size(), fieldsSkipped.size(), localesCreated.size());

        return new TurSNManifestResult(site.getId(), site.getName(), siteCreated,
                fieldsCreated, fieldsSkipped, localesCreated);
    }

    private TurSNSite createSite(TurSNSiteManifest manifest, List<Locale> locales,
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

    private static void validateFields(List<TurSNJobAttributeSpec> fields) {
        if (fields == null) {
            return;
        }
        for (TurSNJobAttributeSpec spec : fields) {
            if (spec == null || !StringUtils.hasText(spec.getName())) {
                throw new IllegalArgumentException("Every manifest field must declare a 'name'");
            }
            if (spec.getType() == null) {
                throw new IllegalArgumentException(
                        "Manifest field '" + spec.getName() + "' must declare a 'type'");
            }
        }
    }
}
