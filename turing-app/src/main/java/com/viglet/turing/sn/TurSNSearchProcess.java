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

import com.viglet.turing.api.sn.bean.TurSNSiteFilterQueryBean;
import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult;
import com.viglet.turing.commons.se.similar.TurSESimilarResult;
import com.viglet.turing.commons.sn.bean.*;
import com.viglet.turing.commons.sn.bean.spellcheck.TurSNSiteSpellCheckBean;
import com.viglet.turing.commons.sn.pagination.TurSNPaginationType;
import com.viglet.turing.commons.sn.search.TurSNParamType;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.metric.TurSNSiteMetricAccess;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm;
import com.viglet.turing.se.result.TurSEGenericResults;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.sn.spotlight.TurSNSpotlightProcess;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.*;

/**
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.6
 *
 */
@Component
public class TurSNSearchProcess {
	@Autowired
	private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
	@Autowired
	private TurSNSiteRepository turSNSiteRepository;
	@Autowired
	private TurSNSiteLocaleRepository turSNSiteLocaleRepository;
	@Autowired
	private TurSolrInstanceProcess turSolrInstanceProcess;
	@Autowired
	private TurSolr turSolr;
	@Autowired
	private TurSNSpotlightProcess turSNSpotlightProcess;
	@Autowired
	private TurSNSiteMetricAccessRepository turSNSiteMetricAccessRepository;

	public List<String> latestSearches(String siteName, String locale, String userId, int rows) {
		TurSNSite turSNSite = turSNSiteRepository.findByName(siteName);
		if (turSNSite != null) {
			return turSNSiteMetricAccessRepository
					.findLatestSearches(turSNSite, locale, userId, PageRequest.of(0, rows)).stream()
					.map(TurSNSiteMetricAccessTerm::getTerm).toList();

		}
		return Collections.emptyList();
	}

	public TurSNSiteSearchBean search(TurSNSiteSearchContext turSNSiteSearchContext) {

		TurSNSiteSearchBean turSNSiteSearchBean = new TurSNSiteSearchBean();
		turSolrInstanceProcess
				.initSolrInstance(turSNSiteSearchContext.getSiteName(), turSNSiteSearchContext.getLocale())
				.ifPresent(turSolrInstance -> {
					TurSNSite turSNSite = turSNSiteRepository.findByName(turSNSiteSearchContext.getSiteName());

					TurSEParameters turSEParameters = turSNSiteSearchContext.getTurSEParameters();
					turSEParameters.setCurrentPage(prepareQueryCurrentPage(turSEParameters));
					turSEParameters.setRows(prepareQueryRows(turSEParameters));
					TurSESpellCheckResult turSESpellCheckResult = prepareQueryAutoCorrection(turSNSiteSearchContext,
							turSNSite, turSolrInstance);
					turSolr.retrieveSolrFromSN(turSolrInstance, turSNSite, turSNSiteSearchContext,
							turSESpellCheckResult).ifPresent(turSEResults -> {
								populateMetrics(turSNSite, turSNSiteSearchContext, turSEResults.getNumFound());
								List<TurSNSiteFieldExt> turSNSiteFacetFieldExts = turSNSiteFieldExtRepository
										.findByTurSNSiteAndFacetAndEnabled(turSNSite, 1, 1);
								Map<String, TurSNSiteFieldExt> facetMap = setFacetMap(turSNSiteFacetFieldExts);
								turSNSiteSearchBean.setResults(responseDocuments(turSNSiteSearchContext,
										turSolrInstance, turSNSite, facetMap, turSEResults.getResults()));
								turSNSiteSearchBean.setGroups(responseGroups(turSNSiteSearchContext, turSolrInstance,
										turSNSite, facetMap, turSEResults));
								turSNSiteSearchBean.setPagination(
										responsePagination(turSNSiteSearchContext.getUri(), turSEResults));
								turSNSiteSearchBean.setWidget(responseWidget(turSNSiteSearchContext, turSNSite,
										turSNSiteFacetFieldExts, facetMap, turSEResults));
								turSNSiteSearchBean.setQueryContext(responseQueryContext(turSNSite, turSEResults,
										turSNSiteSearchContext.getLocale()));
							});
				});
		return turSNSiteSearchBean;
	}

