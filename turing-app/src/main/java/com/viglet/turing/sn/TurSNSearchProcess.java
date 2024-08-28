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
import com.viglet.turing.sn.spotlight.TurSNSpotlightProcess;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import org.apache.commons.collections.CollectionUtils;
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
                        turSNSiteFieldExtDto.setFacetLocales(
                                new HashSet<>(
                                        Collections.singletonList(turSNSiteFieldExtFacetRepository
                                                .findByTurSNSiteFieldExtAndLocale(turSNSiteFieldExt, context.getLocale())
                                                .stream().findFirst()
                                                .orElse(TurSNSiteFieldExtFacet.builder()
                                                        .locale(context.getLocale())
                                                        .label(turSNSiteFieldExt.getFacetName())
                                                        .build()))));
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

    private TurSNSiteSearchBean getSearchBeanForResults(TurSNSiteSearchContext context, TurSolrInstance turSolrInstance,
                                                        TurSEResults turSEResults, TurSNSite turSNSite,
                                                        Map<String, TurSNSiteFieldExtDto> facetMap) {
        return new TurSNSiteSearchBean()
                .setResults(responseDocuments(context, turSolrInstance, turSNSite, facetMap, turSEResults.getResults()))
                .setPagination(responsePagination(context.getUri(), turSEResults))
                .setWidget(responseWidget(context, turSNSite, facetMap, turSEResults))
                .setQueryContext(responseQueryContext(turSNSite, turSEResults,
                        context.getLocale()));
    }

    private TurSNSiteSearchBean getSearchBeanForGroup(TurSNSiteSearchContext context, TurSolrInstance turSolrInstance,
                                                      TurSEResults turSEResults, TurSNSite turSNSite,
                                                      Map<String, TurSNSiteFieldExtDto> facetMap) {
        return new TurSNSiteSearchBean()
                .setGroups(responseGroups(context, turSolrInstance, turSNSite, facetMap, turSEResults))
                .setWidget(responseWidget(context, turSNSite, facetMap, turSEResults))
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

    private static boolean istTuringEntity(TurSNFieldType snType) {
        return Collections.unmodifiableSet(EnumSet.of(TurSNFieldType.NER, TurSNFieldType.THESAURUS)).contains(snType);
    }

    private TurSNSiteFilterQueryBean requestFilterQuery(List<String> fq) {
        List<String> hiddenFilterQuery = new ArrayList<>();
        List<String> filterQueryModified = new ArrayList<>();
        processFilterQuery(fq, hiddenFilterQuery, filterQueryModified);
        return new TurSNSiteFilterQueryBean()
                .setHiddenItems(hiddenFilterQuery)
                .setItems(filterQueryModified);
    }

    private void processFilterQuery(List<String> fq, List<String> hiddenFilterQuery, List<String> filterQueryModified) {
        if (!CollectionUtils.isEmpty(fq)) {
            fq.forEach(filterQuery -> {
                String[] filterParts = filterQuery.split(":");
                if (filterParts.length == 2) {
                    addHiddenFilterQuery(hiddenFilterQuery, filterParts);
                    if (!filterParts[1].startsWith("\"") && !filterParts[1].startsWith("[")) {
                        filterParts[1] = "\"" + filterParts[1] + "\"";
                        filterQueryModified.add(filterParts[0] + ":" + filterParts[1]);
                    }
                } else {
                    filterQueryModified.add(filterQuery);
                }
            });
        }
    }

    private void addHiddenFilterQuery(List<String> hiddenFilterQuery, String[] filterParts) {
        if (!hiddenFilterQuery.contains(filterParts[0])) {
            hiddenFilterQuery.add(filterParts[0]);
        }
    }

    public List<String> requestTargetingRules(List<String> tr) {
        List<String> targetingRuleModified = new ArrayList<>();
        if (!CollectionUtils.isEmpty(tr)) {
            tr.forEach(targetingRule -> {
                String[] targetingRuleParts = targetingRule.split(":");
                if (targetingRuleParts.length == 2) {
                    if (!targetingRuleParts[1].startsWith("\"") && !targetingRuleParts[1].startsWith("[")) {
                        targetingRuleParts[1] = "\"" + targetingRuleParts[1] + "\"";
                        targetingRuleModified.add(targetingRuleParts[0] + ":" + targetingRuleParts[1]);
                    }
                } else {
                    targetingRuleModified.add(targetingRule);
                }
            });
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

    private static boolean isTrue(Integer value) {
        return value == 1;
    }

    private TurSNSiteSearchWidgetBean responseWidget(TurSNSiteSearchContext context, TurSNSite turSNSite,
                                                     Map<String, TurSNSiteFieldExtDto> facetMap,
                                                     TurSEResults turSEResults) {
        return new TurSNSiteSearchWidgetBean()
                .setFacet(responseFacet(context, turSNSite,
                        requestFilterQuery(context.getTurSEParameters().getFilterQueries().getFq())
                                .getHiddenItems(), facetMap, turSEResults))
                .setFacetToRemove(responseFacetToRemove(context))
                .setSimilar(responseMLT(turSNSite, turSEResults))
                .setSpellCheck(new TurSNSiteSpellCheckBean(context, turSEResults.getSpellCheck()))
                .setLocales(responseLocales(turSNSite, context.getUri()))
                .setSpotlights(responseSpotlights(context, turSNSite));
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
                                        turSNSiteLocale.getLanguage()).toString())));

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
                .setFacetType(turSNSite.getFacetType().toString());
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

    private TurSNSiteSearchFacetBean responseFacetToRemove(TurSNSiteSearchContext context) {
        if (!CollectionUtils.isEmpty(context.getTurSEParameters().getFilterQueries().getFq())) {
            List<TurSNSiteSearchFacetItemBean> turSNSiteSearchFacetToRemoveItemBeans = new ArrayList<>();
            context.getTurSEParameters().getFilterQueries().getFq().forEach(facetToRemove -> {
                String[] facetToRemoveParts = facetToRemove.split(":", 2);
                if (facetToRemoveParts.length == 2) {
                    turSNSiteSearchFacetToRemoveItemBeans.add(new TurSNSiteSearchFacetItemBean()
                            .setLabel(facetToRemoveParts[1].replace("\"", ""))
                            .setLink(TurSNUtils.removeFilterQuery(context.getUri(), facetToRemove).toString())
                            .setSelected(true));
                }
            });
            return getTurSNSiteSearchFacetBean(turSNSiteSearchFacetToRemoveItemBeans);
        }
        return new TurSNSiteSearchFacetBean();
    }

    private List<TurSNSiteSearchFacetBean> responseFacet(TurSNSiteSearchContext context,
                                                         TurSNSite turSNSite, List<String> hiddenFilterQuery,
                                                         Map<String, TurSNSiteFieldExtDto> facetMap,
                                                         TurSEResults turSEResults) {
        if (turSNSite.getFacet() == 1) {
            List<String> usedFacetItems = Optional.ofNullable(context.getTurSEParameters())
                    .map(TurSEParameters::getFilterQueries)
                    .map(TurSEFilterQueryParameters::getFq)
                    .orElse(Collections.emptyList());
            List<TurSNSiteSearchFacetBean> turSNSiteSearchFacetBeans = new ArrayList<>();
            turSEResults.getFacetResults().forEach(facet -> {
                if (showFacet(hiddenFilterQuery, facetMap, facet, turSNSite)) {
                    List<TurSNSiteSearchFacetItemBean> turSNSiteSearchFacetItemBeans = new ArrayList<>();
                    facet.getTurSEFacetResultAttr().values().forEach(facetItem -> {
                        final String fq = facet.getFacet() + ":" + facetItem.getAttribute();
                        turSNSiteSearchFacetItemBeans.add(new TurSNSiteSearchFacetItemBean()
                                .setCount(facetItem.getCount())
                                .setLabel(facetItem.getAttribute())
                                .setLink(TurSNUtils
                                        .addFilterQuery(context.getUri(), fq).toString()
                                        .replace(":", "\\:"))
                                .setSelected(usedFacetItems.contains(fq))
                        );

                    });
                    turSNSiteSearchFacetBeans.add(getTurSNSiteSearchFacetBean(context, facetMap.get(facet.getFacet()),
                            turSNSiteSearchFacetItemBeans));
                }
            });
            return turSNSiteSearchFacetBeans;
        }
        return Collections.emptyList();
    }

    private static boolean showFacetByFacetType(TurSNSite turSNSite) {
        if (turSNSite.getFacetType() == null) return false;
        else return switch (turSNSite.getFacetType()) {
            case OR -> true;
            case AND -> false;
        };
    }

    private static boolean showFacet(List<String> hiddenFilterQuery,
                                     Map<String, TurSNSiteFieldExtDto> facetMap,
                                     TurSEFacetResult facet, TurSNSite turSNSite) {
        return facetMap.containsKey(facet.getFacet())
                && (!hiddenFilterQuery.contains(facet.getFacet()) || showFacetByFacetType(turSNSite))
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
                .setMultiValued(isTrue(turSNSiteFieldExtDto.getMultiValued()));
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

    private static boolean isCurrentPage(TurSEGenericResults turSEResults, int page) {
        return page == turSEResults.getCurrentPage();
    }

    private static boolean isNotLastPage(TurSEGenericResults turSEResults) {
        return turSEResults.getCurrentPage() != turSEResults.getPageCount() && turSEResults.getPageCount() > 1;
    }

    private URI changeGroupURIForPagination(URI uri, String fieldName) {
        return TurCommonsUtils.addOrReplaceParameter(TurSNUtils.removeQueryField(uri, GROUP),
                TurSNParamType.FILTER_QUERIES_DEFAULT, fieldName);
    }

    private TurSNSiteSearchPaginationBean setPreviousPage(URI uri, TurSEGenericResults turSEResults) {
        return new TurSNSiteSearchPaginationBean()
                .setType(TurSNPaginationType.PREVIOUS)
                .setHref(TurCommonsUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE,
                        Integer.toString(turSEResults.getCurrentPage() - 1)).toString())
                .setText(PREVIOUS)
                .setPage(turSEResults.getCurrentPage() - 1);
    }

    private TurSNSiteSearchPaginationBean setFirstPage(URI uri) {
        return new TurSNSiteSearchPaginationBean()
                .setType(TurSNPaginationType.FIRST)
                .setHref(TurCommonsUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE, Integer.toString(1))
                        .toString())
                .setText(FIRST)
                .setPage(1);
    }

    private TurSNSiteSearchPaginationBean setLastPage(URI uri, TurSEGenericResults turSEResults) {
        return new TurSNSiteSearchPaginationBean()
                .setType(TurSNPaginationType.LAST)
                .setHref(TurCommonsUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE,
                        Integer.toString(turSEResults.getPageCount())).toString())
                .setText(LAST)
                .setPage(turSEResults.getPageCount());
    }

    private TurSNSiteSearchPaginationBean setNextPage(URI uri, TurSEGenericResults turSEResults) {
        return new TurSNSiteSearchPaginationBean()
                .setType(TurSNPaginationType.NEXT)
                .setHref(TurCommonsUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE,
                        Integer.toString(turSEResults.getCurrentPage() + 1)).toString())
                .setText(NEXT)
                .setPage(turSEResults.getCurrentPage() + 1);

    }

    private TurSNSiteSearchPaginationBean setGenericPages(URI uri, int page,
                                                          TurSNPaginationType type) {
        return new TurSNSiteSearchPaginationBean()
                .setHref(TurCommonsUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE, Integer.toString(page))
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
