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
package com.viglet.turing.plugins.se.contentai;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.contentai.TurContentAiClient;
import com.viglet.turing.contentai.TurContentAiQueryBuilder;
import com.viglet.turing.contentai.TurContentAiResponseMapper;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.properties.TurContentAiProperty;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.solr.bean.TurSECoreInfo;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * T1031 / §XX.18 — a {@link TurSearchEnginePlugin} over an Adobe AEM Content AI
 * index Turing does not own.
 *
 * <p>This is the other half of the two paths §XX.18 named. The first was
 * registering Adobe's MCP server as an agent tool, which needed no Turing code
 * once authenticated remote MCP worked (T1032). This one is the search path: an
 * SN site whose SE vendor is {@code CONTENTAI} queries Adobe's own vector index
 * instead of a Solr, Elasticsearch or Lucene index Turing filled, and the
 * customer stops paying to embed the same published AEM content twice.
 *
 * <p><b>It is read-only, and that is the design and not a gap.</b> Turing does not
 * own this index: Adobe's own acquisition service or an AEM publish instance
 * fills it, its embedding model is Adobe's, and its schema is declared in an
 * IndexConfig on Adobe's side. So every write, schema and lifecycle method
 * throws with a sentence saying so rather than silently succeeding — a
 * {@code createIndex} that no-opped would let the indexer report a successful
 * publish into a store that never received it, which is worse than a failure.
 * The one exception is {@link #commit}, which returns {@code true}: nothing was
 * written, so there is nothing to flush, and the SN pipeline calls it
 * unconditionally.
 *
 * <p><b>Paging is a cursor, and deep pages cost round trips.</b> Adobe's
 * pagination has no offset — only an opaque cursor and a {@code limit} capped at
 * 50 — so page N is reached by asking N times. That is bounded by
 * {@code turing.content-ai.max-page-walk}; past it the plugin returns an empty
 * page carrying the true {@code numFound} rather than walking indefinitely,
 * because the alternative is a search UI whose "last page" link quietly issues
 * hundreds of requests to a metered API.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurContentAiSearchEnginePlugin implements TurSearchEnginePlugin {

    public static final String PLUGIN_TYPE = "contentai";

    private static final int DEFAULT_ROWS = 10;

    private static final String READ_ONLY = "Content AI is a federated, read-only index: "
            + "Adobe owns its schema, its embeddings and its ingestion. %s is not available on "
            + "the '" + PLUGIN_TYPE + "' plugin — index the content in Adobe (AEM publish or the "
            + "Content AI acquisition service) and let Turing query it.";

    private final TurContentAiClient client;
    private final TurContentAiQueryBuilder queryBuilder;
    private final TurContentAiResponseMapper responseMapper;
    private final TurContentAiProperty property;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    public TurContentAiSearchEnginePlugin(TurContentAiClient client,
            TurContentAiQueryBuilder queryBuilder, TurContentAiResponseMapper responseMapper,
            TurContentAiProperty property, TurSNSiteRepository turSNSiteRepository,
            TurSNSiteLocaleRepository turSNSiteLocaleRepository,
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository) {
        this.client = client;
        this.queryBuilder = queryBuilder;
        this.responseMapper = responseMapper;
        this.property = property;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
    }

    @Override
    public String getPluginType() {
        return PLUGIN_TYPE;
    }

    /**
     * T1044 / §XX.21 — Adobe embedded this content with its own chunker, so the
     * passages it returns are the only sub-document grounding that exists for a
     * federated site. True only when the deployment asked for them: the chunk text
     * rides on the same response either way, but a plugin claiming the capability
     * while the request omits {@code chunks.includeInResponse} would promise
     * passages that never arrive.
     */
    @Override
    public boolean supportsPassages() {
        return property.isEnabled() && property.isIncludeChunks();
    }

    // ---- Search ----------------------------------------------------------

    @Override
    public Optional<TurSEResults> retrieveSearchResults(TurSNSiteSearchContext context) {
        if (context == null) {
            return Optional.empty();
        }
        return target(context.getSiteName(), context.getLocale())
                .flatMap(t -> search(t, context.getTurSEParameters()));
    }

    private Optional<TurSEResults> search(Target target, TurSEParameters params) {
        List<TurSNSiteFieldExt> facetFields = facetFields(target.site());
        int rows = rows(params);
        int page = page(params);

        try {
            PagedResponse paged = walkToPage(target, params, facetFields, rows, page);
            JsonNode response = paged.response();
            long numFound = responseMapper.totalResults(response);
            List<TurSEResult> results = paged.reached()
                    ? responseMapper.results(response)
                    : Collections.emptyList();
            List<TurSEFacetResult> facetResults =
                    responseMapper.facetResults(response, facetFields);

            return Optional.of(TurSEResults.builder()
                    .numFound(numFound)
                    .start((long) (page - 1) * rows)
                    .limit(rows)
                    .pageCount((int) Math.ceil((double) numFound / rows))
                    .currentPage(page)
                    .results(results)
                    .qTime(0)
                    .elapsedTime(0L)
                    .queryString(params != null ? params.getQuery() : null)
                    .sort(params != null ? params.getSort() : null)
                    .spellCheck(null)
                    .similarResults(Collections.emptyList())
                    .facetResults(facetResults)
                    .groups(Collections.emptyList())
                    .build());
        } catch (TurContentAiClient.TurContentAiException e) {
            log.error("Content AI search failed for site '{}' source '{}'", target.site().getName(),
                    target.sourceName(), e);
            return Optional.empty();
        }
    }

    /**
     * Walks the cursor chain to the requested page. Page 1 is one request; page N
     * is N, since the API offers no offset. {@link PagedResponse#reached()} is
     * {@code false} when the walk ran out of cursors (the page is past the end)
     * or hit {@code max-page-walk} — in both cases the last response still
     * carries the honest total, which is why it is returned rather than dropped.
     */
    private PagedResponse walkToPage(Target target, TurSEParameters params,
            List<TurSNSiteFieldExt> facetFields, int rows, int page) {
        int walkLimit = Math.max(1, property.getMaxPageWalk());
        if (page > walkLimit) {
            log.warn("Content AI page {} for site '{}' exceeds turing.content-ai.max-page-walk={}; "
                    + "returning an empty page with the true total rather than {} round trips",
                    page, target.site().getName(), walkLimit, page);
            JsonNode first = client.search(target.endpointUrl(), queryBuilder.buildSearchRequest(
                    target.sourceName(), target.site(), params, facetFields, null, rows, property),
                    property);
            return new PagedResponse(first, false);
        }

        String cursor = null;
        JsonNode response = null;
        for (int current = 1; current <= page; current++) {
            response = client.search(target.endpointUrl(), queryBuilder.buildSearchRequest(
                    target.sourceName(), target.site(), params, facetFields, cursor, rows,
                    property), property);
            if (current == page) {
                return new PagedResponse(response, true);
            }
            cursor = responseMapper.cursor(response);
            if (cursor == null) {
                log.debug("Content AI ran out of cursors at page {} of {} for site '{}'", current,
                        page, target.site().getName());
                return new PagedResponse(response, false);
            }
        }
        return new PagedResponse(response, false);
    }

    private record PagedResponse(JsonNode response, boolean reached) {
    }

    @Override
    public Optional<TurSEResults> retrieveFacetResults(TurSNSiteSearchContext context,
            String facetName) {
        if (context == null || StringUtils.isBlank(facetName)) {
            return Optional.empty();
        }
        return target(context.getSiteName(), context.getLocale()).flatMap(target -> {
            TurSNSiteFieldExt facetField = facetFields(target.site()).stream()
                    .filter(field -> facetName.equals(field.getName()))
                    .findFirst()
                    .orElse(null);
            try {
                JsonNode response = client.search(target.endpointUrl(),
                        queryBuilder.buildFacetRequest(target.sourceName(), target.site(),
                                context.getTurSEParameters(), facetName, facetField, property),
                        property);
                return Optional.of(TurSEResults.builder()
                        .numFound(responseMapper.totalResults(response))
                        .results(Collections.emptyList())
                        .facetResults(List.of(responseMapper.facetResult(response, facetName)))
                        .similarResults(Collections.emptyList())
                        .groups(Collections.emptyList())
                        .build());
            } catch (TurContentAiClient.TurContentAiException e) {
                log.error("Content AI facet '{}' failed for site '{}'", facetName,
                        target.site().getName(), e);
                return Optional.empty();
            }
        });
    }

    // ---- Monitoring ------------------------------------------------------

    @Override
    public long getDocumentTotal(TurSNSiteLocale turSNSiteLocale) {
        if (turSNSiteLocale == null || turSNSiteLocale.getTurSNSite() == null) {
            return 0L;
        }
        TurSNSite site = turSNSiteLocale.getTurSNSite();
        try {
            JsonNode response = client.search(endpointUrl(site),
                    queryBuilder.buildCountRequest(sourceName(site, turSNSiteLocale), property),
                    property);
            return responseMapper.totalResults(response);
        } catch (TurContentAiClient.TurContentAiException e) {
            log.error("Content AI document count failed for site '{}'", site.getName(), e);
            return 0L;
        }
    }

    @Override
    public Map<String, String> getSystemInfo(TurSEInstance seInstance) {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("engine", PLUGIN_TYPE);
        info.put("vendor", "Adobe AEM Content AI");
        info.put("mode", "federated (read-only)");
        info.put("endpoint", seInstance != null ? seInstance.getEndpointUrl() : "");
        info.put("credential", credentialConfigured() ? "configured" : "missing");
        info.put("hybrid", String.valueOf(property.isHybrid()));
        info.put("maxLimit", String.valueOf(TurContentAiQueryBuilder.MAX_LIMIT));
        info.put("status", property.isEnabled() && credentialConfigured() ? "UP" : "DOWN");
        return info;
    }

    private boolean credentialConfigured() {
        return StringUtils.isNotBlank(property.getToken())
                || StringUtils.isNotBlank(property.getApiKey());
    }

    // ---- Resolution ------------------------------------------------------

    /**
     * Resolves (site name, locale) to the endpoint and content source to query.
     * Empty when the plugin is off, the site is unknown, or the site has no such
     * locale — each logged once at the level that matches how surprising it is.
     */
    private Optional<Target> target(String siteName, Locale locale) {
        if (!property.isEnabled()) {
            log.warn("A site is configured for the '{}' engine but turing.content-ai.enabled is "
                    + "false; refusing to query Adobe", PLUGIN_TYPE);
            return Optional.empty();
        }
        return turSNSiteRepository.findByNameIgnoreCase(siteName).flatMap(site -> {
            TurSNSiteLocale siteLocale =
                    turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(site, locale);
            if (siteLocale == null) {
                log.warn("Site '{}' has no '{}' locale", siteName, locale);
                return Optional.empty();
            }
            return Optional.of(new Target(site, endpointUrl(site), sourceName(site, siteLocale)));
        });
    }

    private String endpointUrl(TurSNSite site) {
        return Optional.ofNullable(site.getTurSEInstance())
                .map(TurSEInstance::getEndpointUrl)
                .orElse("");
    }

    /**
     * The Adobe content source for this site: the configured mapping if there is
     * one, else the (site, locale) core name — which is what a source created to
     * mirror a Turing site would be named.
     */
    private String sourceName(TurSNSite site, TurSNSiteLocale siteLocale) {
        String configured = property.getSources().get(site.getName());
        return StringUtils.isNotBlank(configured) ? configured : siteLocale.getCore();
    }

    private List<TurSNSiteFieldExt> facetFields(TurSNSite site) {
        return turSNSiteFieldExtRepository
                .findByTurSNSiteAndFacetAndEnabledOrderByFacetPosition(site, 1, 1);
    }

    private int rows(TurSEParameters params) {
        Integer requested = params != null ? params.getRows() : null;
        int rows = requested != null && requested > 0 ? requested : DEFAULT_ROWS;
        return queryBuilder.clampLimit(rows);
    }

    private int page(TurSEParameters params) {
        Integer requested = params != null ? params.getCurrentPage() : null;
        return requested != null && requested > 0 ? requested : 1;
    }

    private record Target(TurSNSite site, String endpointUrl, String sourceName) {
    }

    // ---- Everything Turing does not own ----------------------------------

    @Override
    public void createIndex(TurSEInstance seInstance, TurSNSiteLocale siteLocale, String indexName,
            Map<String, TurSEFieldType> fieldTypes) {
        throw readOnly("createIndex");
    }

    @Override
    public void deleteIndex(TurSEInstance seInstance, String indexName) {
        throw readOnly("deleteIndex");
    }

    @Override
    public void clearIndex(TurSEInstance seInstance, String indexName) {
        throw readOnly("clearIndex");
    }

    /**
     * Answered rather than refused: a caller asking whether the source exists is
     * making a read, and the honest answer is that a configured, credentialled
     * plugin can reach one. It does not probe — {@code /content-sources/{name}}
     * would, and the callers of this method use it as a create-if-absent guard
     * whose create arm this plugin refuses anyway.
     */
    @Override
    public boolean indexExists(TurSEInstance seInstance, String indexName) {
        return property.isEnabled() && credentialConfigured();
    }

    @Override
    public List<TurSECoreInfo> listIndexes(TurSEInstance seInstance) {
        return List.of();
    }

    @Override
    public void addOrUpdateField(TurSEInstance seInstance, String indexName, String fieldName,
            TurSEFieldType fieldType, boolean stored, boolean multiValued, boolean isNew) {
        throw readOnly("addOrUpdateField");
    }

    @Override
    public void deleteField(TurSEInstance seInstance, String indexName, String fieldName,
            TurSEFieldType fieldType) {
        throw readOnly("deleteField");
    }

    @Override
    public boolean fieldExists(TurSEInstance seInstance, String indexName, String fieldName) {
        return false;
    }

    @Override
    public boolean indexDocument(TurSNSite turSNSite, Locale locale,
            Map<String, Object> attributes) {
        throw readOnly("indexDocument");
    }

    @Override
    public boolean deIndex(TurSNSite turSNSite, Locale locale, String id) {
        throw readOnly("deIndex");
    }

    @Override
    public boolean deIndexByType(TurSNSite turSNSite, Locale locale, String type) {
        throw readOnly("deIndexByType");
    }

    /**
     * Nothing was written, so there is nothing to flush. Returns {@code true}
     * because the SN pipeline commits unconditionally and a refusal here would
     * turn every read-only site's request into an error.
     */
    @Override
    public boolean commit(TurSNSite turSNSite, Locale locale) {
        return true;
    }

    private static UnsupportedOperationException readOnly(String operation) {
        return new UnsupportedOperationException(READ_ONLY.formatted(operation));
    }
}
