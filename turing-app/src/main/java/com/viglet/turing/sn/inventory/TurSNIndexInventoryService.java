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
package com.viglet.turing.sn.inventory;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.plugins.se.TurSEInventoryPage;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * T1029 / §XX.16 — answers "what does the SN index hold for this source", the
 * read an external content audit cannot make for itself.
 *
 * <p>A connector already audits its source against its own indexing ledger, so
 * it knows what it published and what it recorded. What neither side knows is
 * what survived in the index: a write that failed or a delete that never
 * arrived leaves the ledger saying processed and the index saying otherwise.
 * This service is the honest per-source enumeration that closes that loop —
 * {@code (id, version)} pairs, paged by a keyset cursor, cheap enough to sweep
 * a whole site.
 *
 * <p>It deliberately runs no sweep of its own: comparing the two lists and
 * repairing the difference stays with the connector, which owns the queue.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TurSNIndexInventoryService {

    /** Page size when the caller names none. */
    public static final int DEFAULT_ROWS = 200;

    /**
     * Hard ceiling on a page. A sweep is a loop, so an unbounded page only
     * moves the cost from the number of requests to the size of one response —
     * and a 10k-row page is where an audit starts timing out instead of paging.
     */
    public static final int MAX_ROWS = 1000;

    private final TurSNSiteLocaleRepository snSiteLocaleRepository;
    private final TurSearchEnginePluginFactory pluginFactory;

    /**
     * Enumerates one page of the site's index for the given source.
     *
     * @param site      the site to audit
     * @param localeTag the language tag to enumerate; blank falls back to the
     *                  site's first locale by position
     * @param source    the {@code source_apps} value to filter by; blank
     *                  enumerates every document, which is what a single-source
     *                  site wants
     * @param after     the previous page's {@code nextCursor}; {@code null}
     *                  starts at the beginning
     * @param rows      page size; clamped to {@code [1, MAX_ROWS]}, defaulting
     *                  to {@link #DEFAULT_ROWS} when non-positive
     * @throws IllegalArgumentException when {@code localeTag} names a locale the
     *                                  site has no index for — a typo there
     *                                  would otherwise read as an empty index
     */
    public TurSNIndexInventoryResponse inventory(TurSNSite site, String localeTag, String source,
            String after, int rows) {
        List<TurSNSiteLocale> locales = snSiteLocaleRepository.findByTurSNSiteOrderByPositionAsc(site);
        if (locales.isEmpty()) {
            throw new IllegalArgumentException(
                    "Site '%s' has no locale configured, so it has no index to enumerate"
                            .formatted(site.getName()));
        }
        TurSNSiteLocale target = resolveLocale(site, locales, localeTag);
        String normalizedSource = blankToNull(source);
        int pageSize = clampRows(rows);

        TurSEInventoryPage page = readPage(site, target, normalizedSource, blankToNull(after),
                pageSize);

        return new TurSNIndexInventoryResponse(
                site.getId(),
                site.getName(),
                target.getLanguage().toLanguageTag(),
                locales.stream().map(l -> l.getLanguage().toLanguageTag()).toList(),
                normalizedSource,
                page.entries(),
                page.nextCursor(),
                page.hasMore(),
                page.remaining(),
                page.supported());
    }

    private TurSEInventoryPage readPage(TurSNSite site, TurSNSiteLocale target, String source,
            String after, int rows) {
        TurSearchEnginePlugin plugin = pluginFactory.getPluginForSite(site);
        try {
            return plugin.retrieveInventoryPage(target, source, after, rows);
        } catch (Exception e) {
            // An unreachable index is not an empty one: report unsupported so a
            // caller never reads the silence as "nothing is indexed" and
            // republishes the whole source.
            log.warn("[T1029] Inventory read failed for site {} locale {}: {}",
                    site.getName(), target.getLanguage(), e.getMessage());
            return TurSEInventoryPage.unsupported();
        }
    }

    private TurSNSiteLocale resolveLocale(TurSNSite site, List<TurSNSiteLocale> locales,
            String localeTag) {
        if (blankToNull(localeTag) == null) {
            return locales.getFirst();
        }
        Locale requested = Locale.forLanguageTag(localeTag.trim());
        TurSNSiteLocale found = snSiteLocaleRepository.findByTurSNSiteAndLanguage(site, requested);
        if (found == null) {
            throw new IllegalArgumentException(
                    "Site '%s' has no index for locale '%s'; configured locales are %s"
                            .formatted(site.getName(), localeTag,
                                    locales.stream().map(l -> l.getLanguage().toLanguageTag())
                                            .toList()));
        }
        return found;
    }

    private static int clampRows(int rows) {
        if (rows <= 0) {
            return DEFAULT_ROWS;
        }
        return Math.min(rows, MAX_ROWS);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