	private List<TurSNSiteSearchGroupBean> responseGroups(TurSNSiteSearchContext context,
			TurSolrInstance turSolrInstance, TurSNSite turSNSite, Map<String, TurSNSiteFieldExt> facetMap,
			TurSEResults turSEResults) {
		List<TurSNSiteSearchGroupBean> turSNSiteSearchGroupBeans = new ArrayList<>();
		if (turSEResults.getGroups() != null) {
			turSEResults.getGroups().forEach(group -> {
				TurSNSiteSearchGroupBean turSNSiteSearchGroupBean = new TurSNSiteSearchGroupBean();
				turSNSiteSearchGroupBean.setName(group.getName());
				turSNSiteSearchGroupBean.setCount((int) group.getNumFound());

				turSNSiteSearchGroupBean.setPageCount(group.getPageCount());
				turSNSiteSearchGroupBean.setPage(group.getCurrentPage());
				turSNSiteSearchGroupBean.setCount((int) group.getNumFound());

				int lastItemOfFullPage = (int) group.getStart() + group.getLimit();

				if (lastItemOfFullPage < turSNSiteSearchGroupBean.getCount()) {
					turSNSiteSearchGroupBean.setPageEnd(lastItemOfFullPage);
				} else {
					turSNSiteSearchGroupBean.setPageEnd(turSNSiteSearchGroupBean.getCount());
				}
				int firstItemOfFullPage = (int) group.getStart() + 1;

				if (firstItemOfFullPage < turSNSiteSearchGroupBean.getPageEnd()) {
					turSNSiteSearchGroupBean.setPageStart(firstItemOfFullPage);
				} else {
					turSNSiteSearchGroupBean.setPageStart(turSNSiteSearchGroupBean.getPageEnd());
				}

				turSNSiteSearchGroupBean.setLimit(group.getLimit());

				turSNSiteSearchGroupBean.setPagination(
						responsePagination(changeGroupURIForPagination(context.getUri(), group.getName()), group));

				turSNSiteSearchGroupBean.setResults(
						responseDocuments(context, turSolrInstance, turSNSite, facetMap, group.getResults()));
				turSNSiteSearchGroupBeans.add(turSNSiteSearchGroupBean);
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
								: null);
				turSNSiteMetricAccess.setUserId(turSNSiteSearchContext.getTurSNSitePostParamsBean().getUserId());
			}

			turSNSiteMetricAccessRepository.save(turSNSiteMetricAccess);
		}
	}

	private boolean useMetrics(TurSNSiteSearchContext turSNSiteSearchContext) {
		return (turSNSiteSearchContext.getTurSNSitePostParamsBean() != null
				&& turSNSiteSearchContext.getTurSNSitePostParamsBean().isPopulateMetrics())
				|| turSNSiteSearchContext.getTurSNSitePostParamsBean() == null;
	}

	private TurSESpellCheckResult prepareQueryAutoCorrection(TurSNSiteSearchContext turSNSiteSearchContext,
			TurSNSite turSNSite, TurSolrInstance turSolrInstance) {
		TurSESpellCheckResult turSESpellCheckResult = turSolr.spellCheckTerm(turSolrInstance,
				turSNSiteSearchContext.getTurSEParameters().getQuery());
		if (TurSNUtils.isAutoCorrectionEnabled(turSNSiteSearchContext, turSNSite)) {
			turSESpellCheckResult.setUsingCorrected(true);
			if (TurSNUtils.hasCorrectedText(turSESpellCheckResult)) {
				turSNSiteSearchContext.setUri(TurCommonsUtils.addOrReplaceParameter(turSNSiteSearchContext.getUri(),
						"q", turSESpellCheckResult.getCorrectedText()));
			}
		} else {
			turSESpellCheckResult.setUsingCorrected(false);
		}

		return turSESpellCheckResult;
	}

