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
import com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult;
import com.viglet.turing.commons.se.similar.TurSESimilarResult;
import com.viglet.turing.commons.sn.bean.*;
import com.viglet.turing.commons.sn.bean.spellcheck.TurSNSiteSpellCheckBean;
import com.viglet.turing.commons.sn.pagination.TurSNPaginationType;
import com.viglet.turing.commons.sn.search.TurSNParamType;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.metric.TurSNSiteMetricAccess;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
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

/**
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
@Component
public class TurSNSearchProcess {
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSolrInstanceProcess turSolrInstanceProcess;
    private final TurSolr turSolr;
    private final TurSNSpotlightProcess turSNSpotlightProcess;
    private final TurSNSiteMetricAccessRepository turSNSiteMetricAccessRepository;

    @Inject
    public TurSNSearchProcess(TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
                              TurSNSiteRepository turSNSiteRepository,
                              TurSNSiteLocaleRepository turSNSiteLocaleRepository,
                              TurSolrInstanceProcess turSolrInstanceProcess,
                              TurSolr turSolr,
                              TurSNSpotlightProcess turSNSpotlightProcess,
                              TurSNSiteMetricAccessRepository turSNSiteMetricAccessRepository) {
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
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

    private TurSNSiteSearchBean searchResponse(TurSNSiteSearchContext turSNSiteSearchContext,
                                               TurSolrInstance turSolrInstance,
                                               TurSEResults turSEResults) {
        return turSNSiteRepository.findByName(turSNSiteSearchContext.getSiteName()).map(turSNSite -> {
            populateMetrics(turSNSite, turSNSiteSearchContext, turSEResults.getNumFound());
            List<TurSNSiteFieldExt> turSNSiteFacetFieldExtList = turSNSiteFieldExtRepository
                    .findByTurSNSiteAndFacetAndEnabled(turSNSite, 1, 1);
            Map<String, TurSNSiteFieldExt> facetMap = setFacetMap(turSNSiteFacetFieldExtList);
            return new TurSNSiteSearchBean()
                    .setResults(responseDocuments(turSNSiteSearchContext,
                            turSolrInstance, turSNSite, facetMap, turSEResults.getResults()))
                    .setGroups(responseGroups(turSNSiteSearchContext, turSolrInstance,
                            turSNSite, facetMap, turSEResults))
                    .setPagination(responsePagination(turSNSiteSearchContext.getUri(), turSEResults))
                    .setWidget(responseWidget(turSNSiteSearchContext, turSNSite,
                            turSNSiteFacetFieldExtList, facetMap, turSEResults))
                    .setQueryContext(responseQueryContext(turSNSite, turSEResults,
                            turSNSiteSearchContext.getLocale()));
        }).orElse(new TurSNSiteSearchBean());
    }

    private List<TurSNSiteSearchGroupBean> responseGroups(TurSNSiteSearchContext context,
                                                          TurSolrInstance turSolrInstance, TurSNSite turSNSite,
                                                          Map<String, TurSNSiteFieldExt> facetMap,
                                                          TurSEResults turSEResults) {
        List<TurSNSiteSearchGroupBean> turSNSiteSearchGroupBeans = new ArrayList<>();
        if (turSEResults.getGroups() != null) {
            turSEResults.getGroups().forEach(group -> {
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
            });
        }
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
            if (turSNSiteSearchContext.getTurSNSitePostParamsBean() != null) {
                turSNSiteMetricAccess.setTargetingRules(
                        turSNSiteSearchContext.getTurSNSitePostParamsBean().getTargetingRules() != null
                                ? new HashSet<>(turSNSiteSearchContext.getTurSNSitePostParamsBean().getTargetingRules())
                                : Collections.emptySet());
                turSNSiteMetricAccess.setUserId(turSNSiteSearchContext.getTurSNSitePostParamsBean().getUserId());
            }
            turSNSiteMetricAccessRepository.save(turSNSiteMetricAccess);
        }
    }

    private boolean useMetrics(TurSNSiteSearchContext turSNSiteSearchContext) {
        return turSNSiteSearchContext.getTurSNSitePostParamsBean() == null
                || turSNSiteSearchContext.getTurSNSitePostParamsBean().isPopulateMetrics();
    }

    private Map<String, TurSNSiteFieldExt> setFacetMap(List<TurSNSiteFieldExt> turSNSiteFacetFieldExtList) {
        Map<String, TurSNSiteFieldExt> facetMap = new HashMap<>();
        turSNSiteFacetFieldExtList.forEach(turSNSiteFacetFieldExt -> {
            if (istTuringEntity(turSNSiteFacetFieldExt.getSnType())) {
                facetMap.put(String.format("%s_%s", TurSNUtils.TURING_ENTITY, turSNSiteFacetFieldExt.getName()),
                        turSNSiteFacetFieldExt);
            } else {
                facetMap.put(turSNSiteFacetFieldExt.getName(), turSNSiteFacetFieldExt);
            }
            facetMap.put(turSNSiteFacetFieldExt.getName(), turSNSiteFacetFieldExt);
        });

        return facetMap;
    }

    private static boolean istTuringEntity(TurSNFieldType snType) {
        return snType == TurSNFieldType.NER || snType == TurSNFieldType.THESAURUS;
    }

    private TurSNSiteFilterQueryBean requestFilterQuery(List<String> fq) {
        List<String> hiddenFilterQuery = new ArrayList<>();
        List<String> filterQueryModified = new ArrayList<>();
        processFilterQuery(fq, hiddenFilterQuery, filterQueryModified);
        TurSNSiteFilterQueryBean turSNSiteFilterQueryBean = new TurSNSiteFilterQueryBean();
        turSNSiteFilterQueryBean.setHiddenItems(hiddenFilterQuery);
        turSNSiteFilterQueryBean.setItems(filterQueryModified);
        return turSNSiteFilterQueryBean;
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
                                                         TurSNSite turSNSite, Map<String, TurSNSiteFieldExt> facetMap,
                                                         List<TurSEResult> seResults) {
        Map<String, TurSNSiteFieldExt> fieldExtMap = new HashMap<>();
        List<TurSNSiteFieldExt> turSNSiteFieldExtList = turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(turSNSite,
                1);
        turSNSiteFieldExtList
                .forEach(turSNSiteFieldExt -> fieldExtMap.put(turSNSiteFieldExt.getName(), turSNSiteFieldExt));
        List<TurSNSiteSearchDocumentBean> turSNSiteSearchDocumentsBean = new ArrayList<>();
        seResults.forEach(result -> TurSNUtils.addSNDocument(context.getUri(), fieldExtMap, facetMap,
                turSNSiteSearchDocumentsBean, result, false));
        if (turSNSite != null && turSNSite.getSpotlightWithResults() != null
                && turSNSite.getSpotlightWithResults() == 1) {
            turSNSpotlightProcess.addSpotlightToResults(context, turSolrInstance, turSNSite, facetMap, fieldExtMap,
                    turSNSiteSearchDocumentsBean);
        }
        return new TurSNSiteSearchResultsBean().setDocument(turSNSiteSearchDocumentsBean);

    }

    private TurSNSiteSearchWidgetBean responseWidget(TurSNSiteSearchContext context, TurSNSite turSNSite,
                                                     List<TurSNSiteFieldExt> turSNSiteFacetFieldList, Map<String,
            TurSNSiteFieldExt> facetMap,
                                                     TurSEResults turSEResults) {
        return new TurSNSiteSearchWidgetBean()
                .setFacet(responseFacet(context, turSNSite,
                        requestFilterQuery(context.getTurSEParameters().getFilterQueries())
                                .getHiddenItems(), turSNSiteFacetFieldList, facetMap, turSEResults))
                .setFacetToRemove(responseFacetToRemove(context))
                .setSimilar(responseMLT(turSNSite, turSEResults))
                .setSpellCheck(responseSpellCheck(context, turSEResults.getSpellCheck()))
                .setLocales(responseLocales(turSNSite, context.getUri()))
                .setSpotlights(responseSpotlights(context, turSNSite));
    }

    private List<TurSNSiteSpotlightDocumentBean> responseSpotlights(TurSNSiteSearchContext context,
                                                                    TurSNSite turSNSite) {
        List<TurSNSiteSpotlightDocumentBean> turSNSiteSpotlightDocumentBeans = new ArrayList<>();
        turSNSpotlightProcess
                .getSpotlightsFromQuery(context, turSNSite)
                .forEach((key, value) -> value
                        .forEach(document -> {
                            TurSNSiteSpotlightDocumentBean turSNSiteSpotlightDocumentBean = new TurSNSiteSpotlightDocumentBean();
                            turSNSiteSpotlightDocumentBean.setId(document.getId());
                            turSNSiteSpotlightDocumentBean.setContent(document.getContent());
                            turSNSiteSpotlightDocumentBean.setLink(document.getLink());
                            turSNSiteSpotlightDocumentBean.setPosition(document.getPosition());
                            turSNSiteSpotlightDocumentBean.setReferenceId(document.getReferenceId());
                            turSNSiteSpotlightDocumentBean.setTitle(document.getTitle());
                            turSNSiteSpotlightDocumentBean.setType(document.getType());
                            turSNSiteSpotlightDocumentBeans.add(turSNSiteSpotlightDocumentBean);
                        }));
        turSNSiteSpotlightDocumentBeans.sort(Comparator.comparingInt(TurSNSiteSpotlightDocumentBean::getPosition));
        return turSNSiteSpotlightDocumentBeans;
    }

    public List<TurSNSiteLocaleBean> responseLocales(TurSNSite turSNSite, URI uri) {
        List<TurSNSiteLocaleBean> turSNSiteLocaleBeans = new ArrayList<>();
        turSNSiteLocaleRepository.findByTurSNSite(Sort.by(Sort.Order.asc("language").ignoreCase()), turSNSite)
                .forEach(turSNSiteLocale -> {
                    TurSNSiteLocaleBean turSNSiteLocaleBean = new TurSNSiteLocaleBean();
                    turSNSiteLocaleBean.setLocale(turSNSiteLocale.getLanguage());
                    turSNSiteLocaleBean.setLink(TurCommonsUtils
                            .addOrReplaceParameter(uri, TurSNParamType.LOCALE, turSNSiteLocale.getLanguage()).toString());
                    turSNSiteLocaleBeans.add(turSNSiteLocaleBean);
                });

        return turSNSiteLocaleBeans;
    }

    private TurSNSiteSpellCheckBean responseSpellCheck(TurSNSiteSearchContext context,
                                                       TurSESpellCheckResult turSESpellCheckResult) {
        return new TurSNSiteSpellCheckBean(context, turSESpellCheckResult);

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
        return turSNSite.getMlt() == 1 && turSEResults.getSimilarResults() != null
                && !turSEResults.getSimilarResults().isEmpty();
    }

    private TurSNSiteSearchFacetBean responseFacetToRemove(TurSNSiteSearchContext context) {
        if (!CollectionUtils.isEmpty(context.getTurSEParameters().getFilterQueries())) {
            List<TurSNSiteSearchFacetItemBean> turSNSiteSearchFacetToRemoveItemBeans = new ArrayList<>();
            context.getTurSEParameters().getFilterQueries().forEach(facetToRemove -> {
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
                                                         List<TurSNSiteFieldExt> turSNSiteFacetFieldExtList, Map<String,
            TurSNSiteFieldExt> facetMap, TurSEResults turSEResults) {
        if (turSNSite.getFacet() == 1 && hasFacetFields(turSNSiteFacetFieldExtList)) {
            List<String> usedFacetItems = new ArrayList<>();
            if (context.getTurSEParameters() != null && context.getTurSEParameters().getFilterQueries() != null )
                usedFacetItems.addAll(context.getTurSEParameters().getFilterQueries());
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
                    turSNSiteSearchFacetBeans.add(getTurSNSiteSearchFacetBean(facetMap.get(facet.getFacet()),
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
                                     Map<String, TurSNSiteFieldExt> facetMap,
                                     TurSEFacetResult facet, TurSNSite turSNSite) {
        return facetMap.containsKey(facet.getFacet())
                && (!hiddenFilterQuery.contains(facet.getFacet()) || showFacetByFacetType(turSNSite))
                && !facet.getTurSEFacetResultAttr().isEmpty();
    }

    @NotNull
    private static TurSNSiteSearchFacetBean getTurSNSiteSearchFacetBean(TurSNSiteFieldExt turSNSiteFieldExt,
                                                                        List<TurSNSiteSearchFacetItemBean>
                                                                                turSNSiteSearchFacetItemBeans) {
        TurSNSiteSearchFacetBean turSNSiteSearchFacetBean = new TurSNSiteSearchFacetBean();
        turSNSiteSearchFacetBean.setLabel(new TurSNSiteSearchFacetLabelBean()
                .setLang(TurSNUtils.DEFAULT_LANGUAGE)
                .setText(turSNSiteFieldExt.getFacetName()));
        turSNSiteSearchFacetBean.setName(turSNSiteFieldExt.getName());
        turSNSiteSearchFacetBean.setDescription(turSNSiteFieldExt.getDescription());
        turSNSiteSearchFacetBean.setMultiValuedWithInt(turSNSiteFieldExt.getMultiValued());
        turSNSiteSearchFacetBean.setType(turSNSiteFieldExt.getType());
        turSNSiteSearchFacetBean.setFacets(turSNSiteSearchFacetItemBeans);
        return new TurSNSiteSearchFacetBean()
                .setLabel(new TurSNSiteSearchFacetLabelBean()
                        .setLang(TurSNUtils.DEFAULT_LANGUAGE)
                        .setText(turSNSiteFieldExt.getFacetName()))
                .setName(turSNSiteFieldExt.getName())
                .setDescription(turSNSiteFieldExt.getDescription())
                .setType(turSNSiteFieldExt.getType())
                .setFacets(turSNSiteSearchFacetItemBeans)
                .setMultiValued(turSNSiteFieldExt.getMultiValued() == 1);
    }

    private static boolean hasFacetFields(List<TurSNSiteFieldExt> turSNSiteFacetFieldExtList) {
        return turSNSiteFacetFieldExtList != null && !turSNSiteFacetFieldExtList.isEmpty();
    }

    @NotNull
    private static TurSNSiteSearchFacetBean getTurSNSiteSearchFacetBean(
            List<TurSNSiteSearchFacetItemBean> turSNSiteSearchFacetToRemoveItemBeans) {
        return new TurSNSiteSearchFacetBean()
                .setLabel(new TurSNSiteSearchFacetLabelBean()
                        .setLang(TurSNUtils.DEFAULT_LANGUAGE)
                        .setText("Facets To Remove"))
                .setFacets(turSNSiteSearchFacetToRemoveItemBeans);
    }

    private List<TurSNSiteSearchPaginationBean> responsePagination(URI uri, TurSEGenericResults turSEResults) {

        List<TurSNSiteSearchPaginationBean> turSNSiteSearchPaginationBeans = new ArrayList<>();
        int firstPagination = setFirstPagination(turSEResults);
        int lastPagination = setLastPagination(turSEResults, turSEResults.getPageCount());
        if (turSEResults.getCurrentPage() > turSEResults.getPageCount()) {
            lastPagination = turSEResults.getPageCount();
            firstPagination = setFirstPagination(turSEResults);
        }
        TurSNSiteSearchPaginationBean turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
        if (turSEResults.getCurrentPage() > 1) {
            setFirstPage(uri, turSNSiteSearchPaginationBeans, turSNSiteSearchPaginationBean);
            setPreviousPage(uri, turSEResults, turSNSiteSearchPaginationBeans);
        }
        for (int page = firstPagination; page <= lastPagination; page++) {
            if (page == turSEResults.getCurrentPage()) {
                setCurrentPage(uri, turSNSiteSearchPaginationBeans, page);
            } else {
                setOthersPages(uri, turSNSiteSearchPaginationBeans, page);
            }
        }
        if (turSEResults.getCurrentPage() != turSEResults.getPageCount() && turSEResults.getPageCount() > 1) {
            setNextPage(uri, turSEResults, turSNSiteSearchPaginationBeans);
            setLastPage(uri, turSEResults, turSNSiteSearchPaginationBeans);
        }
        return turSNSiteSearchPaginationBeans;
    }

    private URI changeGroupURIForPagination(URI uri, String fieldName) {
        return TurCommonsUtils.addOrReplaceParameter(TurSNUtils.removeQueryField(uri, "group"),
                TurSNParamType.FILTER_QUERIES, fieldName);
    }

    private void setPreviousPage(URI uri, TurSEGenericResults turSEResults,
                                 List<TurSNSiteSearchPaginationBean> turSNSiteSearchPaginationBeans) {
        if (turSEResults.getCurrentPage() <= turSEResults.getPageCount()) {
            turSNSiteSearchPaginationBeans.add(new TurSNSiteSearchPaginationBean()
                    .setType(TurSNPaginationType.PREVIOUS)
                    .setHref(TurCommonsUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE,
                            Integer.toString(turSEResults.getCurrentPage() - 1)).toString())
                    .setText("Previous")
                    .setPage(turSEResults.getCurrentPage() - 1)
            );
        }
    }

    private void setFirstPage(URI uri, List<TurSNSiteSearchPaginationBean> turSNSiteSearchPaginationBeans,
                              TurSNSiteSearchPaginationBean turSNSiteSearchPaginationBean) {
        turSNSiteSearchPaginationBean.setType(TurSNPaginationType.FIRST);
        turSNSiteSearchPaginationBean.setHref(
                TurCommonsUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE, Integer.toString(1)).toString());
        turSNSiteSearchPaginationBean.setText("First");
        turSNSiteSearchPaginationBean.setPage(1);
        turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);
    }

    private void setLastPage(URI uri, TurSEGenericResults turSEResults,
                             List<TurSNSiteSearchPaginationBean> turSNSiteSearchPaginationBeans) {
        TurSNSiteSearchPaginationBean turSNSiteSearchPaginationBean;
        turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
        turSNSiteSearchPaginationBean.setType(TurSNPaginationType.LAST);
        turSNSiteSearchPaginationBean.setHref(TurCommonsUtils
                .addOrReplaceParameter(uri, TurSNParamType.PAGE, Integer.toString(turSEResults.getPageCount()))
                .toString());
        turSNSiteSearchPaginationBean.setText("Last");
        turSNSiteSearchPaginationBean.setPage(turSEResults.getPageCount());
        turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);
    }

    private void setNextPage(URI uri, TurSEGenericResults turSEResults,
                             List<TurSNSiteSearchPaginationBean> turSNSiteSearchPaginationBeans) {
        TurSNSiteSearchPaginationBean turSNSiteSearchPaginationBean;
        if (turSEResults.getCurrentPage() <= turSEResults.getPageCount()) {
            turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
            turSNSiteSearchPaginationBean.setType(TurSNPaginationType.NEXT);
            turSNSiteSearchPaginationBean.setHref(TurCommonsUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE,
                    Integer.toString(turSEResults.getCurrentPage() + 1)).toString());
            turSNSiteSearchPaginationBean.setText("Next");
            turSNSiteSearchPaginationBean.setPage(turSEResults.getCurrentPage() + 1);
            turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);

        }
    }

    private void setGenericPages(URI uri, List<TurSNSiteSearchPaginationBean> turSNSiteSearchPaginationBeans, int page,
                                 TurSNPaginationType type) {
        TurSNSiteSearchPaginationBean turSNSiteSearchPaginationBean;
        turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
        turSNSiteSearchPaginationBean.setHref(
                TurCommonsUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE, Integer.toString(page)).toString());
        turSNSiteSearchPaginationBean.setText(Integer.toString(page));
        turSNSiteSearchPaginationBean.setType(type);
        turSNSiteSearchPaginationBean.setPage(page);
        turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);
    }

    private void setOthersPages(URI uri, List<TurSNSiteSearchPaginationBean> turSNSiteSearchPaginationBeans, int page) {
        setGenericPages(uri, turSNSiteSearchPaginationBeans, page, TurSNPaginationType.PAGE);
    }

    private void setCurrentPage(URI uri, List<TurSNSiteSearchPaginationBean> turSNSiteSearchPaginationBeans, int page) {
        setGenericPages(uri, turSNSiteSearchPaginationBeans, page, TurSNPaginationType.CURRENT);
    }

    private int setLastPagination(TurSEGenericResults turSEResults, int lastPagination) {
        if (turSEResults.getCurrentPage() + 3 <= turSEResults.getPageCount()) {
            lastPagination = turSEResults.getCurrentPage() + 3;
        } else if (turSEResults.getCurrentPage() + 3 > turSEResults.getPageCount()) {
            lastPagination = turSEResults.getPageCount();
        }
        return lastPagination;
    }

    private int setFirstPagination(TurSEGenericResults turSEResults) {
        return turSEResults.getCurrentPage() - 3 > 0 ? turSEResults.getCurrentPage() - 3 : 1;
    }
}
