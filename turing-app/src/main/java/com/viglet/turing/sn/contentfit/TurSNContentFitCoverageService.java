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
package com.viglet.turing.sn.contentfit;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * T472 / §XXVI.9 — builds the audience content-fit coverage report for an SN
 * site by range-counting the {@value TurSNContentFitIndexer#FIELD_NAME} field
 * that {@link TurSNContentFitIndexer} writes at index time. It is a pure read
 * over the index (no indexing behaviour) and degrades gracefully — an engine
 * that cannot range-count yields {@code supported=false} so the UI can show an
 * "unavailable" state, exactly like the T388 field-coverage report.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TurSNContentFitCoverageService {

    /** Score below this is "too complex for its audience" (red). */
    public static final int TOO_COMPLEX_THRESHOLD = 40;
    /** Score at/above this is a good fit (green); between the two is borderline (amber). */
    public static final int GOOD_THRESHOLD = 70;

    private final TurSNSiteLocaleRepository snSiteLocaleRepository;
    private final TurPersonaRepository personaRepository;
    private final TurSearchEnginePluginFactory pluginFactory;

    public TurSNContentFitReport coverage(TurSNSite site) {
        TurSNSiteGenAi genAi = site.getTurSNSiteGenAi();
        boolean enabled = genAi != null && genAi.isContentFitIndexingEnabled();
        String personaId = genAi != null ? genAi.getContentFitPersonaId() : null;
        String personaName = resolvePersonaName(personaId);

        List<TurSNSiteLocale> locales = snSiteLocaleRepository.findByTurSNSite(site);
        TurSearchEnginePlugin plugin = pluginFactory.getPluginForSite(site);

        long total = sumOverLocales(locales, locale -> plugin.getDocumentTotal(locale));
        // -1 from any locale ⇒ engine can't report this measure for the whole site.
        long scored = sumSupported(locales,
                locale -> plugin.getDocumentCountWithField(locale, TurSNContentFitIndexer.FIELD_NAME));
        long belowTooComplex = sumSupported(locales, locale -> plugin.getDocumentCountWithFieldBelow(
                locale, TurSNContentFitIndexer.FIELD_NAME, TOO_COMPLEX_THRESHOLD));
        long belowGood = sumSupported(locales, locale -> plugin.getDocumentCountWithFieldBelow(
                locale, TurSNContentFitIndexer.FIELD_NAME, GOOD_THRESHOLD));

        boolean supported = scored >= 0 && belowTooComplex >= 0 && belowGood >= 0;
        if (!supported) {
            return new TurSNContentFitReport(site.getId(), site.getName(), enabled,
                    personaId, personaName, Math.max(0, total), 0, 0, 0, 0,
                    TOO_COMPLEX_THRESHOLD, GOOD_THRESHOLD, 0d, false);
        }

        long tooComplex = belowTooComplex;
        long borderline = Math.max(0, belowGood - belowTooComplex);
        long good = Math.max(0, scored - belowGood);
        double tooComplexPercent = scored > 0
                ? roundOneDecimal(tooComplex * 100.0 / scored)
                : 0d;

        return new TurSNContentFitReport(site.getId(), site.getName(), enabled,
                personaId, personaName, Math.max(0, total), scored, tooComplex, borderline, good,
                TOO_COMPLEX_THRESHOLD, GOOD_THRESHOLD, tooComplexPercent, true);
    }

    private String resolvePersonaName(String personaId) {
        if (StringUtils.isBlank(personaId)) {
            return null;
        }
        return personaRepository.findById(personaId).map(TurPersona::getName).orElse(null);
    }

    private long sumOverLocales(List<TurSNSiteLocale> locales, LocaleCount count) {
        long total = 0L;
        for (TurSNSiteLocale locale : locales) {
            try {
                total += Math.max(0L, count.apply(locale));
            } catch (Exception e) {
                log.warn("[T472] could not read a measure for locale {}: {}",
                        locale.getLanguage(), e.getMessage());
            }
        }
        return total;
    }

    /**
     * Sums a per-locale count, but returns {@code -1} (unsupported) if any locale
     * reports {@code -1} — a partial sum would understate the measure and mislead.
     */
    private long sumSupported(List<TurSNSiteLocale> locales, LocaleCount count) {
        long total = 0L;
        for (TurSNSiteLocale locale : locales) {
            long localeCount;
            try {
                localeCount = count.apply(locale);
            } catch (Exception e) {
                log.warn("[T472] could not count for locale {}: {}",
                        locale.getLanguage(), e.getMessage());
                return -1L;
            }
            if (localeCount < 0) {
                return -1L;
            }
            total += localeCount;
        }
        return total;
    }

    private static double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    @FunctionalInterface
    private interface LocaleCount {
        long apply(TurSNSiteLocale locale);
    }
}