	private Map<String, TurSNSiteFieldExt> setFacetMap(List<TurSNSiteFieldExt> turSNSiteFacetFieldExts) {
		Map<String, TurSNSiteFieldExt> facetMap = new HashMap<>();
		for (TurSNSiteFieldExt turSNSiteFacetFieldExt : turSNSiteFacetFieldExts) {

			TurSNFieldType snType = turSNSiteFacetFieldExt.getSnType();
			if (snType == TurSNFieldType.NER || snType == TurSNFieldType.THESAURUS) {
				facetMap.put(String.format("%s_%s", TurSNUtils.TURING_ENTITY, turSNSiteFacetFieldExt.getName()),
						turSNSiteFacetFieldExt);
			} else {
				facetMap.put(turSNSiteFacetFieldExt.getName(), turSNSiteFacetFieldExt);
			}
			facetMap.put(turSNSiteFacetFieldExt.getName(), turSNSiteFacetFieldExt);
		}

		return facetMap;
	}

	private int prepareQueryRows(TurSEParameters turSEParameters) {
		return turSEParameters.getRows() == null ? 0 : turSEParameters.getRows();
	}

	private int prepareQueryCurrentPage(TurSEParameters turSEParameters) {
		return turSEParameters.getCurrentPage() == null || turSEParameters.getCurrentPage() <= 0 ? 1
				: turSEParameters.getCurrentPage();
	}

	private TurSNSiteFilterQueryBean requestFilterQuery(List<String> fq) {

		TurSNSiteFilterQueryBean turSNSiteFilterQueryBean = new TurSNSiteFilterQueryBean();
		List<String> hiddenFilterQuery = new ArrayList<>();
		List<String> filterQueryModified = new ArrayList<>();
		processFilterQuery(fq, hiddenFilterQuery, filterQueryModified);

		turSNSiteFilterQueryBean.setHiddenItems(hiddenFilterQuery);
		turSNSiteFilterQueryBean.setItems(filterQueryModified);
		return turSNSiteFilterQueryBean;
	}

	private void processFilterQuery(List<String> fq, List<String> hiddenFilterQuery, List<String> filterQueryModified) {
		if (fq != null) {
			for (String filterQuery : fq) {
				String[] filterParts = filterQuery.split(":");
				if (filterParts.length == 2) {
					addHidddenFilterQuery(hiddenFilterQuery, filterParts);
					if (!filterParts[1].startsWith("\"") && !filterParts[1].startsWith("[")) {
						filterParts[1] = "\"" + filterParts[1] + "\"";
						filterQueryModified.add(filterParts[0] + ":" + filterParts[1]);
					}
				} else {
					filterQueryModified.add(filterQuery);
				}

			}
		}
	}

	private void addHidddenFilterQuery(List<String> hiddenFilterQuery, String[] filterParts) {
		if (!hiddenFilterQuery.contains(filterParts[0])) {
			hiddenFilterQuery.add(filterParts[0]);
		}
	}

	public List<String> requestTargetingRules(List<String> tr) {
		List<String> targetingRuleModified = new ArrayList<>();
		if (tr != null) {
			for (String targetingRule : tr) {
				String[] targetingRuleParts = targetingRule.split(":");
				if (targetingRuleParts.length == 2) {
					if (!targetingRuleParts[1].startsWith("\"") && !targetingRuleParts[1].startsWith("[")) {
						targetingRuleParts[1] = "\"" + targetingRuleParts[1] + "\"";
						targetingRuleModified.add(targetingRuleParts[0] + ":" + targetingRuleParts[1]);
					}
				} else {
					targetingRuleModified.add(targetingRule);
				}
			}
		}
		return targetingRuleModified;
	}

