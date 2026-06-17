/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.sn.coverage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * T388 — computes per-field coverage / completeness observability for a
 * Semantic Navigation site. For each enabled field it counts how many indexed
 * documents populate it (across every locale) and divides by the site's total
 * document count, yielding the fraction of documents a source actually fills in.
 *
 * <p>This rides the T381 grounding contract — absent values are not counted as
 * present — and is a pure count over the index, adding no indexing behaviour.
 * Results are ordered worst-coverage-first so the most under-filled fields (the
 * crawler/source gaps) surface at the top.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TurSNFieldCoverageService {

    private final TurSNSiteLocaleRepository snSiteLocaleRepository;
    private final TurSNSiteFieldExtRepository snSiteFieldExtRepository;
    private final TurSearchEnginePluginFactory pluginFactory;

    /**
     * Builds the coverage report for the given site. Never throws on engine
     * errors — an unreachable index yields a report with {@code supported=false}
     * and a zero total so the caller can render a graceful "unavailable" state.
     */
    public TurSNFieldCoverageReport coverage(TurSNSite site) {
        List<TurSNSiteLocale> locales = snSiteLocaleRepository.findByTurSNSite(site);
        TurSearchEnginePlugin plugin = pluginFactory.getPluginForSite(site);

        long totalDocuments = totalDocuments(plugin, locales);

        List<TurSNFieldCoverage> fields = new ArrayList<>();
        boolean engineSupported = false;

        for (TurSNSiteFieldExt field : snSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1)) {
            FieldPresence presence = countPresence(plugin, locales, field.getName());
            if (presence.supported()) {
                engineSupported = true;
            }
            double percent = (presence.supported() && totalDocuments > 0)
                    ? roundOneDecimal(presence.count() * 100.0 / totalDocuments)
                    : 0d;
            fields.add(new TurSNFieldCoverage(
                    field.getName(),
                    field.getType() != null ? field.getType().name() : null,
                    field.getMultiValued() == 1,
                    field.getFacet() == 1,
                    presence.supported() ? presence.count() : -1L,
                    totalDocuments,
                    percent,
                    presence.supported()));
        }

        fields.sort(Comparator
                .comparingDouble(TurSNFieldCoverage::coveragePercent)
                .thenComparing(TurSNFieldCoverage::fieldName));

        return new TurSNFieldCoverageReport(
                site.getId(), site.getName(), totalDocuments, engineSupported, fields);
    }

    private long totalDocuments(TurSearchEnginePlugin plugin, List<TurSNSiteLocale> locales) {
        long total = 0L;
        for (TurSNSiteLocale locale : locales) {
            try {
                total += Math.max(0L, plugin.getDocumentTotal(locale));
            } catch (Exception e) {
                log.warn("[T388] Could not read document total for locale {}: {}",
                        locale.getLanguage(), e.getMessage());
            }
        }
        return total;
    }

    /**
     * Sums the per-locale present counts for a field. If any locale reports
     * "unsupported" ({@code -1}), the whole field is marked unsupported — a
     * partial count would understate coverage and mislead.
     */
    private FieldPresence countPresence(TurSearchEnginePlugin plugin,
            List<TurSNSiteLocale> locales, String fieldName) {
        long count = 0L;
        for (TurSNSiteLocale locale : locales) {
            long localeCount;
            try {
                localeCount = plugin.getDocumentCountWithField(locale, fieldName);
            } catch (Exception e) {
                log.warn("[T388] Could not count field '{}' for locale {}: {}",
                        fieldName, locale.getLanguage(), e.getMessage());
                return new FieldPresence(0L, false);
            }
            if (localeCount < 0) {
                return new FieldPresence(0L, false);
            }
            count += localeCount;
        }
        return new FieldPresence(count, true);
    }

    private static double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record FieldPresence(long count, boolean supported) {
    }
}
