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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.LocaleUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.core.manifest.VigletFieldManifest;
import com.viglet.core.manifest.VigletFieldSpec;
import com.viglet.core.manifest.VigletManifestDeriveRequest;
import com.viglet.core.manifest.VigletManifestDeriver;
import com.viglet.core.manifest.VigletManifestHeuristics;
import com.viglet.core.manifest.VigletManifestResult;
import com.viglet.core.manifest.VigletManifestSampleAnalyzer;
import com.viglet.turing.properties.TurMigrationProperty;
import com.viglet.turing.sn.migration.TurAlgoliaClient.AlgoliaBrowsePage;
import com.viglet.turing.sn.migration.TurAlgoliaClient.TurAlgoliaConnection;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates an Algolia &rarr; Turing migration (T658 / §XXXVIII.2).
 *
 * <p>Reads the index settings, browses the whole index (cursor API), derives the
 * field schema from a sample of records — deterministically via the T387 kernel
 * heuristics, or via an LLM when requested and available — then overlays the
 * settings (facets / searchable / ranking) with {@link TurAlgoliaManifestBuilder}.
 * Records reshape through the shared {@link TurMigrationDocumentMapper} ({@code
 * objectID} becomes the SN id) and push through the shared import path with a
 * trailing COMMIT. Synonyms are fetched and <em>surfaced</em> in the result rather
 * than silently dropped — Turing has no managed synonym API, so they are applied
 * in the target search engine's schema. A {@code dryRun} stops after deriving.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TurAlgoliaMigrationService {

    private static final String ENGINE = "ALGOLIA";
    private static final String PROVIDER = "algolia";
    private static final String OBJECT_ID = "objectID";

    private final TurAlgoliaClient client;
    private final TurMigrationImportService importService;
    private final TurMigrationProperty migrationProperty;
    private final VigletManifestDeriver manifestDeriver;

    public TurMigrationResult migrate(TurAlgoliaMigrationRequest request) {
        validate(request);

        int batchSize = positiveOr(request.batchSize(), migrationProperty.getBatchSize());
        int maxDocuments = request.maxDocuments() != null ? Math.max(0, request.maxDocuments())
                : migrationProperty.getMaxDocuments();
        Locale locale = parseLocale(request.locale());

        TurAlgoliaConnection conn = new TurAlgoliaConnection(request.appId(), request.apiKey(),
                request.host(), migrationProperty.getTimeoutSeconds());

        Map<String, Object> settings = client.fetchSettings(conn, request.index());
        AlgoliaBrowsePage page = client.browse(conn, request.index(), null, batchSize);

        List<Map<String, Object>> sample = sampleFrom(page.hits());
        List<VigletFieldSpec> baseFields = deriveBaseFields(request, sample);
        TurAlgoliaManifestBuilder.Result built = TurAlgoliaManifestBuilder.build(baseFields, settings);
        List<String> warnings = new ArrayList<>(built.warnings());
        List<VigletFieldSpec> fields = TurMigrationOverrides.applyToFields(built.fields(), request.overrides());

        surfaceSynonyms(conn, request.index(), warnings);

        VigletFieldManifest manifest = manifest(request, fields);

        if (request.dryRun()) {
            log.info("[T658] Dry-run migration of Algolia index '{}' -> site '{}': {} field(s) detected",
                    request.index(), request.targetSite(), fields.size());
            return new TurMigrationResult(ENGINE, request.targetSite(), false, fields.size(),
                    List.of(), List.of(), page.hits().size(), 0, true, manifest, warnings);
        }

        VigletManifestResult provisioned = importService.provision(manifest);

        int imported = importAll(conn, request, locale, page, batchSize, maxDocuments);
        importService.commit(request.targetSite(), locale);

        log.info("[T658] Migrated Algolia index '{}' -> site '{}': {} field(s) created, {} document(s) imported",
                request.index(), request.targetSite(), provisioned.fieldsCreated().size(), imported);

        return new TurMigrationResult(ENGINE, provisioned.siteName(), provisioned.siteCreated(),
                fields.size(), provisioned.fieldsCreated(), provisioned.fieldsSkipped(),
                imported, imported, false, manifest, warnings);
    }

    /** Derives base field specs from sample records — LLM when asked & available, else heuristics. */
    private List<VigletFieldSpec> deriveBaseFields(TurAlgoliaMigrationRequest request,
            List<Map<String, Object>> sample) {
        List<Map<String, Object>> records = stripObjectId(sample);
        if (records.isEmpty()) {
            return List.of();
        }
        if (request.useLlm() && manifestDeriver.isLlmAvailable()) {
            VigletFieldManifest draft = manifestDeriver.derive(new VigletManifestDeriveRequest(
                    request.targetSite(), null, request.seInstanceId(), localesOf(request), records));
            return draft.fields() == null ? List.of() : draft.fields();
        }
        return VigletManifestHeuristics.toFieldSpecs(
                new VigletManifestSampleAnalyzer().analyze(records));
    }

    private void surfaceSynonyms(TurAlgoliaConnection conn, String index, List<String> warnings) {
        List<Map<String, Object>> synonyms = client.fetchSynonyms(conn, index);
        if (synonyms.isEmpty()) {
            return;
        }
        warnings.add(synonyms.size() + " Algolia synonym set(s) found. Turing has no managed synonym API; "
                + "apply them in the target search engine's schema (Solr synonyms / Elasticsearch synonym "
                + "filter). The Lucene engine does not support synonyms.");
    }

    /** Imports the already-open first page then follows the cursor to exhaustion. */
    private int importAll(TurAlgoliaConnection conn, TurAlgoliaMigrationRequest request, Locale locale,
            AlgoliaBrowsePage firstPage, int hitsPerPage, int maxDocuments) {
        int imported = 0;
        AlgoliaBrowsePage page = firstPage;
        while (page != null && !page.hits().isEmpty()) {
            List<Map<String, Object>> hits = page.hits();
            if (maxDocuments > 0 && imported + hits.size() > maxDocuments) {
                hits = hits.subList(0, maxDocuments - imported);
            }
            List<Map<String, Object>> batch = new ArrayList<>(hits.size());
            for (Map<String, Object> record : hits) {
                String id = record.get(OBJECT_ID) == null ? null : record.get(OBJECT_ID).toString();
                batch.add(TurMigrationOverrides.applyToAttributes(
                        TurMigrationDocumentMapper.toAttributes(id, without(record, OBJECT_ID),
                                request.index(), PROVIDER),
                        request.overrides()));
            }
            importService.importBatch(request.targetSite(), locale, batch);
            imported += batch.size();
            if ((maxDocuments > 0 && imported >= maxDocuments) || !StringUtils.hasText(page.cursor())) {
                break;
            }
            page = client.browse(conn, request.index(), page.cursor(), hitsPerPage);
        }
        return imported;
    }

    private List<Map<String, Object>> sampleFrom(List<Map<String, Object>> hits) {
        List<Map<String, Object>> sample = new ArrayList<>();
        for (Map<String, Object> hit : hits) {
            if (sample.size() >= migrationProperty.getSampleSize()) {
                break;
            }
            sample.add(hit);
        }
        return sample;
    }

    private static List<Map<String, Object>> stripObjectId(List<Map<String, Object>> records) {
        List<Map<String, Object>> stripped = new ArrayList<>(records.size());
        for (Map<String, Object> record : records) {
            stripped.add(without(record, OBJECT_ID));
        }
        return stripped;
    }

    private static Map<String, Object> without(Map<String, Object> record, String key) {
        Map<String, Object> copy = new LinkedHashMap<>(record);
        copy.remove(key);
        return copy;
    }

    private VigletFieldManifest manifest(TurAlgoliaMigrationRequest request, List<VigletFieldSpec> fields) {
        return new VigletFieldManifest(request.targetSite(),
                "Imported from Algolia index '" + request.index() + "'",
                request.seInstanceId(), "1", localesOf(request), fields, null);
    }

    private static List<String> localesOf(TurAlgoliaMigrationRequest request) {
        return StringUtils.hasText(request.locale())
                ? List.of(request.locale().trim())
                : List.of(Locale.US.toString());
    }

    private static void validate(TurAlgoliaMigrationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (!StringUtils.hasText(request.appId())) {
            throw new IllegalArgumentException("'appId' is required");
        }
        if (!StringUtils.hasText(request.apiKey())) {
            throw new IllegalArgumentException("'apiKey' is required");
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
