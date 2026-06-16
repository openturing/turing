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

package com.viglet.turing.sn.field;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.LocaleUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.sn.TurSNFieldType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Single, idempotent code path that converges an SN site to a field declared by
 * a {@link TurSNJobAttributeSpec}: it creates the {@code TurSNSiteField}, its
 * {@code TurSNSiteFieldExt} (with facet flag, localized facet labels and the
 * {@code mandatory → required} flag), and registers the field in every locale's
 * search-engine core that is missing it. Re-running with an already-declared
 * field is a no-op.
 *
 * <p>This was extracted from {@code TurSNProcessQueue.createMissingFields(...)}
 * so the indexing-time auto-create path (a job item carrying attribute specs)
 * and the declarative field-manifest provisioning path (T382) share one
 * implementation — the "builds on {@code TurSNJobAttributeSpec}" intent from
 * the roadmap.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TurSNFieldProvisioner {

    /** Reserved facetName map key carrying the default (non-localized) label. */
    public static final String DEFAULT = "default";

    private final TurSNSiteFieldRepository turSNSiteFieldRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSEInstanceRepository turSEInstanceRepository;
    private final TurSearchEnginePluginFactory pluginFactory;

    /**
     * Ensures the field declared by {@code spec} exists on {@code turSNSite}.
     *
     * @return {@code true} when the field was created, {@code false} when it
     *         already existed (idempotent no-op).
     */
    public boolean ensureField(TurSNSite turSNSite, TurSNJobAttributeSpec spec) {
        if (turSNSiteFieldExtRepository.existsByTurSNSiteAndName(turSNSite, spec.getName())) {
            return false;
        }
        final TurSNSiteField turSNSiteField = saveSiteField(turSNSite, spec);
        saveFacetLocales(spec, saveSiteFieldExt(turSNSite, spec, turSNSiteField));
        turSNSiteLocaleRepository.findByTurSNSite(turSNSite).stream()
                .filter(turSNSiteLocale -> !existsFieldInSearchEngine(turSNSite,
                        turSNSiteLocale.getCore(), spec.getName()))
                .forEach(turSNSiteLocale -> createFieldInSearchEngine(turSNSite,
                        turSNSiteLocale.getCore(), turSNSiteField));
        return true;
    }

    private void saveFacetLocales(TurSNJobAttributeSpec spec, TurSNSiteFieldExt turSNSiteFieldExt) {
        if (spec.getFacetName() == null) {
            return;
        }
        Set<TurSNSiteFieldExtFacet> facetLocales = new HashSet<>();
        spec.getFacetName().forEach((key, value) -> {
            if (!key.equals(DEFAULT)) {
                TurSNSiteFieldExtFacet turSNSiteFieldExtFacet = new TurSNSiteFieldExtFacet();
                turSNSiteFieldExtFacet.setLocale(LocaleUtils.toLocale(key));
                turSNSiteFieldExtFacet.setLabel(value);
                turSNSiteFieldExtFacet.setTurSNSiteFieldExt(turSNSiteFieldExt);
                facetLocales.add(turSNSiteFieldExtFacet);
            }
        });
        turSNSiteFieldExtFacetRepository.saveAll(facetLocales);
    }

    @NotNull
    private TurSNSiteFieldExt saveSiteFieldExt(TurSNSite turSNSite, TurSNJobAttributeSpec spec,
            TurSNSiteField turSNSiteField) {
        Map<String, String> facetName = spec.getFacetName();
        TurSNSiteFieldExt turSNSiteFieldExt = TurSNSiteFieldExt.builder().enabled(1)
                .name(turSNSiteField.getName()).description(turSNSiteField.getDescription())
                .facet(spec.isFacet() ? 1 : 0)
                .facetName(facetName != null ? facetName.get(DEFAULT) : null).hl(0)
                .multiValued(turSNSiteField.getMultiValued()).mlt(0)
                .required(spec.isMandatory() ? 1 : 0)
                .externalId(turSNSiteField.getId()).snType(TurSNFieldType.SE)
                .type(turSNSiteField.getType()).turSNSite(turSNSite).build();
        turSNSiteFieldExtRepository.save(turSNSiteFieldExt);
        return turSNSiteFieldExt;
    }

    @NotNull
    private TurSNSiteField saveSiteField(TurSNSite turSNSite, TurSNJobAttributeSpec spec) {
        TurSNSiteField turSNSiteField = TurSNSiteField.builder().name(spec.getName())
                .description(spec.getDescription()).type(spec.getType())
                .multiValued(spec.isMultiValued() ? 1 : 0).turSNSite(turSNSite).build();
        turSNSiteFieldRepository.save(turSNSiteField);
        return turSNSiteField;
    }

    private void createFieldInSearchEngine(TurSNSite turSNSite, String coreName,
            TurSNSiteField turSNSiteField) {
        turSEInstanceRepository.findById(turSNSite.getTurSEInstance().getId())
                .ifPresent(seInstance -> pluginFactory.getPluginForSite(turSNSite)
                        .addOrUpdateField(seInstance, coreName, turSNSiteField.getName(),
                                turSNSiteField.getType(), true, turSNSiteField.getMultiValued() == 1, true));
    }

    private boolean existsFieldInSearchEngine(TurSNSite turSNSite, String coreName, String name) {
        if (turSNSite.getTurSEInstance() == null) {
            return false;
        }
        return turSEInstanceRepository.findById(turSNSite.getTurSEInstance().getId())
                .map(seInstance -> pluginFactory.getPluginForSite(turSNSite)
                        .fieldExists(seInstance, coreName, name))
                .orElse(false);
    }
}
