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
package com.viglet.turing.plugins.se.elasticsearch;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.elasticsearch.TurElasticsearch;
import com.viglet.turing.elasticsearch.TurElasticsearchInstanceProcess;
import com.viglet.turing.elasticsearch.TurElasticsearchUtils;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.solr.bean.TurSECoreInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Elasticsearch implementation of the search engine plugin interface.
 *
 * @author Alexandre Oliveira
 * @since 2025.4.4
 */
@Slf4j
@Component
public class TurElasticsearchSearchEnginePlugin implements TurSearchEnginePlugin {

    private final TurElasticsearch turElasticsearch;
    private final TurElasticsearchInstanceProcess turElasticsearchInstanceProcess;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    public TurElasticsearchSearchEnginePlugin(
            TurElasticsearch turElasticsearch,
            TurElasticsearchInstanceProcess turElasticsearchInstanceProcess,
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository) {
        this.turElasticsearch = turElasticsearch;
        this.turElasticsearchInstanceProcess = turElasticsearchInstanceProcess;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
    }

    @Override
    public Optional<TurSEResults> retrieveSearchResults(TurSNSiteSearchContext context) {
        return turElasticsearchInstanceProcess
                .initElasticsearchInstance(context.getSiteName(), context.getLocale())
                .flatMap(elasticsearchInstance -> turElasticsearch.retrieveElasticsearchFromSN(elasticsearchInstance, context));
    }

    @Override
    public Optional<TurSEResults> retrieveFacetResults(TurSNSiteSearchContext context, String facetName) {
        return turElasticsearchInstanceProcess
                .initElasticsearchInstance(context.getSiteName(), context.getLocale())
                .flatMap(elasticsearchInstance -> turElasticsearch.retrieveFacetElasticsearchFromSN(
                        elasticsearchInstance, context, facetName));
    }

    @Override
    public String getPluginType() {
        return "elasticsearch";
    }

    // ---- Index lifecycle -------------------------------------------------

    @Override
    public void createIndex(TurSEInstance seInstance, TurSNSiteLocale siteLocale, String indexName,
            Map<String, TurSEFieldType> fieldTypes) {
        Map<String, TurSEFieldType> resolved = fieldTypes.isEmpty()
                ? buildFieldTypesFromSite(siteLocale)
                : fieldTypes;
        TurElasticsearchUtils.createIndex(seInstance.getEndpointUrl(), indexName, resolved);
    }

    private Map<String, TurSEFieldType> buildFieldTypesFromSite(TurSNSiteLocale siteLocale) {
        if (siteLocale == null || siteLocale.getTurSNSite() == null) {
            return Collections.emptyMap();
        }
        return turSNSiteFieldExtRepository
                .findByTurSNSiteAndEnabled(siteLocale.getTurSNSite(), 1)
                .stream()
                .filter(f -> f.getType() != null)
                .collect(Collectors.toMap(
                        TurSNSiteFieldExt::getName,
                        TurSNSiteFieldExt::getType,
                        (a, b) -> a));
    }

    @Override
    public void deleteIndex(TurSEInstance seInstance, String indexName) {
        TurElasticsearchUtils.deleteIndex(seInstance.getEndpointUrl(), indexName);
    }

    @Override
    public void clearIndex(TurSEInstance seInstance, String indexName) {
        // ES: delete and recreate the index
        TurElasticsearchUtils.deleteIndex(seInstance.getEndpointUrl(), indexName);
        TurElasticsearchUtils.createIndex(seInstance.getEndpointUrl(), indexName);
    }

    @Override
    public boolean indexExists(TurSEInstance seInstance, String indexName) {
        // ES auto-creates indices on first index
        return true;
    }

    @Override
    public List<TurSECoreInfo> listIndexes(TurSEInstance seInstance) {
        return TurElasticsearchUtils.listIndexes(seInstance.getEndpointUrl());
    }

    // ---- Schema management (schema-less -> no-op) ------------------------

    @Override
    public void addOrUpdateField(TurSEInstance seInstance, String indexName, String fieldName,
            TurSEFieldType fieldType, boolean stored, boolean multiValued, boolean isNew) {
        // Elasticsearch is schema-less — field types are tracked in the JPA model only
    }

    @Override
    public void deleteField(TurSEInstance seInstance, String indexName, String fieldName,
            TurSEFieldType fieldType) {
        // Elasticsearch is schema-less — no schema operation needed
    }

    @Override
    public boolean fieldExists(TurSEInstance seInstance, String indexName, String fieldName) {
        return false; // Elasticsearch is schema-less
    }

    // ---- Document operations ---------------------------------------------

    @Override
    public boolean indexDocument(TurSNSite turSNSite, Locale locale, Map<String, Object> attributes) {
        return turElasticsearchInstanceProcess
                .initElasticsearchInstance(turSNSite.getName(), locale)
                .map(esInstance -> {
                    turElasticsearch.indexing(esInstance, turSNSite, attributes);
                    return true;
                }).orElse(false);
    }

    @Override
    public boolean deIndex(TurSNSite turSNSite, Locale locale, String id) {
        return turElasticsearchInstanceProcess
                .initElasticsearchInstance(turSNSite.getName(), locale)
                .map(esInstance -> {
                    turElasticsearch.deIndexing(esInstance, id);
                    return true;
                }).orElse(false);
    }

    @Override
    public boolean deIndexByType(TurSNSite turSNSite, Locale locale, String type) {
        return turElasticsearchInstanceProcess
                .initElasticsearchInstance(turSNSite.getName(), locale)
                .map(esInstance -> {
                    turElasticsearch.deIndexingByType(esInstance, type);
                    return true;
                }).orElse(false);
    }

    @Override
    public boolean commit(TurSNSite turSNSite, Locale locale) {
        // Elasticsearch commits are synchronous — nothing extra needed
        return true;
    }

    // ---- Monitoring ------------------------------------------------------

    @Override
    public long getDocumentTotal(TurSNSiteLocale turSNSiteLocale) {
        return turElasticsearchInstanceProcess.initElasticsearchInstance(turSNSiteLocale)
                .map(turElasticsearch::getDocumentTotal).orElse(0L);
    }

    // ---- System info -----------------------------------------------------

    @Override
    public Map<String, String> getSystemInfo(TurSEInstance seInstance) {
        var info = new LinkedHashMap<String, String>();
        info.put("engine", "Elasticsearch");
        try {
            String baseUrl = seInstance.getEndpointUrl();
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(baseUrl))
                    .GET().build();
            var response = java.net.http.HttpClient.newHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var root = mapper.readTree(response.body());
            var version = root.path("version");
            if (!version.isMissingNode()) {
                info.put("version", version.path("number").asText(""));
                info.put("luceneVersion", version.path("lucene_version").asText(""));
                info.put("buildType", version.path("build_type").asText(""));
            }
            info.put("clusterName", root.path("cluster_name").asText(""));
            info.put("status", "UP");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Failed to retrieve Elasticsearch system info: {}", e.getMessage());
            info.put("status", "DOWN");
            info.put("error", e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to retrieve Elasticsearch system info: {}", e.getMessage());
            info.put("status", "DOWN");
            info.put("error", e.getMessage());
        }
        return info;
    }
}
