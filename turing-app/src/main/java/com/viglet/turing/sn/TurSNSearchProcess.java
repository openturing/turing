/*
 * Copyright (C) 2016-2022 the original author or authors.
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
package com.viglet.turing.sn;

import com.google.inject.Inject;
import com.viglet.turing.api.sn.bean.TurSNSiteFilterQueryBean;
import com.viglet.turing.commons.se.TurSEFilterQueryParameters;
import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.similar.TurSESimilarResult;
import com.viglet.turing.commons.sn.bean.*;
import com.viglet.turing.commons.sn.bean.spellcheck.TurSNSiteSpellCheckBean;
import com.viglet.turing.commons.sn.pagination.TurSNPaginationType;
import com.viglet.turing.commons.sn.search.TurSNParamType;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtDto;
import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtFacetDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.persistence.model.sn.metric.TurSNSiteMetricAccess;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.result.TurSEGenericResults;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.sn.facet.TurSNFacetTypeContext;
import com.viglet.turing.sn.spotlight.TurSNSpotlightProcess;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.*;
import java.util.stream.IntStream;

/**
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
@Component
public class TurSNSearchProcess {
    public static final String PREVIOUS = "Previous";
    public static final String GROUP = "group";
    public static final String LAST = "Last";
    public static final String NEXT = "Next";
    public static final String FIRST = "First";
    public static final String LANGUAGE = "language";
    public static final String FACETS_TO_REMOVE = "Facets To Remove";
    public static final String AND_OR = "AND-OR";
    public static final String FACET_ITEM_AND = "-AND";
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSolrInstanceProcess turSolrInstanceProcess;
    private final TurSolr turSolr;
    private final TurSNSpotlightProcess turSNSpotlightProcess;
    private final TurSNSiteMetricAccessRepository turSNSiteMetricAccessRepository;

    @Inject
    public TurSNSearchProcess(TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
                              TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository,
                              TurSNSiteRepository turSNSiteRepository,
                              TurSNSiteLocaleRepository turSNSiteLocaleRepository,
                              TurSolrInstanceProcess turSolrInstanceProcess,
                              TurSolr turSolr,
                              TurSNSpotlightProcess turSNSpotlightProcess,
                              TurSNSiteMetricAccessRepository turSNSiteMetricAccessRepository) {
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.turSNSiteFieldExtFacetRepository = turSNSiteFieldExtFacetRepository;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.turSolrInstanceProcess = turSolrInstanceProcess;
        this.turSolr = turSolr;
        this.turSNSpotlightProcess = turSNSpotlightProcess;
        this.turSNSiteMetricAccessRepository = turSNSiteMetricAccessRepository;
    }

    public Optional<TurSNSite> getSNSite(String siteName) {
        return turSNSiteRepository.findByName(siteName);

    }
    public boolean existsByTurSNSiteAndLanguage(String siteName, Locale locale) {
        return turSNSiteRepository.findByName(siteName).map(turSNSite ->
                turSNSiteLocaleRepository.existsByTurSNSiteAndLanguage(turSNSite, locale)).orElse(false);
    }
    private static boolean istTuringEntity(TurSNFieldType snType) {
        return Collections.unmodifiableSet(EnumSet.of(TurSNFieldType.NER, TurSNFieldType.THESAURUS)).contains(snType);
    }

    private static boolean isTrue(Integer value) {
        return value == 1;
    }

    private static boolean showFacetByFacetItemType(TurSNSite turSNSite) {
        if (turSNSite.getFacetItemType() == null) return false;
        else return switch (turSNSite.getFacetItemType()) {
            case OR -> true;
            case AND, DEFAULT -> false;
        };
    }

    private static boolean showSecondaryFacet(List<String> facetsInFilterQueries,
                                              Map<String, TurSNSiteFieldExtDto> facetMap,
                                              TurSEFacetResult facet, TurSNSite turSNSite) {
        return showFacet(facetsInFilterQueries, facetMap, facet, turSNSite)
                && facetMap.get(facet.getFacet()).getSecondaryFacet() != null
                && facetMap.get(facet.getFacet()).getSecondaryFacet();
    }

    private static boolean showMainFacet(List<String> facetsInFilterQueries,
                                         Map<String, TurSNSiteFieldExtDto> facetMap,
                                         TurSEFacetResult facet, TurSNSite turSNSite) {
        return showFacet(facetsInFilterQueries, facetMap, facet, turSNSite)
                && (facetMap.get(facet.getFacet()).getSecondaryFacet() == null
                || !(facetMap.get(facet.getFacet()).getSecondaryFacet() != null
                && facetMap.get(facet.getFacet()).getSecondaryFacet()));
    }

    private static boolean showFacet(List<String> facetsInFilterQueries,
                                     Map<String, TurSNSiteFieldExtDto> facetMap,
                                     TurSEFacetResult facet, TurSNSite turSNSite) {
        return facetMap.containsKey(facet.getFacet())
                && (!facetsInFilterQueries.contains(facet.getFacet()) || showFacetByFacetItemType(turSNSite))
                && !facet.getTurSEFacetResultAttr().isEmpty();
    }

    @NotNull
    private static TurSNSiteSearchFacetBean getTurSNSiteSearchFacetBean(TurSNSiteSearchContext context,
                                                                        TurSNSiteFieldExtDto turSNSiteFieldExtDto,
                                                                        List<TurSNSiteSearchFacetItemBean>
                                                                                turSNSiteSearchFacetItemBeans) {
        TurSNSiteFieldExtFacetDto turSNSiteFieldExtFacetDto = turSNSiteFieldExtDto.getFacetLocales()
                .stream()
                .filter(o -> o.getLocale().toString().equals(context.getLocale().toString())).findFirst()
                .orElse(TurSNSiteFieldExtFacetDto.builder().locale(context.getLocale())
                        .label(turSNSiteFieldExtDto.getFacetName()).build());
        return new TurSNSiteSearchFacetBean()
                .setLabel(new TurSNSiteSearchFacetLabelBean()
                        .setLang(context.getLocale().toString())
                        .setText(turSNSiteFieldExtFacetDto.getLabel()))
                .setName(turSNSiteFieldExtDto.getName())
                .setDescription(turSNSiteFieldExtDto.getDescription())
                .setType(turSNSiteFieldExtDto.getType())
                .setFacets(turSNSiteSearchFacetItemBeans)
                .setMultiValued(isTrue(turSNSiteFieldExtDto.getMultiValued()))
                .setCleanUpLink(TurSNUtils.removeFilterQueryByFieldName(context.getUri(),
                        turSNSiteFieldExtDto.getName()).toString());
    }

    @NotNull
    private static TurSNSiteSearchFacetBean getTurSNSiteSearchFacetBean(
            List<TurSNSiteSearchFacetItemBean> turSNSiteSearchFacetToRemoveItemBeans) {
        return new TurSNSiteSearchFacetBean()
                .setLabel(new TurSNSiteSearchFacetLabelBean()
                        .setLang(TurSNUtils.DEFAULT_LANGUAGE)
                        .setText(FACETS_TO_REMOVE))
                .setFacets(turSNSiteSearchFacetToRemoveItemBeans);
    }

    private static boolean isCurrentPage(TurSEGenericResults turSEResults, int page) {
        return page == turSEResults.getCurrentPage();
    }

    private static boolean isNotLastPage(TurSEGenericResults turSEResults) {
        return turSEResults.getCurrentPage() != turSEResults.getPageCount() && turSEResults.getPageCount() > 1;
    }

    public List<String> latestSearches(String siteName, String locale, String userId, int rows) {
        return turSNSiteRepository.findByName(siteName).map(turSNSite -> turSNSiteMetricAccessRepository
                .findLatestSearches(turSNSite, locale, userId, PageRequest.of(0, rows)).stream()
                .map(TurSNSiteMetricAccessTerm::getTerm).toList()).orElse(Collections.emptyList());
    }

    public TurSNSiteSearchBean search(TurSNSiteSearchContext turSNSiteSearchContext) {
        return turSolrInstanceProcess
                .initSolrInstance(turSNSiteSearchContext.getSiteName(), turSNSiteSearchContext.getLocale())
                .map(turSolrInstance ->
                        turSolr.retrieveSolrFromSN(turSolrInstance, turSNSiteSearchContext)
                                .map(turSEResults ->
                                        searchResponse(turSNSiteSearchContext, turSolrInstance, turSEResults))
                                .orElse(new TurSNSiteSearchBean()))
                .orElse(new TurSNSiteSearchBean());
    }

    private TurSNSiteSearchBean searchResponse(TurSNSiteSearchContext context,
                                               TurSolrInstance turSolrInstance,
                                               TurSEResults turSEResults) {
        return turSNSiteRepository.findByName(context.getSiteName()).map(turSNSite -> {
            populateMetrics(turSNSite, context, turSEResults.getNumFound());
            List<TurSNSiteFieldExtDto> turSNSiteFieldExtDtoList = turSNSiteFieldExtRepository
                    .findByTurSNSiteAndFacetAndEnabled(turSNSite, 1, 1).stream()
                    .map(turSNSiteFieldExt -> {
                        TurSNSiteFieldExtDto turSNSiteFieldExtDto = new TurSNSiteFieldExtDto(turSNSiteFieldExt);
                        turSNSiteFieldExtDto.setFacetLocales(getFacetLocales(context, turSNSiteFieldExt));
                        return turSNSiteFieldExtDto;
                    }).toList();
            Map<String, TurSNSiteFieldExtDto> facetMap = setFacetMap(turSNSiteFieldExtDtoList);
            if (turSolr.hasGroup(context.getTurSEParameters())) {
                return getSearchBeanForGroup(context, turSolrInstance, turSEResults, turSNSite, facetMap);
            } else {
                return getSearchBeanForResults(context, turSolrInstance, turSEResults, turSNSite, facetMap);
            }
        }).orElse(new TurSNSiteSearchBean());
    }

    @NotNull
    private HashSet<TurSNSiteFieldExtFacet> getFacetLocales(TurSNSiteSearchContext context, TurSNSiteFieldExt turSNSiteFieldExt) {
        return new HashSet<>(
                Collections.singletonList(turSNSiteFieldExtFacetRepository
                        .findByTurSNSiteFieldExtAndLocale(turSNSiteFieldExt, context.getLocale())
                        .stream().findFirst()
                        .orElse(TurSNSiteFieldExtFacet.builder()
                                .locale(context.getLocale())
                                .label(turSNSiteFieldExt.getFacetName())
                                .build())));
    }

    private TurSNSiteSearchBean getSearchBeanForResults(TurSNSiteSearchContext context, TurSolrInstance turSolrInstance,
                                                        TurSEResults turSEResults, TurSNSite turSNSite,
                                                        Map<String, TurSNSiteFieldExtDto> facetMap) {
        return new TurSNSiteSearchBean()
                .setResults(responseDocuments(context, turSolrInstance, turSNSite, facetMap, turSEResults.getResults()))
                .setPagination(responsePagination(context.getUri(), turSEResults))
                .setWidget(responseWidget(context, turSolrInstance, turSNSite, facetMap, turSEResults))
                .setQueryContext(responseQueryContext(turSNSite, turSEResults,
                        context.getLocale()));
    }

    private TurSNSiteSearchBean getSearchBeanForGroup(TurSNSiteSearchContext context, TurSolrInstance turSolrInstance,
                                                      TurSEResults turSEResults, TurSNSite turSNSite,
                                                      Map<String, TurSNSiteFieldExtDto> facetMap) {
        return new TurSNSiteSearchBean()
                .setGroups(responseGroups(context, turSolrInstance, turSNSite, facetMap, turSEResults))
                .setWidget(responseWidget(context, turSolrInstance, turSNSite, facetMap, turSEResults))
                .setQueryContext(responseQueryContext(turSNSite, turSEResults,
                        context.getLocale()));
    }

    private List<TurSNSiteSearchGroupBean> responseGroups(TurSNSiteSearchContext context,
                                                          TurSolrInstance turSolrInstance, TurSNSite turSNSite,
                                                          Map<String, TurSNSiteFieldExtDto> facetMap,
                                                          TurSEResults turSEResults) {
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
                    .setPage(group.getCurrentPage())
                    .setCount(count)
                    .setPageEnd(pageEnd)
                    .setPageStart(Math.min(firstItemOfFullPage, pageEnd))
                    .setLimit(group.getLimit())
                    .setPagination(responsePagination(changeGroupURIForPagination(context.getUri(),
                            group.getName()), group))
                    .setResults(responseDocuments(context, turSolrInstance, turSNSite, facetMap, group.getResults())));
        }));
        return turSNSiteSearchGroupBeans;
    }

    public void populateMetrics(TurSNSite turSNSite, TurSNSiteSearchContext turSNSiteSearchContext, long numFound) {
        if (!turSNSiteSearchContext.getTurSEParameters().getQuery().trim().equals("*")
                && useMetrics(turSNSiteSearchContext)) {
            TurSNSiteMetricAccess turSNSiteMetricAccess = new TurSNSiteMetricAccess();
            turSNSiteMetricAccess.setAccessDate(new Date());
            turSNSiteMetricAccess.setLanguage(turSNSiteSearchContext.getLocale());
            turSNSiteMetricAccess.setTerm(turSNSiteSearchContext.getTurSEParameters().getQuery());
            turSNSiteMetricAccess.setTurSNSite(turSNSite);
            turSNSiteMetricAccess.setNumFound(numFound);
            Optional.ofNullable(turSNSiteSearchContext.getTurSNSitePostParamsBean()).ifPresent(p -> {
                turSNSiteMetricAccess.setTargetingRules(Optional.ofNullable(p.getTargetingRules()).map(HashSet::new)
                        .orElse(new HashSet<>()));
                turSNSiteMetricAccess.setUserId(p.getUserId());
            });
            turSNSiteMetricAccessRepository.save(turSNSiteMetricAccess);
        }
    }

    private boolean useMetrics(TurSNSiteSearchContext turSNSiteSearchContext) {
        return turSNSiteSearchContext.getTurSNSitePostParamsBean() == null
                || turSNSiteSearchContext.getTurSNSitePostParamsBean().isPopulateMetrics();
    }

    private Map<String, TurSNSiteFieldExtDto> setFacetMap(List<TurSNSiteFieldExtDto> turSNSiteFacetFieldExtList) {
        Map<String, TurSNSiteFieldExtDto> facetMap = new HashMap<>();
        turSNSiteFacetFieldExtList.forEach(turSNSiteFacetFieldExt -> {
            if (istTuringEntity(turSNSiteFacetFieldExt.getSnType())) {
                facetMap.put(String.format("%s_%s", TurSNUtils.TURING_ENTITY, turSNSiteFacetFieldExt.getName()),
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

    private void processFilterQuery(List<String> fq, List<String> facetsInFilterQueries, List<String> filterQueryModified) {
        if (!CollectionUtils.isEmpty(fq)) {
            fq.forEach(filterQuery ->
                    TurCommonsUtils.getKeyValueFromColon(filterQuery).ifPresentOrElse(f -> {
                        addFacetInFilterQuery(facetsInFilterQueries, f.getKey());
                        if (!f.getValue().startsWith("\"") && !f.getValue().startsWith("[")) {
                            filterQueryModified.add("%s:\"%s\"".formatted(f.getKey(), f.getValue()));
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
            tr.forEach(targetingRule ->
                    TurCommonsUtils.getKeyValueFromColon(targetingRule).ifPresentOrElse(t -> {
                        if (!t.getValue().startsWith("\"") && !t.getValue().startsWith("[")) {
                            targetingRuleModified.add("%s:\"%s\"".formatted(t.getKey(), t.getValue()));
                        }
                    }, () -> targetingRuleModified.add(targetingRule)));
        }
        return targetingRuleModified;
    }

    private TurSNSiteSearchResultsBean responseDocuments(TurSNSiteSearchContext context,
                                                         TurSolrInstance turSolrInstance,
                                                         TurSNSite turSNSite, Map<String, TurSNSiteFieldExtDto> facetMap,
                                                         List<TurSEResult> seResults) {
        Map<String, TurSNSiteFieldExtDto> fieldExtMap = new HashMap<>();
        turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(turSNSite,
                        1).stream().map(TurSNSiteFieldExtDto::new).toList()
                .forEach(turSNSiteFieldExtDto -> fieldExtMap.put(turSNSiteFieldExtDto.getName(), turSNSiteFieldExtDto));
        List<TurSNSiteSearchDocumentBean> turSNSiteSearchDocumentsBean = new ArrayList<>();
        seResults.forEach(result -> TurSNUtils.addSNDocument(context.getUri(), fieldExtMap, facetMap,
                turSNSiteSearchDocumentsBean, result, false));
        Optional.ofNullable(turSNSite)
                .map(TurSNSite::getSpotlightWithResults)
                .filter(TurSNSearchProcess::isTrue).ifPresent(r ->
                        turSNSpotlightProcess.addSpotlightToResults(context, turSolrInstance, turSNSite, facetMap,
                                fieldExtMap, turSNSiteSearchDocumentsBean));
        return new TurSNSiteSearchResultsBean().setDocument(turSNSiteSearchDocumentsBean);
    }

    private TurSNSiteSearchWidgetBean responseWidget(TurSNSiteSearchContext context, TurSolrInstance turSolrInstance,
                                                     TurSNSite turSNSite,
                                                     Map<String, TurSNSiteFieldExtDto> facetMap,
                                                     TurSEResults turSEResults) {
        return new TurSNSiteSearchWidgetBean()
                .setFacet(responseFacet(context, turSolrInstance, turSNSite,
                        requestFilterQuery(context.getTurSEParameters().getFilterQueries().getFq())
                                .getFacetsInFilterQueries(), facetMap, turSEResults))
                .setSecondaryFacet(responseSecondaryFacet(context, turSolrInstance, turSNSite,
                        requestFilterQuery(context.getTurSEParameters().getFilterQueries().getFq())
                                .getFacetsInFilterQueries(), facetMap, turSEResults))
                .setFacetToRemove(responseFacetToRemove(context, turSNSite))
                .setSimilar(responseMLT(turSNSite, turSEResults))
                .setSpellCheck(new TurSNSiteSpellCheckBean(context, turSEResults.getSpellCheck()))
                .setLocales(responseLocales(turSNSite, context.getUri()))
                .setSpotlights(responseSpotlights(context, turSNSite))
                .setCleanUpFacets(responseCleanUpFacet(context, turSNSite));
    }

    private List<TurSNSiteSpotlightDocumentBean> responseSpotlights(TurSNSiteSearchContext context,
                                                                    TurSNSite turSNSite) {
        List<TurSNSiteSpotlightDocumentBean> turSNSiteSpotlightDocumentBeans = new ArrayList<>();
        turSNSpotlightProcess
                .getSpotlightsFromQuery(context, turSNSite)
                .forEach((key, value) -> value
                        .forEach(document -> turSNSiteSpotlightDocumentBeans.add(new TurSNSiteSpotlightDocumentBean()
                                .setId(document.getId())
                                .setContent(document.getContent())
                                .setLink(document.getLink())
                                .setPosition(document.getPosition())
                                .setReferenceId(document.getReferenceId())
                                .setTitle(document.getTitle())
                                .setType(document.getType()))));
        turSNSiteSpotlightDocumentBeans.sort(Comparator.comparingInt(TurSNSiteSpotlightDocumentBean::getPosition));
        return turSNSiteSpotlightDocumentBeans;
    }

    public List<TurSNSiteLocaleBean> responseLocales(TurSNSite turSNSite, URI uri) {
        List<TurSNSiteLocaleBean> turSNSiteLocaleBeans = new ArrayList<>();
        turSNSiteLocaleRepository.findByTurSNSite(Sort.by(Sort.Order.asc(LANGUAGE).ignoreCase()), turSNSite)
                .forEach(turSNSiteLocale -> turSNSiteLocaleBeans.add(new TurSNSiteLocaleBean()
                        .setLocale(turSNSiteLocale.getLanguage())
                        .setLink(TurCommonsUtils
                                .addOrReplaceParameter(uri, TurSNParamType.LOCALE,
                                        turSNSiteLocale.getLanguage(), true).toString())));

        return turSNSiteLocaleBeans;
    }

    private TurSNSiteSearchQueryContextBean responseQueryContext(TurSNSite turSNSite, TurSEResults turSEResults,
                                                                 Locale locale) {
        int lastItemOfFullPage = (int) turSEResults.getStart() + turSEResults.getLimit();
        int firstItemOfFullPage = (int) turSEResults.getStart() + 1;
        int count = (int) turSEResults.getNumFound();
        int pageEnd = Math.min(lastItemOfFullPage, count);
        return new TurSNSiteSearchQueryContextBean()
                .setQuery(new TurSNSiteSearchQueryContextQueryBean()
                        .setQueryString(turSEResults.getQueryString())
                        .setSort(turSEResults.getSort())
                        .setLocale(locale))
                .setDefaultFields(defaultFields(turSNSite))
                .setPageCount(turSEResults.getPageCount())
                .setPage(turSEResults.getCurrentPage())
                .setCount(count)
                .setPageEnd(pageEnd)
                .setPageStart(Math.min(firstItemOfFullPage, pageEnd))
                .setLimit(turSEResults.getLimit())
                .setOffset(0)
                .setResponseTime(turSEResults.getElapsedTime())
                .setIndex(turSNSite.getName())
                .setFacetType(turSNSite.getFacetType() != null ? turSNSite.getFacetType().toString() :
                        TurSNSiteFacetFieldEnum.AND.toString())
                .setFacetItemType(turSNSite.getFacetItemType() != null ? turSNSite.getFacetItemType().toString() :
                        TurSNSiteFacetFieldEnum.AND.toString());
    }

    private TurSNSiteSearchDefaultFieldsBean defaultFields(TurSNSite turSNSite) {
        return new TurSNSiteSearchDefaultFieldsBean()
                .setDate(turSNSite.getDefaultDateField())
                .setDescription(turSNSite.getDefaultDescriptionField())
                .setImage(turSNSite.getDefaultImageField())
                .setText(turSNSite.getDefaultTextField())
                .setTitle(turSNSite.getDefaultTitleField())
                .setUrl(turSNSite.getDefaultURLField());
    }

    private List<TurSESimilarResult> responseMLT(TurSNSite turSNSite, TurSEResults turSEResults) {
        return hasMLT(turSNSite, turSEResults) ? turSEResults.getSimilarResults() : Collections.emptyList();
    }

    private boolean hasMLT(TurSNSite turSNSite, TurSEResults turSEResults) {
        return isTrue(turSNSite.getMlt()) && turSEResults.getSimilarResults() != null
                && !turSEResults.getSimilarResults().isEmpty();
    }

    private TurSNSiteSearchFacetBean responseFacetToRemove(TurSNSiteSearchContext context,
                                                           TurSNSite turSNSite) {
        if (!CollectionUtils.isEmpty(context.getTurSEParameters().getFilterQueries().getFq())) {
            TurSNFacetTypeContext turSNFacetTypeContext = new TurSNFacetTypeContext(null, turSNSite,
                    context.getTurSEParameters().getFilterQueries());
            List<TurSNSiteSearchFacetItemBean> turSNSiteSearchFacetToRemoveItemBeans = new ArrayList<>();
            context.getTurSEParameters().getFilterQueries().getFq().forEach(facetToRemove ->
                    TurCommonsUtils.getKeyValueFromColon(facetToRemove).ifPresent(f -> {
                        if (turSolr.getFacetsInFilterQuery(turSNFacetTypeContext).contains(f.getKey())) {
                            turSNSiteSearchFacetToRemoveItemBeans.add(new TurSNSiteSearchFacetItemBean()
                                    .setLabel(f.getValue().replace("\"", ""))
                                    .setLink(TurSNUtils.removeFilterQuery(context.getUri(), facetToRemove).toString())
                                    .setSelected(true));
                        }
                    }));
            if (!turSNSiteSearchFacetToRemoveItemBeans.isEmpty()) {
                return getTurSNSiteSearchFacetBean(turSNSiteSearchFacetToRemoveItemBeans);
            }
        }
        return new TurSNSiteSearchFacetBean();
    }

    private String responseCleanUpFacet(TurSNSiteSearchContext context,
                                        TurSNSite turSNSite) {
        return TurSNUtils.removeFilterQueryByFieldNames(context.getUri(),
                turSolr.getFacetsInFilterQuery(new TurSNFacetTypeContext(null, turSNSite,
                        context.getTurSEParameters().getFilterQueries()))).toString();
    }

    private List<TurSNSiteSearchFacetBean> responseFacet(TurSNSiteSearchContext context,
                                                         TurSolrInstance turSolrInstance,
                                                         TurSNSite turSNSite,
                                                         List<String> facetsInFilterQueries,
                                                         Map<String, TurSNSiteFieldExtDto> facetMap,
                                                         TurSEResults turSEResults) {
        if (facetIsEnabled(turSNSite, turSEResults)) {
            List<TurSNSiteSearchFacetBean> turSNSiteSearchFacetBeans = new ArrayList<>();
            turSEResults.getFacetResults()
                    .stream().filter(f -> showMainFacet(facetsInFilterQueries, facetMap, f, turSNSite))
                    .forEach(facet -> {
                        if (facetMap.containsKey(facet.getFacet())) {
                            TurSNFacetTypeContext turSNFacetTypeContext =
                                    new TurSNFacetTypeContext(facetMap.get(facet.getFacet()), turSNSite,
                                            context.getTurSEParameters().getFilterQueries());
                            getFacetResponse(context, facetMap, getFacetResult(context, turSolrInstance, turSEResults,
                                            turSNFacetTypeContext),
                                    turSNSiteSearchFacetBeans);
                        }
                    });
            return turSNSiteSearchFacetBeans;
        }
        return Collections.emptyList();
    }

    private static boolean facetIsEnabled(TurSNSite turSNSite, TurSEResults turSEResults) {
        return turSNSite.getFacet() == 1 && Optional.ofNullable(turSEResults.getFacetResults()).isPresent();
    }


    private List<TurSNSiteSearchFacetBean> responseSecondaryFacet(TurSNSiteSearchContext context,
                                                                  TurSolrInstance turSolrInstance,
                                                                  TurSNSite turSNSite,
                                                                  List<String> facetsInFilterQueries,
                                                                  Map<String, TurSNSiteFieldExtDto> facetMap,
                                                                  TurSEResults turSEResults) {
        if (facetIsEnabled(turSNSite, turSEResults)) {
            List<TurSNSiteSearchFacetBean> turSNSiteSearchFacetBeans = new ArrayList<>();
            turSEResults.getFacetResults()
                    .stream().filter(f -> showSecondaryFacet(facetsInFilterQueries, facetMap, f, turSNSite))
                    .forEach(facet -> {
                        if (facetMap.containsKey(facet.getFacet())) {
                            TurSNFacetTypeContext turSNFacetTypeContext =
                                    new TurSNFacetTypeContext(facetMap.get(facet.getFacet()), turSNSite,
                                            context.getTurSEParameters().getFilterQueries());
                            getFacetResponse(context, facetMap, getFacetResult(context, turSolrInstance, turSEResults,
                                            turSNFacetTypeContext),
                                    turSNSiteSearchFacetBeans);
                        }
                    });
            return turSNSiteSearchFacetBeans;
        }
        return Collections.emptyList();
    }

    @NotNull
    private FacetResult getFacetResult(TurSNSiteSearchContext context, TurSolrInstance turSolrInstance,
                                       TurSEResults turSEResults, TurSNFacetTypeContext turSNFacetTypeContext) {
        String facetName = turSNFacetTypeContext.getTurSNSiteFacetFieldExtDto().getName();
        String facetTypeAndFacetItemTypeValues = TurSolr.getFacetTypeAndFacetItemTypeValues(turSNFacetTypeContext);
        List<String> usedFacetItems = getUsedFacetItems(context);
        if (facetTypeAndFacetItemTypeValues.equals(AND_OR) &&
                turSolr.getFqFields(turSNFacetTypeContext
                        .getQueryParameters()).contains(facetName)) {
            TurSEFacetResult turSEFacetResult = turSolr.retrieveFacetSolrFromSN(turSolrInstance,
                            getContextSearchFacet(context, facetName), facetName)
                    .map(turSEFacetResults ->
                            turSEFacetResults.getFacetResults().stream()
                                    .filter(ff -> ff.getFacet().equals(facetName))
                                    .findFirst()
                                    .orElseGet(() -> getTurSEFacetResultDefault(turSEResults, facetName))
                    )
                    .orElseGet(() -> getTurSEFacetResultDefault(turSEResults, facetName));
            return new FacetResult(usedFacetItems, facetTypeAndFacetItemTypeValues, turSEFacetResult);
        }
        TurSEFacetResult turSEFacetResult = getTurSEFacetResultDefault(turSEResults,
                facetName);
        return new FacetResult(usedFacetItems, facetTypeAndFacetItemTypeValues, turSEFacetResult);

    }

    @NotNull
    private static TurSNSiteSearchContext getContextSearchFacet(TurSNSiteSearchContext context, String facetName) {
        TurSNSiteSearchContext contextSearchFacet = SerializationUtils.clone(context);
        contextSearchFacet.getTurSEParameters().getFilterQueries()
                .setFq(contextSearchFacet.getTurSEParameters()
                        .getFilterQueries().getFq().stream()
                        .filter(fq -> !fq.startsWith(facetName))
                        .toList());
        contextSearchFacet.getTurSEParameters().setRows(-1);
        return contextSearchFacet;
    }

    @NotNull
    private static List<String> getUsedFacetItems(TurSNSiteSearchContext context) {
        return Optional.ofNullable(context.getTurSEParameters())
                .map(TurSEParameters::getFilterQueries)
                .map(TurSEFilterQueryParameters::getFq)
                .orElse(Collections.emptyList());
    }

    @NotNull
    private static TurSEFacetResult getTurSEFacetResultDefault(TurSEResults turSEResults, String facet) {
        return turSEResults.getFacetResults().stream()
                .filter(ff -> ff.getFacet().equals(facet))
                .findFirst().orElse(new TurSEFacetResult());
    }

    private record FacetResult(List<String> usedFacetItems, String facetTypeAndFacetItemTypeValues,
                               TurSEFacetResult turSEFacetResult) {
    }

    private static void getFacetResponse(TurSNSiteSearchContext context, Map<String, TurSNSiteFieldExtDto> facetMap,
                                         FacetResult facetResult,
                                         List<TurSNSiteSearchFacetBean> turSNSiteSearchFacetBeans) {
        List<TurSNSiteSearchFacetItemBean> turSNSiteSearchFacetItemBeans = new ArrayList<>();
        if (facetMap.containsKey(facetResult.turSEFacetResult.getFacet())) {

            TurSNSiteFieldExtDto turSNSiteFieldExtDto = facetMap.get(facetResult.turSEFacetResult.getFacet());
            boolean showAllFacetItems = turSNSiteFieldExtDto.getShowAllFacetItems() != null &&
                    turSNSiteFieldExtDto.getShowAllFacetItems();
            facetResult.turSEFacetResult.getTurSEFacetResultAttr().values().forEach(facetItem -> {
                        final String fq = facetResult.turSEFacetResult.getFacet() + ":" + facetItem.getAttribute();
                        if (showAllFacetItems || facetItem.getCount() > 0 ||
                                (facetResult.usedFacetItems.contains(fq) &&
                                        facetResult.facetTypeAndFacetItemTypeValues.equals(AND_OR))
                        ) {
                            boolean selected = facetResult.usedFacetItems.contains(fq);
                            turSNSiteSearchFacetItemBeans.add(new TurSNSiteSearchFacetItemBean()
                                    .setCount(facetItem.getCount())
                                    .setLabel(facetItem.getAttribute())
                                    .setSelected(selected)
                                    .setLink(getLink(context, selected, fq,
                                            facetResult.facetTypeAndFacetItemTypeValues, turSNSiteFieldExtDto)));

                        }
                    }
            );
            if (!turSNSiteSearchFacetItemBeans.isEmpty()) {
                turSNSiteSearchFacetBeans.add(getTurSNSiteSearchFacetBean(context, turSNSiteFieldExtDto,
                        turSNSiteSearchFacetItemBeans));
            }
        }

    }

    private static String getLink(TurSNSiteSearchContext context, boolean selected, String fq,
                                  String facetTypeAndFacetItemTypeValues, TurSNSiteFieldExtDto turSNSiteFieldExtDto) {
        if (facetTypeAndFacetItemTypeValues.endsWith(FACET_ITEM_AND)) {
            if (selected) {
                return TurSNUtils
                        .removeFilterQuery(context.getUri(), fq)
                        .toString();
            } else {
                URI uri = TurSNUtils.removeFilterQueryByFieldName(context.getUri(),
                        turSNSiteFieldExtDto.getName());
                return TurSNUtils
                        .addFilterQuery(uri, fq)
                        .toString();
            }
        } else {
            return selected ?
                    TurSNUtils
                            .removeFilterQuery(context.getUri(), fq)
                            .toString() :
                    TurSNUtils
                            .addFilterQuery(context.getUri(), fq)
                            .toString();
        }
    }

    private List<TurSNSiteSearchPaginationBean> responsePagination(URI uri, TurSEGenericResults turSEResults) {
        List<TurSNSiteSearchPaginationBean> pagination = new ArrayList<>();
        if (turSEResults.getCurrentPage() > 1) {
            pagination.add(setFirstPage(uri));
            if (turSEResults.getCurrentPage() <= turSEResults.getPageCount()) {
                pagination.add(setPreviousPage(uri, turSEResults));
            }
        }
        IntStream.rangeClosed(getFirstPagination(turSEResults), getLastPagination(turSEResults)).forEach(page ->
                pagination.add(isCurrentPage(turSEResults, page) ? setCurrentPage(uri, page) :
                        setOtherPages(uri, page)));
        if (isNotLastPage(turSEResults)) {
            if (turSEResults.getCurrentPage() <= turSEResults.getPageCount()) {
                pagination.add(setNextPage(uri, turSEResults));
            }
            pagination.add(setLastPage(uri, turSEResults));
        }
        return pagination;
    }

    private URI changeGroupURIForPagination(URI uri, String fieldName) {
        return TurCommonsUtils.addOrReplaceParameter(TurSNUtils.removeQueryStringParameter(uri, GROUP),
                TurSNParamType.FILTER_QUERIES_DEFAULT, fieldName, true);
    }

    private TurSNSiteSearchPaginationBean setPreviousPage(URI uri, TurSEGenericResults turSEResults) {
        return new TurSNSiteSearchPaginationBean()
                .setType(TurSNPaginationType.PREVIOUS)
                .setHref(TurCommonsUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE,
                        Integer.toString(turSEResults.getCurrentPage() - 1), true).toString())
                .setText(PREVIOUS)
                .setPage(turSEResults.getCurrentPage() - 1);
    }

    private TurSNSiteSearchPaginationBean setFirstPage(URI uri) {
        return new TurSNSiteSearchPaginationBean()
                .setType(TurSNPaginationType.FIRST)
                .setHref(TurCommonsUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE, Integer.toString(1), true)
                        .toString())
                .setText(FIRST)
                .setPage(1);
    }

    private TurSNSiteSearchPaginationBean setLastPage(URI uri, TurSEGenericResults turSEResults) {
        return new TurSNSiteSearchPaginationBean()
                .setType(TurSNPaginationType.LAST)
                .setHref(TurCommonsUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE,
                        Integer.toString(turSEResults.getPageCount()), true).toString())
                .setText(LAST)
                .setPage(turSEResults.getPageCount());
    }

    private TurSNSiteSearchPaginationBean setNextPage(URI uri, TurSEGenericResults turSEResults) {
        return new TurSNSiteSearchPaginationBean()
                .setType(TurSNPaginationType.NEXT)
                .setHref(TurCommonsUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE,
                        Integer.toString(turSEResults.getCurrentPage() + 1), true).toString())
                .setText(NEXT)
                .setPage(turSEResults.getCurrentPage() + 1);

    }

    private TurSNSiteSearchPaginationBean setGenericPages(URI uri, int page,
                                                          TurSNPaginationType type) {
        return new TurSNSiteSearchPaginationBean()
                .setHref(TurCommonsUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE, Integer.toString(page), true)
                        .toString())
                .setText(Integer.toString(page))
                .setType(type)
                .setPage(page);
    }

    private TurSNSiteSearchPaginationBean setOtherPages(URI uri, int page) {
        return setGenericPages(uri, page, TurSNPaginationType.PAGE);
    }

    private TurSNSiteSearchPaginationBean setCurrentPage(URI uri, int page) {
        return setGenericPages(uri, page, TurSNPaginationType.CURRENT);
    }

    private int getLastPagination(TurSEGenericResults turSEResults) {
        return Math.min(turSEResults.getCurrentPage() + 3, turSEResults.getPageCount());
    }

    private int getFirstPagination(TurSEGenericResults turSEResults) {
        return turSEResults.getCurrentPage() - 3 > 0 ? turSEResults.getCurrentPage() - 3 : 1;
    }
}
