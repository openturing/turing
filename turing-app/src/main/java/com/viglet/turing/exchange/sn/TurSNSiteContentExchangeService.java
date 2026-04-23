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

package com.viglet.turing.exchange.sn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.viglet.turing.genai.TurSNGenAi;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.sn.field.TurSNSiteFieldService;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for exporting and importing indexed content from SN sites.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Slf4j
@Service
public class TurSNSiteContentExchangeService {

    private static final int EXPORT_PAGE_SIZE = 500;
    public static final int IMPORT_CHUNK_SIZE = 50;

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSearchEnginePluginFactory pluginFactory;
    private final TurSNSiteFieldService turSNSiteFieldService;
    private final TurSNGenAi turSNGenAi;

    private final ConcurrentHashMap<String, ContentExchangeProgress> progressMap = new ConcurrentHashMap<>();

    public record ContentExchangeProgress(int totalDocuments, int processedDocuments,
            String currentLocale, String phase, long startTimeMillis) {

        public int percentage() {
            return totalDocuments == 0 ? 0
                    : Math.min(100, (int) ((processedDocuments * 100L) / totalDocuments));
        }

        public long estimatedRemainingMillis() {
            if (processedDocuments == 0) {
                return -1;
            }
            long elapsed = System.currentTimeMillis() - startTimeMillis;
            double docsPerMs = (double) processedDocuments / elapsed;
            int remaining = totalDocuments - processedDocuments;
            return (long) (remaining / docsPerMs);
        }
    }

