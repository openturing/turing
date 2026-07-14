/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.sn.TurSNSearchProcess;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshot;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshotService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Pulls the searchable text of a single indexed Semantic Navigation document
 * for a persona {@code SN_DOC} source (Block AA / §XXVI.2). Confines all of the
 * search-engine plumbing (context construction, per-locale snapshot lookup,
 * field flattening) to one place so the persona-source extractor stays clean.
 *
 * <p>Best-effort: queries each of the site's locales for {@code id:"<ref>"} and
 * returns the first non-empty hit's text. Returns blank when the site is
 * unknown or the document is not indexed — the caller then marks the source
 * {@code FAILED} (retryable), never throwing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurPersonaSnDocResolver {

    private static final int MAX_FIELD_VALUE = 100_000;

    private final TurSNSearchProcess turSNSearchProcess;
    private final TurSNSiteSearchSnapshotService snapshotService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TurPersonaSnDocResolver(TurSNSearchProcess turSNSearchProcess,
            TurSNSiteSearchSnapshotService snapshotService) {
        this.turSNSearchProcess = turSNSearchProcess;
        this.snapshotService = snapshotService;
    }

    /**
     * Resolve the text of the document identified by {@code documentId} inside
     * {@code siteName}. Returns blank when nothing is found.
     */
    public String resolveText(String siteName, String documentId) {
        if (StringUtils.isAnyBlank(siteName, documentId)) {
            return "";
        }
        // Use the materialized snapshot (allLocales) rather than the site
        // entity's LAZY turSNSiteLocales collection: getSnapshot returns a
        // cached, detached TurSNSite, so touching its lazy associations throws
        // LazyInitializationException ("no session") and rolls back the caller's
        // transaction. The snapshot already carries the locales it loaded.
        TurSNSiteSearchSnapshot snapshot = snapshotService.getSnapshot(siteName, null).orElse(null);
        if (snapshot == null || snapshot.site() == null) {
            log.warn("[PersonaSource] SN_DOC site '{}' not found", siteName);
            return "";
        }
        List<Locale> locales = snapshot.allLocales().stream()
                .map(TurSNSiteLocale::getLanguage)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (locales.isEmpty()) {
            locales = List.of(Locale.ENGLISH);
        }
        for (Locale locale : locales) {
            String text = queryOneLocale(siteName, documentId, locale);
            if (StringUtils.isNotBlank(text)) {
                return text;
            }
        }
        return "";
    }

    private String queryOneLocale(String siteName, String documentId, Locale locale) {
        try {
            TurSNSearchParams params = new TurSNSearchParams();
            params.setQ("id:\"" + documentId.replace("\"", "") + "\"");
            params.setRows(1);
            params.setP(1);
            TurSEParameters seParameters = new TurSEParameters(params);
            TurSNSiteSearchContext context = new TurSNSiteSearchContext(
                    siteName, new TurSNConfig(), seParameters, locale,
                    URI.create("/api/sn/" + siteName + "/search"));
            List<Object> results = turSNSearchProcess.searchList(context);
            if (results == null || results.isEmpty()) {
                return "";
            }
            return flattenStrings(results.get(0));
        } catch (RuntimeException e) {
            log.debug("[PersonaSource] SN_DOC query failed for {} in {}/{}: {}",
                    documentId, siteName, locale, e.getMessage());
            return "";
        }
    }

    /** Flatten a result bean's string field values into one block of text. */
    private String flattenStrings(Object result) {
        Object asTree = objectMapper.convertValue(result, Object.class);
        StringJoiner joiner = new StringJoiner("\n");
        collect(asTree, joiner);
        return joiner.toString().strip();
    }

    private void collect(Object node, StringJoiner joiner) {
        switch (node) {
            case null -> { /* skip */ }
            case String s -> {
                String trimmed = s.strip();
                if (!trimmed.isEmpty() && trimmed.length() <= MAX_FIELD_VALUE) {
                    joiner.add(trimmed);
                }
            }
            case java.util.Map<?, ?> map ->
                map.values().forEach(v -> collect(v, joiner));
            case Iterable<?> iterable ->
                iterable.forEach(v -> collect(v, joiner));
            default -> { /* numbers/booleans carry no readable prose */ }
        }
    }
}
