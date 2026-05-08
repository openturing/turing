/*
 * Copyright (C) 2016-2022 the original author or authors.
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

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.viglet.turing.api.sn.bean.TurSNSiteFilterQueryBean;
import com.viglet.turing.commons.se.similar.TurSESimilarResult;
import com.viglet.turing.commons.sn.bean.TurSNSiteLocaleBean;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchGroupBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchWidgetBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSpotlightDocumentBean;
import com.viglet.turing.commons.sn.bean.spellcheck.TurSNSiteSpellCheckBean;
import com.viglet.turing.commons.sn.search.TurSNParamType;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.domain.sn.TurSNSiteMetricAccessRepositoryPort;
import com.viglet.turing.domain.sn.TurSNSiteMetricAccessTermDomain;
import com.viglet.turing.observability.TurMeterNames;
import com.viglet.turing.observability.TurSearchPipelineObservation;
import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.model.sn.metric.TurSNSiteMetricAccess;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.sn.document.TurSNDocumentResponse;
import com.viglet.turing.sn.facet.TurSNFacetDefinition;
import com.viglet.turing.sn.facet.TurSNFacetDefinitionFactory;
import com.viglet.turing.sn.facet.TurSNFacetRenderer;
import com.viglet.turing.sn.facet.TurSNFacetTypeContext;
import com.viglet.turing.sn.pagination.TurSNPaginationBuilder;
import com.viglet.turing.sn.querycontext.TurSNQueryContextBuilder;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshot;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshotService;
import com.viglet.turing.sn.spotlight.TurSNSpotlightProcess;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import com.viglet.turing.solr.TurSolrQueryBuilder;

/**
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
@Component
public class TurSNSearchProcess {
        public static final String GROUP = "group";
        public static final String LANGUAGE = "language";
        private final TurSNSiteSearchSnapshotService snapshotService;
        private final TurSolrInstanceProcess turSolrInstanceProcess;
        private final TurSNSpotlightProcess turSNSpotlightProcess;
        private final TurSNSiteMetricAccessRepository turSNSiteMetricAccessRepository;
        private final TurSNSiteMetricAccessRepositoryPort turSNSiteMetricAccessRepositoryPort;
        private final boolean metricsEnabled;
        private final TurSearchEnginePluginFactory searchEnginePluginFactory;
        private final TurSolrQueryBuilder turSolrQueryBuilder;
        private final TurSNFacetDefinitionFactory turSNFacetDefinitionFactory;
        private final TurSNFacetRenderer facetRenderer;
        private final TurSNPaginationBuilder paginationBuilder;
        private final TurSNDocumentResponse documentResponse;
        private final TurSNQueryContextBuilder queryContextBuilder;
        private final TurSearchPipelineObservation pipelineObservation;

        public TurSNSearchProcess(TurSNSiteSearchSnapshotService snapshotService,
                        TurSolrInstanceProcess turSolrInstanceProcess,
                        TurSNSpotlightProcess turSNSpotlightProcess,
                        TurSNSiteMetricAccessRepository turSNSiteMetricAccessRepository,
                        TurSNSiteMetricAccessRepositoryPort turSNSiteMetricAccessRepositoryPort,
                        @Value("${turing.search.metrics.enabled:false}") boolean metricsEnabled,
                        TurSearchEnginePluginFactory searchEnginePluginFactory,
                        TurSolrQueryBuilder turSolrQueryBuilder,
                        TurSNFacetDefinitionFactory turSNFacetDefinitionFactory,
                        TurSNFacetRenderer facetRenderer,
                        TurSNPaginationBuilder paginationBuilder,
                        TurSNDocumentResponse documentResponse,
                        TurSNQueryContextBuilder queryContextBuilder,
                        TurSearchPipelineObservation pipelineObservation) {
                this.snapshotService = snapshotService;
                this.turSolrInstanceProcess = turSolrInstanceProcess;
                this.turSNSpotlightProcess = turSNSpotlightProcess;
                this.turSNSiteMetricAccessRepository = turSNSiteMetricAccessRepository;
                this.turSNSiteMetricAccessRepositoryPort = turSNSiteMetricAccessRepositoryPort;
                this.metricsEnabled = metricsEnabled;
                this.searchEnginePluginFactory = searchEnginePluginFactory;
                this.turSolrQueryBuilder = turSolrQueryBuilder;
                this.turSNFacetDefinitionFactory = turSNFacetDefinitionFactory;
                this.facetRenderer = facetRenderer;
                this.paginationBuilder = paginationBuilder;
                this.documentResponse = documentResponse;
                this.queryContextBuilder = queryContextBuilder;
                this.pipelineObservation = pipelineObservation;
        }

        public Optional<TurSNSite> getSNSite(String siteName) {
                return snapshotService.getSnapshot(siteName, null)
                                .map(TurSNSiteSearchSnapshot::site);
        }

        public boolean existsByTurSNSiteAndLanguage(String siteName, Locale locale) {
                return snapshotService.getSnapshot(siteName, locale)
                                .map(snap -> snap.allLocales().stream()
                                                .map(TurSNSiteLocale::getLanguage)
                                                .anyMatch(l -> l != null && l.equals(locale)))
                                .orElse(false);
        }

        private static boolean istTuringEntity(TurSNFieldType snType) {
                return Collections.unmodifiableSet(
                                EnumSet.of(TurSNFieldType.NER, TurSNFieldType.THESAURUS))
                                .contains(snType);
        }

        public List<String> latestSearches(String siteName, String locale, String userId,
                        int rows) {
                return snapshotService.getSnapshot(siteName, null)
                                .map(snap -> turSNSiteMetricAccessRepositoryPort
                                                .findLatestSearches(snap.siteId(), locale,
                                                                userId, PageRequest.of(0, rows))
                                                .stream()
                                                .map(TurSNSiteMetricAccessTermDomain::term)
                                                .toList())
                                .orElse(Collections.emptyList());
        }

        public TurSNSiteSearchBean search(TurSNSiteSearchContext context) {
                return pipelineObservation.record(TurMeterNames.STAGE_SEARCH_TOTAL,
                                () -> searchInternal(context));
        }

        private TurSNSiteSearchBean searchInternal(TurSNSiteSearchContext context) {
                Optional<TurSNSiteSearchSnapshot> snapOpt = snapshotService
                                .getSnapshot(context.getSiteName(), context.getLocale());
                if (snapOpt.isEmpty()) {
                        return new TurSNSiteSearchBean();
                }
                TurSNSiteSearchSnapshot snap = snapOpt.get();
                TurSearchEnginePlugin plugin = searchEnginePluginFactory.getPlugin(snap.pluginType());
                Optional<TurSEResults> resultsOpt = plugin.retrieveSearchResults(context);
                if (resultsOpt.isEmpty()) {
                        return new TurSNSiteSearchBean();
                }
                TurSEResults turSEResults = resultsOpt.get();
                TurSolrInstance solrInstance = turSolrInstanceProcess
                                .initSolrInstance(context.getSiteName(), context.getLocale())
                                .orElse(null);
                return pipelineObservation.record(TurMeterNames.STAGE_SEARCH_RESPONSE,
                                () -> searchResponse(context, snap, solrInstance, turSEResults));
        }

        public List<Object> searchList(TurSNSiteSearchContext context) {
                context.getTurSNConfig().setHlEnabled(false);
                return snapshotService.getSnapshot(context.getSiteName(), context.getLocale())
                                .map(snap -> {
                                        TurSearchEnginePlugin plugin = searchEnginePluginFactory
                                                        .getPlugin(snap.pluginType());
                                        return plugin.retrieveSearchResults(context)
                                                        .map(turSEResults -> {
                                                                populateMetrics(snap.site(), context,
                                                                                turSEResults.getNumFound());
                                                                return documentResponse.responseList(context,
                                                                                turSEResults.getResults());
                                                        })
                                                        .orElse(Collections.<Object>emptyList());
                                })
                                .orElse(Collections.emptyList());
        }

        private TurSNSiteSearchBean searchResponse(TurSNSiteSearchContext context,
                        TurSNSiteSearchSnapshot snap,
                        TurSolrInstance turSolrInstance, TurSEResults turSEResults) {
                TurSNSite turSNSite = snap.site();
                populateMetrics(turSNSite, context, turSEResults.getNumFound());
                List<TurSNFacetDefinition> facetDefinitions = snap.enabledFields()
                                .stream()
                                .filter(field -> field.getFacet() == 1
                                                || snap.fieldIdsWithCustomFacets().contains(field.getId()))
                                .flatMap(field -> turSNFacetDefinitionFactory.fromField(field,
                                                context.getLocale(), facetLocalesFor(snap, field,
                                                                context.getLocale()))
                                                .stream())
                                .toList();
                List<TurSNSiteFieldExtDto> turSNSiteFieldExtDtoList = facetDefinitions.stream()
                                .map(TurSNFacetDefinition::toFacetFieldExtDto)
                                .toList();
                Map<String, TurSNSiteFieldExtDto> facetMap = setFacetMap(turSNSiteFieldExtDtoList);
                if (turSolrQueryBuilder.hasGroup(context.getTurSEParameters())) {
                        return getSearchBeanForGroup(context, snap, turSolrInstance, turSEResults,
                                        facetMap);
                }
                return getSearchBeanForResults(context, snap, turSolrInstance, turSEResults,
                                facetMap);
        }

        @NotNull
        private HashSet<TurSNSiteFieldExtFacet> facetLocalesFor(TurSNSiteSearchSnapshot snap,
                        TurSNSiteFieldExt field, Locale locale) {
                Set<TurSNSiteFieldExtFacet> labels = snap.facetLabelsByFieldId()
                                .getOrDefault(field.getId(), Collections.emptySet());
                return new HashSet<>(Collections.singletonList(labels.stream().findFirst()
                                .orElse(TurSNSiteFieldExtFacet.builder().locale(locale)
                                                .label(field.getFacetName()).build())));
        }

        private TurSNSiteSearchBean getSearchBeanForResults(TurSNSiteSearchContext context,
                        TurSNSiteSearchSnapshot snap, TurSolrInstance turSolrInstance,
                        TurSEResults turSEResults, Map<String, TurSNSiteFieldExtDto> facetMap) {
                TurSNSite turSNSite = snap.site();
                return new TurSNSiteSearchBean()
                                .setResults(documentResponse.responseDocuments(context, turSolrInstance,
                                                turSNSite, facetMap, turSEResults.getResults()))
                                .setPagination(paginationBuilder.build(context.getUri(), turSEResults))
                                .setWidget(responseWidget(context, snap, facetMap, turSEResults))
                                .setQueryContext(queryContextBuilder.build(turSNSite, turSEResults,
                                                context.getLocale()));
        }

        private TurSNSiteSearchBean getSearchBeanForGroup(TurSNSiteSearchContext context,
                        TurSNSiteSearchSnapshot snap, TurSolrInstance turSolrInstance,
                        TurSEResults turSEResults, Map<String, TurSNSiteFieldExtDto> facetMap) {
                TurSNSite turSNSite = snap.site();
                return new TurSNSiteSearchBean()
                                .setGroups(responseGroups(context, turSolrInstance, turSNSite,
                                                facetMap, turSEResults))
                                .setWidget(responseWidget(context, snap, facetMap, turSEResults))
                                .setQueryContext(queryContextBuilder.build(turSNSite, turSEResults,
                                                context.getLocale()));
        }

        private List<TurSNSiteSearchGroupBean> responseGroups(TurSNSiteSearchContext context,
                        TurSolrInstance turSolrInstance, TurSNSite turSNSite,
                        Map<String, TurSNSiteFieldExtDto> facetMap, TurSEResults turSEResults) {
                List<TurSNSiteSearchGroupBean> turSNSiteSearchGroupBeans = new ArrayList<>();
                Optional.ofNullable(turSEResults.getGroups()).ifPresent(g -> g.forEach(group -> {
                        int lastItemOfFullPage = (int) group.getStart() + group.getLimit();
                        int firstItemOfFullPage = (int) group.getStart() + 1;
                        int count = (int) group.getNumFound();
                        int pageEnd = Math.min(lastItemOfFullPage, count);
                        turSNSiteSearchGroupBeans.add(new TurSNSiteSearchGroupBean()
                                        .setName(group.getName())
                                        .setCount((int) group.getNumFound())
                                        .setPageCount(group.getPageCount())
                                        .setPage(group.getCurrentPage()).setCount(count)
                                        .setPageEnd(pageEnd)
                                        .setPageStart(Math.min(firstItemOfFullPage, pageEnd))
                                        .setLimit(group.getLimit())
                                        .setPagination(paginationBuilder.build(
                                                        changeGroupURIForPagination(
                                                                        context.getUri(),
                                                                        group.getName()),
                                                        group))
                                        .setResults(documentResponse.responseDocuments(context,
                                                        turSolrInstance, turSNSite, facetMap,
                                                        group.getResults())));
                }));
                return turSNSiteSearchGroupBeans;
        }

        public void populateMetrics(TurSNSite turSNSite,
                        TurSNSiteSearchContext turSNSiteSearchContext, long numFound) {
                if (!turSNSiteSearchContext.getTurSEParameters().getQuery().trim().equals("*")
                                && useMetrics(turSNSiteSearchContext)) {
                        TurSNSiteMetricAccess turSNSiteMetricAccess = new TurSNSiteMetricAccess();
                        turSNSiteMetricAccess.setAccessDate(Instant.now());
                        turSNSiteMetricAccess.setLanguage(turSNSiteSearchContext.getLocale());
                        turSNSiteMetricAccess.setTerm(
                                        turSNSiteSearchContext.getTurSEParameters().getQuery());
                        turSNSiteMetricAccess.setTurSNSite(turSNSite);
                        turSNSiteMetricAccess.setNumFound(numFound);
                        Optional.ofNullable(turSNSiteSearchContext.getTurSNSitePostParamsBean())
                                        .ifPresent(p -> {
                                                turSNSiteMetricAccess.setTargetingRules(Optional
                                                                .ofNullable(p.getTargetingRules())
                                                                .map(HashSet::new)
                                                                .orElse(new HashSet<>()));
                                                turSNSiteMetricAccess.setUserId(p.getUserId());
                                        });
                        turSNSiteMetricAccessRepository.save(turSNSiteMetricAccess);
                }
        }

        private boolean useMetrics(TurSNSiteSearchContext turSNSiteSearchContext) {
                if (!metricsEnabled) {
                        return false;
                }
                return Optional.ofNullable(turSNSiteSearchContext.getTurSNSitePostParamsBean())
                                .map(TurSNSitePostParamsBean::isPopulateMetrics).orElse(true);
        }

        private Map<String, TurSNSiteFieldExtDto> setFacetMap(
                        List<TurSNSiteFieldExtDto> turSNSiteFacetFieldExtList) {
                Map<String, TurSNSiteFieldExtDto> facetMap = new HashMap<>();
                turSNSiteFacetFieldExtList.forEach(turSNSiteFacetFieldExt -> {
                        if (istTuringEntity(turSNSiteFacetFieldExt.getSnType())) {
                                facetMap.put(String.format("%s_%s", TurSNUtils.TURING_ENTITY,
                                                turSNSiteFacetFieldExt.getName()),
                                                turSNSiteFacetFieldExt);
                        }
                        facetMap.put(turSNSiteFacetFieldExt.getName(), turSNSiteFacetFieldExt);
                });

                return facetMap;
        }

        private TurSNSiteFilterQueryBean requestFilterQuery(List<String> fq) {
                List<String> facetsInFilterQueries = new ArrayList<>();
                List<String> filterQueryModified = new ArrayList<>();
                processFilterQuery(fq, facetsInFilterQueries, filterQueryModified);
                return new TurSNSiteFilterQueryBean()
                                .setFacetsInFilterQueries(facetsInFilterQueries)
                                .setItems(filterQueryModified);
        }

        private void processFilterQuery(List<String> fq, List<String> facetsInFilterQueries,
                        List<String> filterQueryModified) {
                if (!CollectionUtils.isEmpty(fq)) {
                        fq.forEach(filterQuery -> TurCommonsUtils.getKeyValueFromColon(filterQuery)
                                        .ifPresentOrElse(f -> {
                                                addFacetInFilterQuery(facetsInFilterQueries,
                                                                f.getKey());
                                                if (!f.getValue().startsWith("\"")
                                                                && !f.getValue().startsWith("[")) {
                                                        filterQueryModified.add("%s:\"%s\""
                                                                        .formatted(f.getKey(), f
                                                                                        .getValue()));
                                                }
                                        }, () -> filterQueryModified.add(filterQuery)));
                }
        }

        private void addFacetInFilterQuery(List<String> facetsInFilterQueries, String key) {
                if (!facetsInFilterQueries.contains(key)) {
                        facetsInFilterQueries.add(key);
                }
        }

        public List<String> requestTargetingRules(List<String> tr) {
                List<String> targetingRuleModified = new ArrayList<>();
                if (!CollectionUtils.isEmpty(tr)) {
                        tr.forEach(targetingRule -> TurCommonsUtils
                                        .getKeyValueFromColon(targetingRule).ifPresentOrElse(t -> {
                                                if (!t.getValue().startsWith("\"")
                                                                && !t.getValue().startsWith("[")) {
                                                        targetingRuleModified.add("%s:\"%s\""
                                                                        .formatted(t.getKey(), t
                                                                                        .getValue()));
                                                }
                                        }, () -> targetingRuleModified.add(targetingRule)));
                }
                return targetingRuleModified;
        }

        private TurSNSiteSearchWidgetBean responseWidget(TurSNSiteSearchContext context,
                        TurSNSiteSearchSnapshot snap,
                        Map<String, TurSNSiteFieldExtDto> facetMap, TurSEResults turSEResults) {
                return pipelineObservation.record(TurMeterNames.STAGE_WIDGET_BUILD,
                                () -> buildWidget(context, snap, facetMap, turSEResults));
        }

        private TurSNSiteSearchWidgetBean buildWidget(TurSNSiteSearchContext context,
                        TurSNSiteSearchSnapshot snap,
                        Map<String, TurSNSiteFieldExtDto> facetMap, TurSEResults turSEResults) {
                TurSNSite turSNSite = snap.site();
                List<String> facetsInFilterQueries = requestFilterQuery(context.getTurSEParameters()
                                .getTurSNFilterParams().getDefaultValues())
                                .getFacetsInFilterQueries();
                List<String> facetFieldsInFilterQuery = getFacetFieldsInFilterQuery(context, turSNSite);
                return new TurSNSiteSearchWidgetBean()
                                .setFacet(facetRenderer.responseFacet(context, turSNSite,
                                                facetsInFilterQueries, facetMap, turSEResults))
                                .setSecondaryFacet(facetRenderer.responseSecondaryFacet(context,
                                                turSNSite, facetsInFilterQueries, facetMap, turSEResults))
                                .setFacetToRemove(facetRenderer.responseFacetToRemove(context, turSNSite))
                                .setSimilar(responseMLT(turSNSite, turSEResults))
                                .setSpellCheck(turSEResults.getSpellCheck() != null
                                                ? new TurSNSiteSpellCheckBean(context, turSEResults.getSpellCheck())
                                                : new TurSNSiteSpellCheckBean())
                                .setIcon(turSNSite.getIcon())
                                .setLocales(responseLocales(snap, context.getUri()))
                                .setSpotlights(responseSpotlights(context, turSNSite))
                                .setCleanUpFacets(TurSNUtils.removeFilterQueryByFieldNames(context.getUri(),
                                                facetFieldsInFilterQuery).toString())
                                .setSelectedFilterQueries(TurSNUtils.filterQueryByFieldNames(context.getUri(),
                                                facetFieldsInFilterQuery));
        }

        private List<TurSNSiteSpotlightDocumentBean> responseSpotlights(
                        TurSNSiteSearchContext context, TurSNSite turSNSite) {
                List<TurSNSiteSpotlightDocumentBean> turSNSiteSpotlightDocumentBeans = new ArrayList<>();
                turSNSpotlightProcess.getSpotlightsFromQuery(context, turSNSite).forEach((key,
                                value) -> value.forEach(document -> turSNSiteSpotlightDocumentBeans
                                                .add(new TurSNSiteSpotlightDocumentBean()
                                                                .setId(document.getId())
                                                                .setContent(document.getContent())
                                                                .setLink(document.getLink())
                                                                .setPosition(document.getPosition())
                                                                .setReferenceId(document
                                                                                .getReferenceId())
                                                                .setTitle(document.getTitle())
                                                                .setType(document.getType()))));
                turSNSiteSpotlightDocumentBeans.sort(Comparator
                                .comparingInt(TurSNSiteSpotlightDocumentBean::getPosition));
                return turSNSiteSpotlightDocumentBeans;
        }

        /**
         * Builds the locale switcher from a pre-loaded snapshot — no DB hit. Used by
         * the search response pipeline where the snapshot is already in scope.
         */
        public List<TurSNSiteLocaleBean> responseLocales(TurSNSiteSearchSnapshot snap, URI uri) {
                return responseLocaleBeans(snap.allLocales(), uri);
        }

        public List<TurSNSiteLocaleBean> responseLocales(TurSNSite turSNSite, URI uri) {
                return snapshotService.getSnapshot(turSNSite.getName(), null)
                                .map(snap -> responseLocaleBeans(snap.allLocales(), uri))
                                .orElseGet(ArrayList::new);
        }

        /**
         * Domain-friendly overload — controllers that already hold a
         * {@link com.viglet.turing.domain.sn.TurSNSiteDomain} from the port can call
         * this directly without re-loading the JPA entity.
         */
        public List<TurSNSiteLocaleBean> responseLocales(
                        com.viglet.turing.domain.sn.TurSNSiteDomain site, URI uri) {
                return snapshotService.getSnapshot(site.name(), null)
                                .map(snap -> responseLocaleBeans(snap.allLocales(), uri))
                                .orElseGet(ArrayList::new);
        }

        private List<TurSNSiteLocaleBean> responseLocaleBeans(List<TurSNSiteLocale> locales, URI uri) {
                List<TurSNSiteLocaleBean> turSNSiteLocaleBeans = new ArrayList<>();
                locales.forEach(turSNSiteLocale -> turSNSiteLocaleBeans
                                .add(new TurSNSiteLocaleBean()
                                                .setLocale(turSNSiteLocale.getLanguage())
                                                .setLink(TurCommonsUtils.addOrReplaceParameter(uri,
                                                                TurSNParamType.LOCALE,
                                                                turSNSiteLocale.getLanguage(),
                                                                true)
                                                                .toString())));
                return turSNSiteLocaleBeans;
        }

        private List<TurSESimilarResult> responseMLT(TurSNSite turSNSite,
                        TurSEResults turSEResults) {
                return hasMLT(turSNSite, turSEResults) ? turSEResults.getSimilarResults()
                                : Collections.emptyList();
        }

        private boolean hasMLT(TurSNSite turSNSite, TurSEResults turSEResults) {
                return TurSNUtils.isTrue(turSNSite.getMlt())
                                && turSEResults.getSimilarResults() != null
                                && !turSEResults.getSimilarResults().isEmpty();
        }

        private List<String> getFacetFieldsInFilterQuery(TurSNSiteSearchContext context,
                        TurSNSite turSNSite) {
                return turSolrQueryBuilder.getFacetFieldsInFilterQuery(new TurSNFacetTypeContext(null,
                                turSNSite, context.getTurSEParameters().getTurSNFilterParams()));
        }

        private URI changeGroupURIForPagination(URI uri, String fieldName) {
                return TurCommonsUtils.addOrReplaceParameter(
                                TurSNUtils.removeQueryStringParameter(uri, GROUP),
                                TurSNParamType.FILTER_QUERIES_DEFAULT, fieldName, true);
        }
}