	private TurSNSiteSearchResultsBean responseDocuments(TurSNSiteSearchContext context,
			TurSolrInstance turSolrInstance, TurSNSite turSNSite, Map<String, TurSNSiteFieldExt> facetMap,
			List<TurSEResult> seResults) {

		Map<String, TurSNSiteFieldExt> fieldExtMap = new HashMap<>();
		List<TurSNSiteFieldExt> turSNSiteFieldExts = turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(turSNSite,
				1);
		turSNSiteFieldExts
				.forEach(turSNSiteFieldExt -> fieldExtMap.put(turSNSiteFieldExt.getName(), turSNSiteFieldExt));

		TurSNSiteSearchResultsBean turSNSiteSearchResultsBean = new TurSNSiteSearchResultsBean();
		List<TurSNSiteSearchDocumentBean> turSNSiteSearchDocumentsBean = new ArrayList<>();
		seResults.forEach(result -> TurSNUtils.addSNDocument(context.getUri(), fieldExtMap, facetMap,
				turSNSiteSearchDocumentsBean, result, false));

		if (turSNSite != null && turSNSite.getSpotlightWithResults() != null
				&& turSNSite.getSpotlightWithResults() == 1) {
			turSNSpotlightProcess.addSpotlightToResults(context, turSolrInstance, turSNSite, facetMap, fieldExtMap,
					turSNSiteSearchDocumentsBean);
		}
		turSNSiteSearchResultsBean.setDocument(turSNSiteSearchDocumentsBean);
		return turSNSiteSearchResultsBean;

	}

	private TurSNSiteSearchWidgetBean responseWidget(TurSNSiteSearchContext context, TurSNSite turSNSite,
			List<TurSNSiteFieldExt> turSNSiteFacetFieldExts, Map<String, TurSNSiteFieldExt> facetMap,
			TurSEResults turSEResults) {
		TurSNSiteFilterQueryBean turSNSiteFilterQueryBean = requestFilterQuery(
				context.getTurSEParameters().getFilterQueries());
		TurSNSiteSearchWidgetBean turSNSiteSearchWidgetBean = new TurSNSiteSearchWidgetBean();
		turSNSiteSearchWidgetBean.setFacet(responseFacet(context.getUri(), turSNSite,
				turSNSiteFilterQueryBean.getHiddenItems(), turSNSiteFacetFieldExts, facetMap, turSEResults));
		turSNSiteSearchWidgetBean.setFacetToRemove(responseFacetToRemove(context));
		turSNSiteSearchWidgetBean.setSimilar(responseMLT(turSNSite, turSEResults));
		turSNSiteSearchWidgetBean.setSpellCheck(responseSpellCheck(context, turSEResults.getSpellCheck()));
		turSNSiteSearchWidgetBean.setLocales(responseLocales(turSNSite, context.getUri()));
		turSNSiteSearchWidgetBean.setSpotlights(responseSpotlights(context, turSNSite));

		return turSNSiteSearchWidgetBean;

	}

