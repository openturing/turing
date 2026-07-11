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

package com.viglet.turing.sn.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.LocaleUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.core.manifest.VigletFieldManifest;
import com.viglet.core.manifest.VigletFieldSpec;
import com.viglet.core.manifest.VigletManifestResult;
import com.viglet.turing.properties.TurMigrationProperty;
import com.viglet.turing.sn.migration.TurElasticsearchClient.EsHit;
import com.viglet.turing.sn.migration.TurElasticsearchClient.EsPage;
import com.viglet.turing.sn.migration.TurElasticsearchClient.TurEsConnection;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates an Elasticsearch &rarr; Turing migration (T657 / §XXXVIII.1).
 *
 * <p>Reads the source {@code _mapping}, derives a field manifest (deterministic,
 * {@link TurElasticsearchManifestBuilder}), provisions the SN site + schema, then
 * scrolls the whole index a page at a time, reshaping each document
 * ({@link TurEsDocumentMapper}) into batched import job items and flushing with a
 * trailing COMMIT. The first scroll page doubles as the sample used to detect
 * multi-valued fields, so no extra request is spent. A {@code dryRun} stops after
 * deriving the manifest.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TurElasticsearchMigrationService {

    private static final String ENGINE = "ELASTICSEARCH";
    private static final String PROVIDER = "elasticsearch";

    private final TurElasticsearchClient client;
    private final TurMigrationImportService importService;
    private final TurMigrationProperty migrationProperty;

    public TurMigrationResult migrate(TurEsMigrationRequest request) {
        validate(request);

        int batchSize = positiveOr(request.batchSize(), migrationProperty.getBatchSize());
        int maxDocuments = request.maxDocuments() != null ? Math.max(0, request.maxDocuments())
                : migrationProperty.getMaxDocuments();
        String keepAlive = migrationProperty.getEsScrollKeepAlive();
        Locale locale = parseLocale(request.locale());

        TurEsConnection conn = new TurEsConnection(request.sourceUrl(), request.username(),
                request.password(), request.apiKey(), migrationProperty.getTimeoutSeconds());

        Map<String, Object> mapping = client.fetchMapping(conn, request.index());
        EsPage page = client.openScroll(conn, request.index(), batchSize, keepAlive);

        // The first page is the multi-valued detection sample (bounded by sampleSize).
        List<Map<String, Object>> sample = new ArrayList<>();
        for (EsHit hit : page.hits()) {
            if (sample.size() >= migrationProperty.getSampleSize()) {
                break;
            }
            sample.add(hit.source());
        }

        TurElasticsearchManifestBuilder.Result built =
                TurElasticsearchManifestBuilder.build(mapping, sample);
        List<String> warnings = new ArrayList<>(built.warnings());
        List<VigletFieldSpec> fields = TurMigrationOverrides.applyToFields(built.fields(), request.overrides());
        VigletFieldManifest manifest = manifest(request, fields);

        if (request.dryRun()) {
            client.clearScroll(conn, page.scrollId());
            log.info("[T657] Dry-run migration of ES index '{}' -> site '{}': {} field(s) detected",
                    request.index(), request.targetSite(), fields.size());
            return new TurMigrationResult(ENGINE, request.targetSite(), false, fields.size(),
                    List.of(), List.of(), page.hits().size(), 0, true, manifest, warnings);
        }

        VigletManifestResult provisioned = importService.provision(manifest);

        int imported = importAll(conn, request, locale, page, keepAlive, maxDocuments);
        importService.commit(request.targetSite(), locale);

        log.info("[T657] Migrated ES index '{}' -> site '{}': {} field(s) created, {} document(s) imported",
                request.index(), request.targetSite(), provisioned.fieldsCreated().size(), imported);

        return new TurMigrationResult(ENGINE, provisioned.siteName(), provisioned.siteCreated(),
                fields.size(), provisioned.fieldsCreated(), provisioned.fieldsSkipped(),
                imported, imported, false, manifest, warnings);
    }

    /** Imports the already-open first page then follows the scroll to exhaustion. */
    private int importAll(TurEsConnection conn, TurEsMigrationRequest request, Locale locale,
            EsPage firstPage, String keepAlive, int maxDocuments) {
        int imported = 0;
        EsPage page = firstPage;
        while (page != null && !page.hits().isEmpty()) {
            List<EsHit> hits = page.hits();
            if (maxDocuments > 0 && imported + hits.size() > maxDocuments) {
                hits = hits.subList(0, maxDocuments - imported);
            }
            List<Map<String, Object>> batch = new ArrayList<>(hits.size());
            for (EsHit hit : hits) {
                batch.add(TurMigrationOverrides.applyToAttributes(
                        TurEsDocumentMapper.toAttributes(hit.id(), hit.source(), request.index(), PROVIDER),
                        request.overrides()));
            }
            importService.importBatch(request.targetSite(), locale, batch);
            imported += batch.size();
            if (maxDocuments > 0 && imported >= maxDocuments) {
                break;
            }
            page = client.nextScroll(conn, page.scrollId(), keepAlive);
        }
        client.clearScroll(conn, page != null ? page.scrollId() : firstPage.scrollId());
        return imported;
    }

    private VigletFieldManifest manifest(TurEsMigrationRequest request, List<VigletFieldSpec> fields) {
        List<String> locales = StringUtils.hasText(request.locale())
                ? List.of(request.locale().trim())
                : List.of(Locale.US.toString());
        return new VigletFieldManifest(request.targetSite(),
                "Imported from Elasticsearch index '" + request.index() + "'",
                request.seInstanceId(), "1", locales, fields, null);
    }

    private static void validate(TurEsMigrationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (!StringUtils.hasText(request.sourceUrl())) {
            throw new IllegalArgumentException("'sourceUrl' is required");
        }
        if (!StringUtils.hasText(request.index())) {
            throw new IllegalArgumentException("'index' is required");
        }
        if (!StringUtils.hasText(request.targetSite())) {
            throw new IllegalArgumentException("'targetSite' is required");
        }
    }

    private static Locale parseLocale(String code) {
        return StringUtils.hasText(code) ? LocaleUtils.toLocale(code.trim()) : Locale.US;
    }

    private static int positiveOr(Integer value, int fallback) {
        return value != null && value > 0 ? value : fallback;
    }
}
