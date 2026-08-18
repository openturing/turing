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
package com.viglet.turing.plugins.se;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.solr.bean.TurSECoreInfo;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Interface for search engine plugins that can be used by Turing.
 * Implementations include Solr, Elasticsearch, and Lucene.
 *
 * <p>Each plugin owns all index lifecycle, schema, document, and monitoring
 * operations for its engine. Callers should resolve the plugin via
 * {@link TurSearchEnginePluginFactory} and call the appropriate method without
 * needing to know which engine is in use.
 *
 * @author Alexandre Oliveira
 * @since 2025.4.4
 */
public interface TurSearchEnginePlugin {

    // ---- Search (existing) -----------------------------------------------

    Optional<TurSEResults> retrieveSearchResults(TurSNSiteSearchContext context);

    Optional<TurSEResults> retrieveFacetResults(TurSNSiteSearchContext context, String facetName);

    String getPluginType();

    // ---- Index lifecycle -------------------------------------------------

    /**
     * Creates an index/core for the given SE instance.
     * Solr uses the locale language to pick the configSet; Lucene/ES ignore siteLocale.
     */
    void createIndex(TurSEInstance seInstance, TurSNSiteLocale siteLocale, String indexName,
            Map<String, TurSEFieldType> fieldTypes);

    /**
     * Creates a standalone index/core without requiring a site locale entity.
     * Uses only the language to determine the configSet (Solr) or
     * creates the index directly (Lucene/ES).
     *
     * @param seInstance the search engine instance
     * @param language   the locale language (e.g., Locale.forLanguageTag("en"))
     * @param indexName  the name for the new index/core
     */
    default void createStandaloneIndex(TurSEInstance seInstance, Locale language, String indexName) {
        TurSNSiteLocale tempLocale = new TurSNSiteLocale();
        tempLocale.setLanguage(language);
        createIndex(seInstance, tempLocale, indexName, Map.of());
    }

    void deleteIndex(TurSEInstance seInstance, String indexName);

    void clearIndex(TurSEInstance seInstance, String indexName);

    boolean indexExists(TurSEInstance seInstance, String indexName);

    List<TurSECoreInfo> listIndexes(TurSEInstance seInstance);

    // ---- Schema management (Lucene/ES are schema-less -> no-op/false) ----

    /**
     * Adds or updates a field in the index schema.
     * @param isNew true = add-field, false = replace-field (Solr only; ignored by others)
     */
    void addOrUpdateField(TurSEInstance seInstance, String indexName, String fieldName,
            TurSEFieldType fieldType, boolean stored, boolean multiValued, boolean isNew);

    void deleteField(TurSEInstance seInstance, String indexName, String fieldName,
            TurSEFieldType fieldType);

    boolean fieldExists(TurSEInstance seInstance, String indexName, String fieldName);

    /**
     * Makes an existing field multiValued, preserving its current type. No-op by
     * default (schema-less engines); Solr overrides it. Never changes the type —
     * see {@code TurSolrUtils.reconcileMultiValued}.
     */
    default void reconcileMultiValued(TurSEInstance seInstance, String indexName, String fieldName) {
        // no-op for schema-less engines (Lucene/Elasticsearch)
    }

    // ---- Document operations (plugin resolves instance via site+locale) --

    boolean indexDocument(TurSNSite turSNSite, Locale locale, Map<String, Object> attributes);

    /**
     * Indexes a batch of documents in a single round-trip to the search engine
     * when the implementation supports it (Solr/Elasticsearch). The default
     * implementation falls back to per-document indexing — engines that don't
     * support batching get the same behaviour as before, but Solr/ES override
     * this to coalesce N documents into one HTTP/update request.
     *
     * @return number of documents successfully indexed.
     * @since 2026.2.7
     */
    default int indexDocuments(TurSNSite turSNSite, Locale locale,
            List<Map<String, Object>> documents) {
        int indexed = 0;
        for (Map<String, Object> doc : documents) {
            if (indexDocument(turSNSite, locale, doc)) {
                indexed++;
            }
        }
        return indexed;
    }

    boolean deIndex(TurSNSite turSNSite, Locale locale, String id);

    boolean deIndexByType(TurSNSite turSNSite, Locale locale, String type);

    boolean commit(TurSNSite turSNSite, Locale locale);

    // ---- Copy field (Solr-specific; no-op for schema-less engines) -------

    /**
     * Creates a copy field mapping if the engine supports it.
     * Lucene and Elasticsearch ignore this call by default.
     */
    default void createCopyField(TurSEInstance seInstance, String indexName, String fieldName,
            TurSEFieldType fieldType, boolean multiValued) {
        // No-op for schema-less engines
    }

    // ---- Content retrieval ------------------------------------------------

    /**
     * Retrieves a page of documents from the index for export purposes.
     * Only returns fields defined in the SN Site field map, respecting
     * multiValued settings (single-value fields are flattened from arrays).
     *
     * @param turSNSiteLocale  the site locale to query
     * @param siteFieldMap     field definitions from TurSNSiteFieldService.toMap()
     * @param start            pagination offset
     * @param rows             number of documents per page
     * @return list of documents as maps
     */
    default List<Map<String, Object>> retrieveDocumentPage(TurSNSiteLocale turSNSiteLocale,
            Map<String, ?> siteFieldMap, int start, int rows) {
        return List.of();
    }

    // ---- Autocomplete -----------------------------------------------------

    /**
     * Returns autocomplete suggestions for the given query term.
     * Implementations should return a list of suggested terms sorted by relevance.
     *
     * @param siteName the SN site name
     * @param term     the partial query to complete
     * @param locale   the locale for the search
     * @param rows     maximum number of suggestions to return
     * @return list of autocomplete suggestions
     */
    default List<String> autoComplete(String siteName, String term, Locale locale, long rows) {
        return List.of();
    }

    // ---- Monitoring ------------------------------------------------------

    long getDocumentTotal(TurSNSiteLocale turSNSiteLocale);

    /**
     * Returns system information about the search engine instance,
     * such as version, OS, JVM info, etc.
     * Keys and values are engine-specific.
     */
    default Map<String, String> getSystemInfo(TurSEInstance seInstance) {
        return Map.of("engine", getPluginType(), "status", "UP");
    }
}
