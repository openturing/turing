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
package com.viglet.turing.sn.synonym;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.dto.sn.synonym.TurSNSynonymDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.properties.TurMigrationProperty;
import com.viglet.turing.sn.migration.TurAlgoliaClient;
import com.viglet.turing.sn.migration.TurAlgoliaClient.TurAlgoliaConnection;

/**
 * T666 / §XXXIX (Block AP) — closes the migration loop: fetches an Algolia
 * index's synonyms (which the T658 importer could only surface) and writes them
 * into Turing's own synonym store via {@link TurSNSynonymService#batch}. Reuses
 * the SDK-free {@link TurAlgoliaClient} and the pure {@link TurAlgoliaSynonymMapper}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurSNSynonymImportService {

    private final TurAlgoliaClient algoliaClient;
    private final TurMigrationProperty migrationProperty;
    private final TurSNSynonymService synonymService;

    public TurSNSynonymImportService(TurAlgoliaClient algoliaClient,
            TurMigrationProperty migrationProperty,
            TurSNSynonymService synonymService) {
        this.algoliaClient = algoliaClient;
        this.migrationProperty = migrationProperty;
        this.synonymService = synonymService;
    }

    /**
     * Imports an Algolia index's synonyms into the site's synonym store for the
     * given locale.
     *
     * @param host optional Algolia host override (blank → {@code {appId}-dsn.algolia.net})
     * @return the persisted synonym rules
     */
    public List<TurSNSynonymDto> importFromAlgolia(TurSNSite site, String appId, String apiKey,
            String host, String index, Locale locale) {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("'appId' is required");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("'apiKey' is required");
        }
        if (index == null || index.isBlank()) {
            throw new IllegalArgumentException("'index' is required");
        }
        if (locale == null) {
            throw new IllegalArgumentException("'locale' is required");
        }
        TurAlgoliaConnection conn = new TurAlgoliaConnection(appId, apiKey, host,
                migrationProperty.getTimeoutSeconds());
        List<Map<String, Object>> raw = algoliaClient.fetchSynonyms(conn, index);
        List<TurSNSynonymDto> dtos = TurAlgoliaSynonymMapper.toDtos(raw, locale);
        return synonymService.batch(site, dtos);
    }
}
