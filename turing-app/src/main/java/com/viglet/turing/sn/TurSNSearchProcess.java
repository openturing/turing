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
import java.util.stream.Collectors;

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
import com.viglet.turing.domain.sn.TurSNSiteLocaleRepositoryPort;
import com.viglet.turing.domain.sn.TurSNSiteMetricAccessRepositoryPort;
import com.viglet.turing.domain.sn.TurSNSiteMetricAccessTermDomain;
import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.persistence.model.sn.metric.TurSNSiteMetricAccess;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
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
        private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
        private final TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository;
        private final TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository;
        private final TurSNSiteRepository turSNSiteRepository;
        private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
        private final TurSNSiteLocaleRepositoryPort turSNSiteLocaleRepositoryPort;
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

        public TurSNSearchProcess(TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
                        TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository,
                        TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository,
                        TurSNSiteRepository turSNSiteRepository,
                        TurSNSiteLocaleRepository turSNSiteLocaleRepository,
                        TurSNSiteLocaleRepositoryPort turSNSiteLocaleRepositoryPort,
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
                        TurSNQueryContextBuilder queryContextBuilder) {
                this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
                this.turSNSiteCustomFacetRepository = turSNSiteCustomFacetRepository;
                this.turSNSiteFieldExtFacetRepository = turSNSiteFieldExtFacetRepository;
                this.turSNSiteRepository = turSNSiteRepository;
                this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
                this.turSNSiteLocaleRepositoryPort = turSNSiteLocaleRepositoryPort;
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
        }

        public Optional<TurSNSite> getSNSite(String siteName) {
                return turSNSiteRepository.findByNameIgnoreCase(siteName);

        }

        public boolean existsByTurSNSiteAndLanguage(String siteName, Locale locale) {
                return turSNSiteRepository.findByNameIgnoreCase(siteName)
                                .map(turSNSite -> turSNSiteLocaleRepositoryPort
                                                .existsBySnSiteIdAndLanguage(turSNSite.getId(),
                                                                locale))
                                .orElse(false);
        }

        private static boolean istTuringEntity(TurSNFieldType snType) {
                return Collections.unmodifiableSet(
                                EnumSet.of(TurSNFieldType.NER, TurSNFieldType.THESAURUS))
                                .contains(snType);
        }

        public List<String> latestSearches(String siteName, String locale, String userId,
                        int rows) {
                return turSNSiteRepository.findByNameIgnoreCase(siteName)
                                .map(turSNSite -> turSNSiteMetricAccessRepositoryPort
                                                .findLatestSearches(turSNSite.getId(), locale,
                                                                userId, PageRequest.of(0, rows))
                                                .stream()
                                                .map(TurSNSiteMetricAccessTermDomain::term)
                                                .toList())
                                .orElse(Collections.emptyList());
        }

        public TurSNSiteSearchBean search(TurSNSiteSearchContext turSNSiteSearchContext) {
                return turSNSiteRepository.findByNameIgnoreCase(turSNSiteSearchContext.getSiteName())
                                .map(turSNSite -> {
                                        TurSearchEnginePlugin plugin = searchEnginePluginFactory
                                                        .getPluginForSite(turSNSite);
                                        return plugin.retrieveSearchResults(turSNSiteSearchContext)
                                                        .map(turSEResults -> {
                                                                TurSolrInstance solrInstance = turSolrInstanceProcess
                                                                                .initSolrInstance(
                                                                                                turSNSiteSearchContext
                                                                                                                .getSiteName(),
                                                                                                turSNSiteSearchContext
                                                                                                                .getLocale())
                                                                                .orElse(null);
                                                                return searchResponse(turSNSiteSearchContext,
                                                                                solrInstance, turSEResults);
                                                        })
                                                        .orElse(new TurSNSiteSearchBean());
                                })
                                .orElse(new TurSNSiteSearchBean());
        }

        public List<Object> searchList(TurSNSiteSearchContext turSNSiteSearchContext) {
                turSNSiteSearchContext.getTurSNConfig().setHlEnabled(false);
                return turSNSiteRepository.findByNameIgnoreCase(turSNSiteSearchContext.getSiteName())
                                .map(turSNSite -> {
                                        TurSearchEnginePlugin plugin = searchEnginePluginFactory
                                                        .getPluginForSite(turSNSite);
                                        return plugin.retrieveSearchResults(turSNSiteSearchContext)
                                                        .map(turSEResults -> searchResponseList(
                                                                        turSNSiteSearchContext, turSEResults))
                                                        .orElse(Collections.emptyList());
                                })
                                .orElse(Collections.emptyList());
        }

        private List<Object> searchResponseList(TurSNSiteSearchContext context, TurSEResults turSEResults) {
                return turSNSiteRepository.findByNameIgnoreCase(context.getSiteName()).map(turSNSite -> {
                        populateMetrics(turSNSite, context, turSEResults.getNumFound());
                        return documentResponse.responseList(context, turSEResults.getResults());
                }).orElse(Collections.emptyList());
        }

        private TurSNSiteSearchBean searchResponse(TurSNSiteSearchContext context,
                        TurSolrInstance turSolrInstance, TurSEResults turSEResults) {
                return turSNSiteRepository.findByNameIgnoreCase(context.getSiteName()).map(turSNSite -> {
                        populateMetrics(turSNSite, context, turSEResults.getNumFound());
                        List<TurSNSiteFieldExt> enabledFields = turSNSiteFieldExtRepository
                                        .findByTurSNSiteAndEnabled(turSNSite, 1);
                        Set<String> fieldIdsWithCustomFacets = turSNSiteCustomFacetRepository
                                        .findByFieldExtsWithDetails(enabledFields).stream()
                                        .map(cf -> cf.getTurSNSiteFieldExt().getId())
                                        .collect(Collectors.toSet());
                        List<TurSNFacetDefinition> facetDefinitions = enabledFields
                                        .stream()
                                        .filter(turSNSiteFieldExt -> turSNSiteFieldExt.getFacet() == 1
                                                        || fieldIdsWithCustomFacets.contains(
                                                                        turSNSiteFieldExt.getId()))
                                        .flatMap(turSNSiteFieldExt -> getFacetDefinitions(
                                                        context, turSNSiteFieldExt)
                                                        .stream())
                                        .toList();

                        List<TurSNSiteFieldExtDto> turSNSiteFieldExtDtoList = facetDefinitions.stream()
                                        .map(TurSNFacetDefinition::toFacetFieldExtDto)
                                        .toList();
                        Map<String, TurSNSiteFieldExtDto> facetMap = setFacetMap(turSNSiteFieldExtDtoList);
                        if (turSolrQueryBuilder.hasGroup(context.getTurSEParameters())) {
                                return getSearchBeanForGroup(context, turSolrInstance, turSEResults,
                                                turSNSite, facetMap);
                        } else {
                                return getSearchBeanForResults(context, turSolrInstance,
                                                turSEResults, turSNSite, facetMap);
                        }
                }).orElse(new TurSNSiteSearchBean());
        }

        @NotNull
        private HashSet<TurSNSiteFieldExtFacet> getFacetLocales(TurSNSiteSearchContext context,
                        TurSNSiteFieldExt turSNSiteFieldExt) {
                return new HashSet<>(Collections.singletonList(turSNSiteFieldExtFacetRepository
                                .findByTurSNSiteFieldExtAndLocale(turSNSiteFieldExt,
                                                context.getLocale())
                                .stream().findFirst()
                                .orElse(TurSNSiteFieldExtFacet.builder().locale(context.getLocale())
                                                .label(turSNSiteFieldExt.getFacetName()).build())));
        }

        private List<TurSNFacetDefinition> getFacetDefinitions(TurSNSiteSearchContext context,
                        TurSNSiteFieldExt turSNSiteFieldExt) {
                return turSNFacetDefinitionFactory.fromField(turSNSiteFieldExt,
                                context.getLocale(),
                                getFacetLocales(context, turSNSiteFieldExt));
        }

        private TurSNSiteSearchBean getSearchBeanForResults(TurSNSiteSearchContext context,
                        TurSolrInstance turSolrInstance, TurSEResults turSEResults,
                        TurSNSite turSNSite, Map<String, TurSNSiteFieldExtDto> facetMap) {
                return new TurSNSiteSearchBean()
                                .setResults(documentResponse.responseDocuments(context, turSolrInstance,
                                                turSNSite, facetMap, turSEResults.getResults()))
                                .setPagination(paginationBuilder.build(context.getUri(), turSEResults))
                                .setWidget(responseWidget(context, turSNSite,
                                                facetMap, turSEResults))
                                .setQueryContext(queryContextBuilder.build(turSNSite, turSEResults,
                                                context.getLocale()));
        }

        private TurSNSiteSearchBean getSearchBeanForGroup(TurSNSiteSearchContext context,
                        TurSolrInstance turSolrInstance, TurSEResults turSEResults,
                        TurSNSite turSNSite, Map<String, TurSNSiteFieldExtDto> facetMap) {
                return new TurSNSiteSearchBean()
                                .setGroups(responseGroups(context, turSolrInstance, turSNSite,
                                                facetMap, turSEResults))
                                .setWidget(responseWidget(context, turSNSite,
                                                facetMap, turSEResults))
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
                        TurSNSite turSNSite,
                        Map<String, TurSNSiteFieldExtDto> facetMap, TurSEResults turSEResults) {
                List<String> facetsInFilterQueries = requestFilterQuery(context.getTurSEParameters()
                                .getTurSNFilterParams().getDefaultValues())
                                .getFacetsInFilterQueries();
                return new TurSNSiteSearchWidgetBean()
                                .setFacet(facetRenderer.responseFacet(context, turSNSite,
                                                facetsInFilterQueries, facetMap, turSEResults))
                                .setSecondaryFacet(facetRenderer.responseSecondaryFacet(context,
                                                turSNSite, facetsInFilterQueries, facetMap,
                                                turSEResults))
                                .setFacetToRemove(facetRenderer.responseFacetToRemove(context, turSNSite))
                                .setSimilar(responseMLT(turSNSite, turSEResults))
                                .setSpellCheck(turSEResults.getSpellCheck() != null
                                                ? new TurSNSiteSpellCheckBean(context, turSEResults.getSpellCheck())
                                                : new TurSNSiteSpellCheckBean())
                                .setIcon(turSNSite.getIcon())
                                .setLocales(responseLocales(turSNSite, context.getUri()))
                                .setSpotlights(responseSpotlights(context, turSNSite))
                                .setCleanUpFacets(responseCleanUpFacet(context, turSNSite))
                                .setSelectedFilterQueries(
                                                responseFieldQueriesFromFacets(context, turSNSite));
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

        public List<TurSNSiteLocaleBean> responseLocales(TurSNSite turSNSite, URI uri) {
                List<TurSNSiteLocaleBean> turSNSiteLocaleBeans = new ArrayList<>();
                turSNSiteLocaleRepository
                                .findByTurSNSiteOrderByPositionAsc(turSNSite)
                                .forEach(turSNSiteLocale -> turSNSiteLocaleBeans
                                                .add(new TurSNSiteLocaleBean()
                                                                .setLocale(turSNSiteLocale
                                                                                .getLanguage())
                                                                .setLink(TurCommonsUtils
                                                                                .addOrReplaceParameter(
                                                                                                uri,
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

        private String responseCleanUpFacet(TurSNSiteSearchContext context, TurSNSite turSNSite) {
                return TurSNUtils
                                .removeFilterQueryByFieldNames(context.getUri(),
                                                getFacetFieldsInFilterQuery(context, turSNSite))
                                .toString();
        }

        private List<String> getFacetFieldsInFilterQuery(TurSNSiteSearchContext context,
                        TurSNSite turSNSite) {
                return turSolrQueryBuilder.getFacetFieldsInFilterQuery(new TurSNFacetTypeContext(null,
                                turSNSite, context.getTurSEParameters().getTurSNFilterParams()));
        }

        private List<String> responseFieldQueriesFromFacets(TurSNSiteSearchContext context,
                        TurSNSite turSNSite) {
                return TurSNUtils.filterQueryByFieldNames(context.getUri(),
                                getFacetFieldsInFilterQuery(context, turSNSite));
        }

        private URI changeGroupURIForPagination(URI uri, String fieldName) {
                return TurCommonsUtils.addOrReplaceParameter(
                                TurSNUtils.removeQueryStringParameter(uri, GROUP),
                                TurSNParamType.FILTER_QUERIES_DEFAULT, fieldName, true);
        }
}