	private List<TurSNSiteSpotlightDocumentBean> responseSpotlights(TurSNSiteSearchContext context,
			TurSNSite turSNSite) {
		Map<Integer, List<TurSNSiteSpotlightDocument>> turSNSiteSpotlightDocumentMap = turSNSpotlightProcess
				.getSpotlightsFromQuery(context, turSNSite);
		List<TurSNSiteSpotlightDocumentBean> turSNSiteSpotlightDocumentBeans = new ArrayList<>();

		turSNSiteSpotlightDocumentMap.entrySet()
				.forEach(spotlightEntry -> spotlightEntry.getValue().forEach(document -> {
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
		Collections.sort(turSNSiteSpotlightDocumentBeans, new Comparator<TurSNSiteSpotlightDocumentBean>() {
			@Override
			public int compare(TurSNSiteSpotlightDocumentBean s1, TurSNSiteSpotlightDocumentBean s2) {
				return s1.getPosition() - s2.getPosition();
			}
		});
		return turSNSiteSpotlightDocumentBeans;
	}

	public List<TurSNSiteLocaleBean> responseLocales(TurSNSite turSNSite, URI uri) {
		List<TurSNSiteLocaleBean> turSNSiteLocaleBeans = new ArrayList<>();
		turSNSiteLocaleRepository.findByTurSNSite(turSNSite).forEach(turSNSiteLocale -> {
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
			String locale) {
		TurSNSiteSearchQueryContextQueryBean turSNSiteSearchQueryContextQueryBean = new TurSNSiteSearchQueryContextQueryBean();

		turSNSiteSearchQueryContextQueryBean.setQueryString(turSEResults.getQueryString());
		turSNSiteSearchQueryContextQueryBean.setSort(turSEResults.getSort());
		turSNSiteSearchQueryContextQueryBean.setLocale(locale);
		TurSNSiteSearchQueryContextBean turSNSiteSearchQueryContextBean = new TurSNSiteSearchQueryContextBean();
		turSNSiteSearchQueryContextBean.setQuery(turSNSiteSearchQueryContextQueryBean);
		turSNSiteSearchQueryContextBean.setDefaultFields(defaultFields(turSNSite));

		turSNSiteSearchQueryContextBean.setPageCount(turSEResults.getPageCount());
		turSNSiteSearchQueryContextBean.setPage(turSEResults.getCurrentPage());
		turSNSiteSearchQueryContextBean.setCount((int) turSEResults.getNumFound());

		int lastItemOfFullPage = (int) turSEResults.getStart() + turSEResults.getLimit();

		if (lastItemOfFullPage < turSNSiteSearchQueryContextBean.getCount()) {
			turSNSiteSearchQueryContextBean.setPageEnd(lastItemOfFullPage);
		} else {
			turSNSiteSearchQueryContextBean.setPageEnd(turSNSiteSearchQueryContextBean.getCount());
		}
		int firstItemOfFullPage = (int) turSEResults.getStart() + 1;

		if (firstItemOfFullPage < turSNSiteSearchQueryContextBean.getPageEnd()) {
			turSNSiteSearchQueryContextBean.setPageStart(firstItemOfFullPage);
		} else {
			turSNSiteSearchQueryContextBean.setPageStart(turSNSiteSearchQueryContextBean.getPageEnd());
		}

		turSNSiteSearchQueryContextBean.setLimit(turSEResults.getLimit());
		turSNSiteSearchQueryContextBean.setOffset(0);
		turSNSiteSearchQueryContextBean.setResponseTime(turSEResults.getElapsedTime());
		turSNSiteSearchQueryContextBean.setIndex(turSNSite.getName());
		return turSNSiteSearchQueryContextBean;

	}

	private TurSNSiteSearchDefaultFieldsBean defaultFields(TurSNSite turSNSite) {
		TurSNSiteSearchDefaultFieldsBean turSNSiteSearchDefaultFieldsBean = new TurSNSiteSearchDefaultFieldsBean();
		turSNSiteSearchDefaultFieldsBean.setDate(turSNSite.getDefaultDateField());
		turSNSiteSearchDefaultFieldsBean.setDescription(turSNSite.getDefaultDescriptionField());
		turSNSiteSearchDefaultFieldsBean.setImage(turSNSite.getDefaultImageField());
		turSNSiteSearchDefaultFieldsBean.setText(turSNSite.getDefaultTextField());
		turSNSiteSearchDefaultFieldsBean.setTitle(turSNSite.getDefaultTitleField());
		turSNSiteSearchDefaultFieldsBean.setUrl(turSNSite.getDefaultURLField());
		return turSNSiteSearchDefaultFieldsBean;
	}

	private List<TurSESimilarResult> responseMLT(TurSNSite turSNSite, TurSEResults turSEResults) {
		return hasMLT(turSNSite, turSEResults) ? turSEResults.getSimilarResults() : null;
	}

	private boolean hasMLT(TurSNSite turSNSite, TurSEResults turSEResults) {
		return turSNSite.getMlt() == 1 && turSEResults.getSimilarResults() != null
				&& !turSEResults.getSimilarResults().isEmpty();
	}

	private List<TurSNSiteSearchFacetBean> responseFacet(URI uri, TurSNSite turSNSite, List<String> hiddenFilterQuery,
			List<TurSNSiteFieldExt> turSNSiteFacetFieldExts, Map<String, TurSNSiteFieldExt> facetMap,
			TurSEResults turSEResults) {
		if (turSNSite.getFacet() == 1 && turSNSiteFacetFieldExts != null && !turSNSiteFacetFieldExts.isEmpty()) {

			List<TurSNSiteSearchFacetBean> turSNSiteSearchFacetBeans = new ArrayList<>();
			turSEResults.getFacetResults().forEach(facet -> {
				if (facetMap.containsKey(facet.getFacet()) && !hiddenFilterQuery.contains(facet.getFacet())
						&& !facet.getTurSEFacetResultAttr().isEmpty()) {
					TurSNSiteFieldExt turSNSiteFieldExt = facetMap.get(facet.getFacet());

					TurSNSiteSearchFacetBean turSNSiteSearchFacetBean = new TurSNSiteSearchFacetBean();
					List<TurSNSiteSearchFacetItemBean> turSNSiteSearchFacetItemBeans = new ArrayList<>();
					facet.getTurSEFacetResultAttr().values().forEach(facetItem -> {
						TurSNSiteSearchFacetItemBean turSNSiteSearchFacetItemBean = new TurSNSiteSearchFacetItemBean();
						turSNSiteSearchFacetItemBean.setCount(facetItem.getCount());
						turSNSiteSearchFacetItemBean.setLabel(facetItem.getAttribute());
						turSNSiteSearchFacetItemBean.setLink(TurSNUtils
								.addFilterQuery(uri, facet.getFacet() + ":" + facetItem.getAttribute()).toString().replace(":","\\:"));
						turSNSiteSearchFacetItemBeans.add(turSNSiteSearchFacetItemBean);
					});

					TurSNSiteSearchFacetLabelBean turSNSiteSearchFacetLabelBean = new TurSNSiteSearchFacetLabelBean();
					turSNSiteSearchFacetLabelBean.setLang(TurSNUtils.DEFAULT_LANGUAGE);
					turSNSiteSearchFacetLabelBean.setText(turSNSiteFieldExt.getFacetName());
					turSNSiteSearchFacetBean.setLabel(turSNSiteSearchFacetLabelBean);
					turSNSiteSearchFacetBean.setName(turSNSiteFieldExt.getName());
					turSNSiteSearchFacetBean.setDescription(turSNSiteFieldExt.getDescription());
					turSNSiteSearchFacetBean.setMultiValuedWithInt(turSNSiteFieldExt.getMultiValued());
					turSNSiteSearchFacetBean.setType(turSNSiteFieldExt.getType());
					turSNSiteSearchFacetBean.setFacets(turSNSiteSearchFacetItemBeans);

					turSNSiteSearchFacetBeans.add(turSNSiteSearchFacetBean);
				}
			});
			return turSNSiteSearchFacetBeans;
		}
		return Collections.emptyList();
	}

	private TurSNSiteSearchFacetBean responseFacetToRemove(TurSNSiteSearchContext context) {

		if (context.getTurSEParameters().getFilterQueries() != null
				&& !context.getTurSEParameters().getFilterQueries().isEmpty()) {

			List<TurSNSiteSearchFacetItemBean> turSNSiteSearchFacetToRemoveItemBeans = new ArrayList<>();
			context.getTurSEParameters().getFilterQueries().forEach(facetToRemove -> {
				String[] facetToRemoveParts = facetToRemove.split(":", 2);
				if (facetToRemoveParts.length == 2) {
					String facetToRemoveValue = facetToRemoveParts[1].replace("\"", "");

					TurSNSiteSearchFacetItemBean turSNSiteSearchFacetToRemoveItemBean = new TurSNSiteSearchFacetItemBean();
					turSNSiteSearchFacetToRemoveItemBean.setLabel(facetToRemoveValue);
					turSNSiteSearchFacetToRemoveItemBean
							.setLink(TurSNUtils.removeFilterQuery(context.getUri(), facetToRemove).toString());
					turSNSiteSearchFacetToRemoveItemBeans.add(turSNSiteSearchFacetToRemoveItemBean);
				}
			});
			TurSNSiteSearchFacetLabelBean turSNSiteSearchFacetToRemoveLabelBean = new TurSNSiteSearchFacetLabelBean();

			TurSNSiteSearchFacetBean turSNSiteSearchFacetToRemoveBean = new TurSNSiteSearchFacetBean();
			turSNSiteSearchFacetToRemoveLabelBean.setLang(TurSNUtils.DEFAULT_LANGUAGE);
			turSNSiteSearchFacetToRemoveLabelBean.setText("Facets To Remove");
			turSNSiteSearchFacetToRemoveBean.setLabel(turSNSiteSearchFacetToRemoveLabelBean);
			turSNSiteSearchFacetToRemoveBean.setFacets(turSNSiteSearchFacetToRemoveItemBeans);
			return turSNSiteSearchFacetToRemoveBean;
		}
		return null;
	}

	private List<TurSNSiteSearchPaginationBean> responsePagination(URI uri, TurSEGenericResults turSEResults) {

		List<TurSNSiteSearchPaginationBean> turSNSiteSearchPaginationBeans = new ArrayList<>();

		int firstPagination = 1;
		int lastPagination = turSEResults.getPageCount();

		firstPagination = setFirstPagination(turSEResults, firstPagination);
		lastPagination = setLastPagination(turSEResults, lastPagination);

		if (turSEResults.getCurrentPage() > turSEResults.getPageCount()) {
			lastPagination = turSEResults.getPageCount();
			if (turSEResults.getPageCount() - 3 > 0) {
				firstPagination = turSEResults.getPageCount() - 3;
			} else if (turSEResults.getPageCount() - 3 <= 0) {
				firstPagination = 1;
			}
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

	private void setPreviousPage(URI uri, TurSEGenericResults turSEResults,
			List<TurSNSiteSearchPaginationBean> turSNSiteSearchPaginationBeans) {
		TurSNSiteSearchPaginationBean turSNSiteSearchPaginationBean;
		if (turSEResults.getCurrentPage() <= turSEResults.getPageCount()) {
			turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
			turSNSiteSearchPaginationBean.setType(TurSNPaginationType.PREVIOUS);
			turSNSiteSearchPaginationBean.setHref(TurCommonsUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE,
					Integer.toString(turSEResults.getCurrentPage() - 1)).toString());

			turSNSiteSearchPaginationBean.setText("Previous");
			turSNSiteSearchPaginationBean.setPage(turSEResults.getCurrentPage() - 1);
			turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);
		}
	}

	private URI changeGroupURIForPagination(URI uri, String fieldName) {
		return TurCommonsUtils.addOrReplaceParameter(TurSNUtils.removeQueryField(uri, "group"),
				TurSNParamType.FILTER_QUERIES, fieldName);
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

	private void setOthersPages(URI uri, List<TurSNSiteSearchPaginationBean> turSNSiteSearchPaginationBeans, int page) {
		TurSNSiteSearchPaginationBean turSNSiteSearchPaginationBean;
		turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
		turSNSiteSearchPaginationBean.setHref(
				TurCommonsUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE, Integer.toString(page)).toString());
		turSNSiteSearchPaginationBean.setText(Integer.toString(page));
		turSNSiteSearchPaginationBean.setType(TurSNPaginationType.PAGE);
		turSNSiteSearchPaginationBean.setPage(page);
		turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);
	}

	private void setCurrentPage(URI uri, List<TurSNSiteSearchPaginationBean> turSNSiteSearchPaginationBeans, int page) {
		TurSNSiteSearchPaginationBean turSNSiteSearchPaginationBean;
		turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
		turSNSiteSearchPaginationBean.setHref(
				TurCommonsUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE, Integer.toString(page)).toString());
		turSNSiteSearchPaginationBean.setType(TurSNPaginationType.CURRENT);
		turSNSiteSearchPaginationBean.setText(Integer.toString(page));
		turSNSiteSearchPaginationBean.setPage(page);
		turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);
	}

	private int setLastPagination(TurSEGenericResults turSEResults, int lastPagination) {
		if (turSEResults.getCurrentPage() + 3 <= turSEResults.getPageCount()) {
			lastPagination = turSEResults.getCurrentPage() + 3;
		} else if (turSEResults.getCurrentPage() + 3 > turSEResults.getPageCount()) {
			lastPagination = turSEResults.getPageCount();
		}
		return lastPagination;
	}

	private int setFirstPagination(TurSEGenericResults turSEResults, int firstPagination) {
		if (turSEResults.getCurrentPage() - 3 > 0) {
			firstPagination = turSEResults.getCurrentPage() - 3;
		} else if (turSEResults.getCurrentPage() - 3 <= 0) {
			firstPagination = 1;
		}
		return firstPagination;
	}

}