    public TurSNSiteContentExchangeService(TurSNSiteRepository turSNSiteRepository,
            TurSNSiteLocaleRepository turSNSiteLocaleRepository,
            TurSearchEnginePluginFactory pluginFactory,
            TurSNSiteFieldService turSNSiteFieldService,
            TurSNGenAi turSNGenAi) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.pluginFactory = pluginFactory;
        this.turSNSiteFieldService = turSNSiteFieldService;
        this.turSNGenAi = turSNGenAi;
    }

    public Optional<ContentExchangeProgress> getProgress(String taskId) {
        return Optional.ofNullable(progressMap.get(taskId));
    }

    public void removeProgress(String taskId) {
        progressMap.remove(taskId);
    }

    public void updatePhase(String taskId, String phase) {
        var current = progressMap.get(taskId);
        if (current != null) {
            progressMap.put(taskId, new ContentExchangeProgress(
                    current.totalDocuments(), current.processedDocuments(),
                    current.currentLocale(), phase, current.startTimeMillis()));
        } else {
            progressMap.put(taskId, new ContentExchangeProgress(
                    0, 0, "", phase, System.currentTimeMillis()));
        }
    }

    /**
     * Exports all indexed content for a site, organized by locale.
     * Structure: { "siteName": { "en_US": [ {doc1}, {doc2}, ... ], "pt_BR": [...] } }
     */
    public Map<String, Map<String, List<Map<String, Object>>>> exportContent(String siteId, String taskId) {
        return turSNSiteRepository.findById(siteId).map(turSNSite -> {
            Map<String, Map<String, List<Map<String, Object>>>> contentMap = new LinkedHashMap<>();
            List<TurSNSiteLocale> locales = turSNSiteLocaleRepository.findByTurSNSite(turSNSite);
            TurSearchEnginePlugin plugin = pluginFactory.getPluginForSite(turSNSite);
            Map<String, TurSNSiteField> siteFieldMap = turSNSiteFieldService.toMap(turSNSite);

            long totalDocs = 0;
            log.info("Exporting content for site '{}': plugin={}, locales={}, siteFields={}",
                    turSNSite.getName(), plugin.getClass().getSimpleName(), locales.size(), siteFieldMap.size());
            for (TurSNSiteLocale locale : locales) {
                long localeCount = plugin.getDocumentTotal(locale);
                log.info("  Locale '{}' (core='{}'): {} documents", locale.getLanguage(), locale.getCore(), localeCount);
                totalDocs += localeCount;
            }

            progressMap.put(taskId, new ContentExchangeProgress(
                    (int) totalDocs, 0, "", "counting", System.currentTimeMillis()));

            Map<String, List<Map<String, Object>>> localeContent = new LinkedHashMap<>();
            int processedSoFar = 0;
            long startTime = System.currentTimeMillis();

            for (TurSNSiteLocale locale : locales) {
                String localeKey = locale.getLanguage().toString();
                List<Map<String, Object>> documents = new ArrayList<>();

                progressMap.put(taskId, new ContentExchangeProgress(
                        (int) totalDocs, processedSoFar, localeKey, "exporting", startTime));

                int start = 0;
                boolean hasMore = true;
                while (hasMore) {
                    List<Map<String, Object>> page = plugin.retrieveDocumentPage(locale, siteFieldMap, start, EXPORT_PAGE_SIZE);
                    documents.addAll(page);
                    processedSoFar += page.size();

                    progressMap.put(taskId, new ContentExchangeProgress(
                            (int) totalDocs, processedSoFar, localeKey, "exporting", startTime));

                    start += EXPORT_PAGE_SIZE;
                    hasMore = page.size() == EXPORT_PAGE_SIZE;
                }
                localeContent.put(localeKey, documents);
            }
            contentMap.put(turSNSite.getName(), localeContent);

            return contentMap;
        }).orElse(Map.of());
    }

    /**
     * Imports content from the content map, indexing documents in chunks of 50.
     * Returns total documents indexed.
     */
    public int importContent(TurSNSite turSNSite,
            Map<String, Map<String, List<Map<String, Object>>>> contentMap, String taskId) {
        int totalIndexed = 0;

        for (var siteEntry : contentMap.entrySet()) {
            Map<String, List<Map<String, Object>>> localeContent = siteEntry.getValue();
            int totalDocs = localeContent.values().stream().mapToInt(List::size).sum();
            long startTime = System.currentTimeMillis();
            int processedSoFar = 0;

            progressMap.put(taskId, new ContentExchangeProgress(
                    totalDocs, 0, "", "indexing", startTime));

            TurSearchEnginePlugin plugin = pluginFactory.getPluginForSite(turSNSite);

            for (var localeEntry : localeContent.entrySet()) {
                Locale locale = Locale.forLanguageTag(localeEntry.getKey().replace("_", "-"));
                List<Map<String, Object>> documents = localeEntry.getValue();

                progressMap.put(taskId, new ContentExchangeProgress(
                        totalDocs, processedSoFar, localeEntry.getKey(), "indexing", startTime));

                for (int i = 0; i < documents.size(); i += IMPORT_CHUNK_SIZE) {
                    int end = Math.min(i + IMPORT_CHUNK_SIZE, documents.size());
                    List<Map<String, Object>> chunk = documents.subList(i, end);

                    for (Map<String, Object> doc : chunk) {
                        try {
                            if (plugin.indexDocument(turSNSite, locale, doc)) {
                                totalIndexed++;
                            }
                        } catch (Exception e) {
                            log.warn("Failed to index document '{}': {}",
                                    doc.getOrDefault("id", "unknown"), e.getMessage());
                        }
                        try {
                            turSNGenAi.addDocument(turSNSite, locale, doc);
                        } catch (Exception e) {
                            log.warn("Failed to index document '{}' into vector store: {}",
                                    doc.getOrDefault("id", "unknown"), e.getMessage());
                        }
                        processedSoFar++;
                    }
                    plugin.commit(turSNSite, locale);

                    progressMap.put(taskId, new ContentExchangeProgress(
                            totalDocs, processedSoFar, localeEntry.getKey(), "indexing", startTime));
                }
            }

            progressMap.put(taskId, new ContentExchangeProgress(
                    totalDocs, processedSoFar, "", "completed", startTime));
        }
        return totalIndexed;
    }
}
