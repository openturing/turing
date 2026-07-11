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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.LocaleUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentBean;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.properties.TurMigrationProperty;
import com.viglet.turing.sn.TurSNSearchProcess;
import com.viglet.turing.sn.migration.TurAlgoliaClient.TurAlgoliaConnection;
import com.viglet.turing.sn.migration.TurElasticsearchClient.TurEsConnection;
import com.viglet.turing.sn.migration.TurMigrationCompareMetrics.Aggregate;
import com.viglet.turing.sn.migration.TurMigrationCompareMetrics.QueryComparison;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Shadow / dual-run relevance comparison (T661 / §XXXVIII.5).
 *
 * <p>Runs a query set against both the source engine (Elasticsearch/Algolia, via
 * the migration REST clients) and the migrated Turing SN site (via the same
 * internal search path an end user hits, {@link TurSNSearchProcess#search}), then
 * diffs the top-N result sets with the pure {@link TurMigrationCompareMetrics}. The
 * report lets a team measure relevance parity before flipping traffic. Read-only on
 * both sides.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TurMigrationCompareService {

    private static final int DEFAULT_ROWS = 10;
    private static final String ELASTICSEARCH = "elasticsearch";
    private static final String ALGOLIA = "algolia";

    private final TurElasticsearchClient elasticsearchClient;
    private final TurAlgoliaClient algoliaClient;
    private final TurSNSearchProcess turSNSearchProcess;
    private final TurMigrationProperty migrationProperty;

    public TurMigrationCompareResult compare(TurMigrationCompareRequest request) {
        String engine = validate(request);
        int rows = request.rows() != null && request.rows() > 0 ? request.rows() : DEFAULT_ROWS;
        Locale locale = parseLocale(request.locale());
        List<String> warnings = new ArrayList<>();

        if (!turSNSearchProcess.existsByTurSNSiteAndLanguage(request.targetSite(), locale)) {
            throw new IllegalArgumentException("SN site '" + request.targetSite()
                    + "' has no '" + locale + "' locale to compare against");
        }

        List<QueryComparison> perQuery = new ArrayList<>();
        for (String query : request.queries()) {
            if (!StringUtils.hasText(query)) {
                continue;
            }
            List<String> sourceIds = sourceSearch(engine, request, query, rows);
            List<String> turingIds = turingSearch(request.targetSite(), locale, query, rows);
            perQuery.add(TurMigrationCompareMetrics.compareQuery(query, sourceIds, turingIds, rows));
        }

        Aggregate agg = TurMigrationCompareMetrics.aggregate(perQuery);
        log.info("[T661] Compared {} query(ies) of {} vs SN site '{}': avg Jaccard {}, {} zero-overlap",
                perQuery.size(), engine, request.targetSite(), agg.avgJaccard(), agg.zeroOverlapQueries());

        return new TurMigrationCompareResult(engine.toUpperCase(Locale.ROOT), request.targetSite(),
                perQuery.size(), rows, agg.avgJaccard(), agg.avgSourceRecall(),
                agg.topRankMatches(), agg.zeroOverlapQueries(), perQuery, warnings);
    }

    private List<String> sourceSearch(String engine, TurMigrationCompareRequest request, String query,
            int rows) {
        int timeout = migrationProperty.getTimeoutSeconds();
        if (ELASTICSEARCH.equals(engine)) {
            TurEsConnection conn = new TurEsConnection(request.sourceUrl(), request.username(),
                    request.password(), request.apiKey(), timeout);
            return elasticsearchClient.search(conn, request.index(), query, rows);
        }
        TurAlgoliaConnection conn = new TurAlgoliaConnection(request.appId(), request.apiKey(),
                request.host(), timeout);
        return algoliaClient.search(conn, request.index(), query, rows);
    }

    /** Queries Turing exactly as an end user would, returning the ordered result ids. */
    private List<String> turingSearch(String siteName, Locale locale, String query, int rows) {
        TurSNSearchParams params = new TurSNSearchParams();
        params.setQ(query);
        params.setRows(rows);
        params.setP(1);
        params.setSort("relevance");
        params.setLocale(locale);
        TurSNSiteSearchContext context = new TurSNSiteSearchContext(siteName, new TurSNConfig(),
                new TurSEParameters(params), locale, URI.create("/api/sn/" + siteName + "/search"));

        TurSNSiteSearchBean bean = turSNSearchProcess.search(context);
        List<String> ids = new ArrayList<>();
        if (bean.getResults() != null && bean.getResults().getDocument() != null) {
            for (TurSNSiteSearchDocumentBean doc : bean.getResults().getDocument()) {
                Object id = doc.getFields() == null ? null : doc.getFields().get(TurSNFieldName.ID);
                if (id != null) {
                    ids.add(id.toString());
                }
            }
        }
        return ids;
    }

    private static String validate(TurMigrationCompareRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        String engine = StringUtils.hasText(request.engine())
                ? request.engine().trim().toLowerCase(Locale.ROOT) : "";
        if (!ELASTICSEARCH.equals(engine) && !ALGOLIA.equals(engine)) {
            throw new IllegalArgumentException("'engine' must be 'elasticsearch' or 'algolia'");
        }
        if (!StringUtils.hasText(request.index())) {
            throw new IllegalArgumentException("'index' is required");
        }
        if (!StringUtils.hasText(request.targetSite())) {
            throw new IllegalArgumentException("'targetSite' is required");
        }
        if (request.queries() == null || request.queries().isEmpty()) {
            throw new IllegalArgumentException("'queries' must contain at least one query");
        }
        if (ELASTICSEARCH.equals(engine) && !StringUtils.hasText(request.sourceUrl())) {
            throw new IllegalArgumentException("'sourceUrl' is required for Elasticsearch");
        }
        if (ALGOLIA.equals(engine)
                && (!StringUtils.hasText(request.appId()) || !StringUtils.hasText(request.apiKey()))) {
            throw new IllegalArgumentException("'appId' and 'apiKey' are required for Algolia");
        }
        return engine;
    }

    private static Locale parseLocale(String code) {
        return StringUtils.hasText(code) ? LocaleUtils.toLocale(code.trim()) : Locale.US;
    }
}
