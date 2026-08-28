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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.contentai.TurContentAiClient;
import com.viglet.turing.contentai.TurContentAiQueryBuilder;
import com.viglet.turing.contentai.TurContentAiResponseMapper;
import com.viglet.turing.contentai.TurContentAiSource;
import com.viglet.turing.contentai.TurContentAiSourceCatalog;
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
    private final TurContentAiSourceCatalog sourceCatalog;
    private final TurContentAiProperty property;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    /**
     * T1045 — endpoints whose configured source has already been diagnosed, so the
     * zero-result check costs one listing per endpoint+source rather than one per
     * empty search. A misconfiguration is permanent until somebody changes the
     * configuration; repeating the warning would only bury it.
     */
    private final Set<String> diagnosed = ConcurrentHashMap.newKeySet();

    public TurContentAiSearchEnginePlugin(TurContentAiClient client,
            TurContentAiQueryBuilder queryBuilder, TurContentAiResponseMapper responseMapper,
            TurContentAiSourceCatalog sourceCatalog,
            TurContentAiProperty property, TurSNSiteRepository turSNSiteRepository,
            TurSNSiteLocaleRepository turSNSiteLocaleRepository,
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository) {
        this.client = client;
        this.queryBuilder = queryBuilder;
        this.responseMapper = responseMapper;
        this.sourceCatalog = sourceCatalog;
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

            if (numFound == 0) {
                diagnoseEmptyResult(target, facetFields);
            }

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

    // ---- Configuration diagnostics (T1045) --------------------------------

    /**
     * T1045 / §XX.22 — says why a search found nothing, once per endpoint+source.
     *
     * <p>Adobe answers a query naming a source it does not have with
     * {@code totalResults: 0}, which is also what a source that exists and matched
     * nothing answers. The two are opposite facts and the plugin could not tell
     * them apart, so a typo in {@code turing.content-ai.sources.<site>} looked
     * exactly like an empty index. {@code GET /content-sources} separates them, and
     * while it is being read the rest of the configuration is worth checking
     * against the same response — the source's type, the space names, and whether
     * the site's facet fields are filterable at all, each of which fails the same
     * silent way.
     *
     * <p>It runs on an empty result rather than on every search, and at most once
     * per endpoint+source: a misconfiguration is permanent until the configuration
     * changes, so repeating the warning would bury it. A failure to reach the
     * listing is logged and forgotten — a diagnostic that throws would turn a
     * search returning nothing into a search returning an error.
     */
    private void diagnoseEmptyResult(Target target, List<TurSNSiteFieldExt> facetFields) {
        String key = target.endpointUrl() + "\0" + target.sourceName();
        if (!diagnosed.add(key)) {
            return;
        }
        try {
            List<TurContentAiSource> available =
                    sourceCatalog.sources(target.endpointUrl(), property);
            Optional<TurContentAiSource> resolved = available.stream()
                    .filter(source -> target.sourceName().equals(source.name()))
                    .findFirst();

            if (resolved.isEmpty()) {
                log.warn("Content AI site '{}' searches source '{}', which this tenant does not "
                        + "have — an empty result set here is a configuration error, not an empty "
                        + "index. Available sources: {}. Set turing.content-ai.sources.{} to one "
                        + "of them.", target.site().getName(), target.sourceName(),
                        available.stream().map(TurContentAiSource::name).toList(),
                        target.site().getName());
                return;
            }
            warnOnConfigurationMismatch(target, resolved.get(), facetFields);
        } catch (TurContentAiClient.TurContentAiException e) {
            // Allow a retry: the listing failing is not evidence about the source.
            diagnosed.remove(key);
            log.warn("Content AI site '{}' returned no results and the content-source listing "
                    + "could not be read to say why: {}", target.site().getName(), e.getMessage());
        }
    }

    /**
     * The three other ways the configuration can be wrong while every request
     * still succeeds: a source type that does not match the source, a space name
     * the source's IndexConfig does not declare (Adobe silently uses its default),
     * and a Turing facet field the index will not filter on (Adobe returns an empty
     * bucket list, so the facet renders as having no values).
     */
    private void warnOnConfigurationMismatch(Target target, TurContentAiSource source,
            List<TurSNSiteFieldExt> facetFields) {
        String site = target.site().getName();
        if (StringUtils.isNotBlank(source.type())
                && !source.type().equals(property.getSourceType())) {
            log.warn("Content AI source '{}' is of type {} but turing.content-ai.source-type is "
                    + "{}; a search names the type and resolves against the wrong index",
                    source.name(), source.type(), property.getSourceType());
        }
        warnUnknownSpace(source.name(), "vector-space", property.getVectorSpace(),
                source.vectorSpaces());
        warnUnknownSpace(source.name(), "lexical-space", property.getLexicalSpace(),
                source.lexicalSpaces());

        if (!source.filterFields().isEmpty()) {
            List<String> notFilterable = facetFields.stream()
                    .map(TurSNSiteFieldExt::getName)
                    .filter(name -> !source.filterFields().contains(name))
                    .toList();
            if (!notFilterable.isEmpty()) {
                log.warn("Content AI source '{}' does not filter on {}, so those facets of site "
                        + "'{}' will always render with no values. Filterable fields: {}",
                        source.name(), notFilterable, site, source.filterFields());
            }
        }
    }

    private void warnUnknownSpace(String sourceName, String setting, String configured,
            List<String> declared) {
        if (StringUtils.isBlank(configured) || declared.isEmpty()
                || declared.contains(configured)) {
            return;
        }
        log.warn("Content AI source '{}' declares no {} named '{}' (turing.content-ai.{}); Adobe "
                + "falls back to its default space, so relevance is not what was configured. "
                + "Declared: {}", sourceName, setting, configured, setting, declared);
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
        addSourceInfo(info, seInstance);
        return info;
    }

    /**
     * T1045 — what the tenant actually has, beside what the file says it should.
     * This is the read an operator makes to find out that a source name is wrong,
     * so the two are reported together and never merged: {@code sources} is
     * Adobe's answer, {@code sourceType} is the file's.
     */
    private void addSourceInfo(Map<String, String> info, TurSEInstance seInstance) {
        info.put("sourceType", property.getSourceType());
        if (!property.isEnabled() || !credentialConfigured() || seInstance == null) {
            return;
        }
        try {
            List<TurContentAiSource> sources =
                    sourceCatalog.sources(seInstance.getEndpointUrl(), property);
            info.put("sources", sources.stream().map(TurContentAiSource::name)
                    .collect(java.util.stream.Collectors.joining(", ")));
            info.put("sourceCount", String.valueOf(sources.size()));
            property.getSources().forEach((site, sourceName) -> {
                boolean resolves = sources.stream()
                        .anyMatch(source -> sourceName.equals(source.name()));
                info.put("source." + site, sourceName + (resolves ? "" : " (NOT FOUND)"));
            });
        } catch (TurContentAiClient.TurContentAiException e) {
            info.put("sources", "unavailable: " + e.getMessage());
        }
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
     * T1045 / §XX.22 — asks Adobe, rather than answering from the configuration
     * file.
     *
     * <p>This used to return "the plugin is enabled and has a credential", which is
     * a fact about {@code application.yaml} and says nothing about whether the
     * source exists. It now resolves {@code indexName} against
     * {@code GET /content-sources}, so a name that resolves to nothing is
     * reportable rather than inferable from an empty result set.
     *
     * <p>A listing that cannot be read is <em>not</em> reported as absent: the
     * caller would read that as "no such source" when the truth is "Adobe could
     * not be reached", so an unreachable tenant falls back to the old
     * configuration-level answer with a warning.
     */
    @Override
    public boolean indexExists(TurSEInstance seInstance, String indexName) {
        if (!property.isEnabled() || !credentialConfigured() || seInstance == null) {
            return false;
        }
        try {
            return sourceCatalog.find(seInstance.getEndpointUrl(), indexName, property).isPresent();
        } catch (TurContentAiClient.TurContentAiException e) {
            log.warn("Content AI content-source listing at {} could not be read, so whether '{}' "
                    + "exists is unknown; reporting it as present rather than as missing: {}",
                    seInstance.getEndpointUrl(), indexName, e.getMessage());
            return true;
        }
    }

    /**
     * T1045 — the tenant's content sources, so an admin choosing a source name can
     * see the real ones instead of typing one.
     *
     * <p>{@code numDocs} is reported as {@code -1} rather than 0. Counting would
     * mean one search per source, and a 0 here would be read as "this source is
     * empty" — the very confusion this task exists to remove. {@code usedBySites}
     * is empty: Turing does not own these indexes and cannot say who else queries
     * them.
     */
    @Override
    public List<TurSECoreInfo> listIndexes(TurSEInstance seInstance) {
        if (!property.isEnabled() || !credentialConfigured() || seInstance == null) {
            return List.of();
        }
        try {
            return sourceCatalog.sources(seInstance.getEndpointUrl(), property).stream()
                    .map(source -> new TurSECoreInfo(source.name(), -1L, List.of()))
                    .toList();
        } catch (TurContentAiClient.TurContentAiException e) {
            log.warn("Content AI content-source listing at {} failed: {}",
                    seInstance.getEndpointUrl(), e.getMessage());
            return List.of();
        }
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
