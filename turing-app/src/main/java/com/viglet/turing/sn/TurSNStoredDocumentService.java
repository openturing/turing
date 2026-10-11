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
package com.viglet.turing.sn;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshot;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshotService;

/**
 * T1077 — reads stored documents of a site by id, whole: every stored field,
 * unhighlighted and not shortened, which a detail page needs and a search
 * result does not give (a result carries highlight snippets and only the
 * fields the request lists).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurSNStoredDocumentService {

    /** Bound on one request, so a caller cannot ask for an unbounded id list. */
    static final int MAX_IDS = 50;

    private final TurSNSiteSearchSnapshotService snapshotService;
    private final TurSearchEnginePluginFactory pluginFactory;

    public TurSNStoredDocumentService(TurSNSiteSearchSnapshotService snapshotService,
            TurSearchEnginePluginFactory pluginFactory) {
        this.snapshotService = snapshotService;
        this.pluginFactory = pluginFactory;
    }

    /**
     * The stored documents with these ids, in the order asked, dropping ids the
     * site does not hold. Empty when the site or its locale is unknown.
     *
     * @param locale request locale (the site's first locale when null or not one of its own)
     */
    public List<Map<String, Object>> findByIds(String siteName, List<String> ids, Locale locale) {
        List<String> wanted = ids == null ? List.of() : ids.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .limit(MAX_IDS)
                .toList();
        if (siteName == null || wanted.isEmpty()) {
            return List.of();
        }
        Optional<TurSNSiteSearchSnapshot> snapshot = snapshotService.getSnapshot(siteName, locale);
        if (snapshot.isEmpty() || snapshot.get().site() == null) {
            return List.of();
        }
        TurSNSiteLocale siteLocale = TurSNSimilarDocumentsService.resolveLocale(snapshot.get(), locale);
        if (siteLocale == null) {
            return List.of();
        }
        return pluginFactory.getPluginForSite(snapshot.get().site())
                .getStoredDocumentsByIds(siteLocale, wanted);
    }
}
