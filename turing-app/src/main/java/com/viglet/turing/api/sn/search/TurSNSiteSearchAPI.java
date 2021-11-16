/*
 * Copyright (C) 2016-2021 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.api.sn.search;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrField;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrInstanceProcess;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.viglet.turing.api.sn.bean.TurSNSiteFilterQueryBean;
import com.viglet.turing.api.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.api.sn.bean.TurSNSiteSearchDefaultFieldsBean;
import com.viglet.turing.api.sn.bean.TurSNSiteSearchDocumentBean;
import com.viglet.turing.api.sn.bean.TurSNSiteSearchDocumentMetadataBean;
import com.viglet.turing.api.sn.bean.TurSNSiteSearchFacetBean;
import com.viglet.turing.api.sn.bean.TurSNSiteSearchFacetItemBean;
import com.viglet.turing.api.sn.bean.TurSNSiteSearchFacetLabelBean;
import com.viglet.turing.api.sn.bean.TurSNSiteSearchPaginationBean;
import com.viglet.turing.api.sn.bean.TurSNSiteSearchQueryContextBean;
import com.viglet.turing.api.sn.bean.TurSNSiteSearchQueryContextQueryBean;
import com.viglet.turing.api.sn.bean.TurSNSiteSearchResultsBean;
import com.viglet.turing.api.sn.bean.TurSNSiteSearchWidgetBean;
import com.viglet.turing.api.sn.bean.spellcheck.TurSNSiteSpellCheckBean;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightTerm;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightTermRepository;
import com.viglet.turing.se.TurSEParameters;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.se.result.spellcheck.TurSESpellCheckResult;
import com.viglet.turing.se.similar.TurSESimilarResult;
import com.viglet.turing.sn.TurSNFieldType;
import com.viglet.turing.sn.TurSNUtils;

@RestController
@RequestMapping("/api/sn/{siteName}/search")
@Tag(name = "Semantic Navigation Search", description = "Semantic Navigation Search API")
public class TurSNSiteSearchAPI {
	private static final Logger logger = LogManager.getLogger(TurSNSiteSearchAPI.class);
	private static final String TURING_ENTITY = "turing_entity";
	private static final String DEFAULT_LANGUAGE = "en";
	private static final String URL = "url";
	@Autowired
	private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
	@Autowired
	private TurSNSiteRepository turSNSiteRepository;
	@Autowired
	private TurSolrField turSolrField;
	@Autowired
	private TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;
	@Autowired
	private TurSNSiteSpotlightTermRepository turSNSiteSpotlightTermRepository;
	@Autowired
	private TurSolrInstanceProcess turSolrInstanceProcess;
	@Autowired
	private TurSolr turSolr;

	@GetMapping
	public TurSNSiteSearchBean turSNSiteSearchSelect(@PathVariable String siteName,
			@RequestParam(required = false, name = TurSNParamType.QUERY) String q,
			@RequestParam(required = false, name = TurSNParamType.PAGE) Integer currentPage,
			@RequestParam(required = false, name = TurSNParamType.FILTER_QUERIES) List<String> fq,
			@RequestParam(required = false, name = TurSNParamType.TARGETING_RULES) List<String> tr,
			@RequestParam(required = false, name = TurSNParamType.SORT) String sort,
			@RequestParam(required = false, name = TurSNParamType.ROWS) Integer rows,
			@RequestParam(required = false, name = TurSNParamType.AUTO_CORRECTION_DISABLED, defaultValue = "0") Integer autoCorrectionDisabled,
			@RequestParam(required = false, name = TurSNParamType.LOCALE) String locale, HttpServletRequest request) {

		TurSNSiteSearchContext turSNSiteSearchContext = new TurSNSiteSearchContext(siteName, new TurSEParameters(q, fq, requestTargetingRules(tr), currentPage,
				sort, rows, autoCorrectionDisabled), locale,
				TurSNUtils.requestToURI(request));
		TurSNSite turSNSite = turSNSiteRepository.findByName(siteName);

		TurSNSiteFilterQueryBean turSNSiteFilterQueryBean = requestFilterQuery(fq);

		TurSNSiteSearchBean turSNSiteSearchBean = new TurSNSiteSearchBean();
		TurSolrInstance turSolrInstance = turSolrInstanceProcess.initSolrInstance(turSNSite, locale);

		requestSolr(turSolrInstance, turSNSiteSearchContext, turSNSite).ifPresent(turSEResults -> {
			System.out.println("turSEResults: " + turSEResults.getQueryString());
			if (TurSNUtils.isAutoCorrectionEnabled(turSNSiteSearchContext, turSNSite)) {
				TurSESpellCheckResult turSESpellCheckResult = turSolr.spellCheckTerm(turSolrInstance, q);
				if (TurSNUtils.hasCorrectedText(turSESpellCheckResult)) {
					turSNSiteSearchContext.setUri(TurSNUtils.addOrReplaceParameter(turSNSiteSearchContext.getUri(), "q",
							turSESpellCheckResult.getCorrectedText()));
				}
			}
			List<TurSNSiteFieldExt> turSNSiteFacetFieldExts = turSNSiteFieldExtRepository
					.findByTurSNSiteAndFacetAndEnabled(turSNSite, 1, 1);

			Map<String, TurSNSiteFieldExt> facetMap = setFacetMap(turSNSiteFacetFieldExts);

			turSNSiteSearchBean.setResults(
					responseDocuments(turSNSiteSearchContext, turSolrInstance, turSNSite, facetMap, turSEResults));
			turSNSiteSearchBean.setPagination(responsePagination(turSNSiteSearchContext.getUri(), turSEResults));
			turSNSiteSearchBean.setWidget(responseWidget(turSNSiteSearchContext, turSNSite, turSNSiteFilterQueryBean,
					turSNSiteFacetFieldExts, facetMap, turSEResults));
			turSNSiteSearchBean.setQueryContext(responseQueryContext(turSNSite, turSEResults));
		});

		return turSNSiteSearchBean;
	}

	private Map<String, TurSNSiteFieldExt> setFacetMap(List<TurSNSiteFieldExt> turSNSiteFacetFieldExts) {
		Map<String, TurSNSiteFieldExt> facetMap = new HashMap<>();
		for (TurSNSiteFieldExt turSNSiteFacetFieldExt : turSNSiteFacetFieldExts) {

			TurSNFieldType snType = turSNSiteFacetFieldExt.getSnType();
			if (snType == TurSNFieldType.NER || snType == TurSNFieldType.THESAURUS) {
				facetMap.put(String.format("%s_%s", TURING_ENTITY, turSNSiteFacetFieldExt.getName()),
						turSNSiteFacetFieldExt);
			} else {
				facetMap.put(turSNSiteFacetFieldExt.getName(), turSNSiteFacetFieldExt);
			}
			facetMap.put(turSNSiteFacetFieldExt.getName(), turSNSiteFacetFieldExt);
		}

		return facetMap;
	}

	private Optional<TurSEResults> requestSolr(TurSolrInstance turSolrInstance, TurSNSiteSearchContext context,
			TurSNSite turSNSite) {
		TurSEParameters turSEParameters = context.getTurSEParameters();
		turSEParameters.setCurrentPage(
				turSEParameters.getCurrentPage() == null || turSEParameters.getCurrentPage() <= 0 ? 1 : turSEParameters.getCurrentPage());
		turSEParameters.setRows(turSEParameters.getRows() == null ? 0 : turSEParameters.getRows());
		try {
			TurSEResults turSEResults = turSolr.retrieveSolrFromSN(turSolrInstance, turSNSite, context);
			return Optional.of(turSEResults);
		} catch (Exception e) {
			logger.error(e);
		}

		return Optional.empty();
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

	private List<String> requestTargetingRules(List<String> tr) {
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
			TurSEResults turSEResults) {

		Map<String, TurSNSiteFieldExt> fieldExtMap = new HashMap<>();
		List<TurSNSiteFieldExt> turSNSiteFieldExts = turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(turSNSite,
				1);
		turSNSiteFieldExts
				.forEach(turSNSiteFieldExt -> fieldExtMap.put(turSNSiteFieldExt.getName(), turSNSiteFieldExt));

		TurSNSiteSearchResultsBean turSNSiteSearchResultsBean = new TurSNSiteSearchResultsBean();
		List<TurSNSiteSearchDocumentBean> turSNSiteSearchDocumentsBean = new ArrayList<>();
		List<TurSNSiteSpotlightTerm> turSNSiteSpotlightTerms = turSNSiteSpotlightTermRepository
				.findByNameIn(Arrays.asList(context.getTurSEParameters().getQuery().split(" ")));

		List<TurSNSiteSpotlight> turSNSiteSpotlights = turSNSiteSpotlightRepository
				.findDistinctByTurSNSiteAndTurSNSiteSpotlightTermsIn(turSNSite, turSNSiteSpotlightTerms);

		List<TurSEResult> seResults = turSEResults.getResults();

		Map<Integer, List<TurSNSiteSpotlightDocument>> turSNSiteSpotlightDocumentMap = new HashMap<>();
		turSNSiteSpotlights.forEach(spotlight -> spotlight.getTurSNSiteSpotlightDocuments().forEach(document -> {
			if (turSNSiteSpotlightDocumentMap.containsKey(document.getPosition())) {
				turSNSiteSpotlightDocumentMap.get(document.getPosition()).add(document);
			} else {
				turSNSiteSpotlightDocumentMap.put(document.getPosition(), Arrays.asList(document));
			}
		}));

		int position = 1;
		for (TurSEResult result : seResults) {
			if (turSNSiteSpotlightDocumentMap.containsKey(position)) {
				List<TurSNSiteSpotlightDocument> turSNSiteSpotlightDocuments = turSNSiteSpotlightDocumentMap
						.get(position);
				turSNSiteSpotlightDocuments.forEach(document -> {
					TurSEResult turSEResult = turSolr.findById(turSolrInstance, turSNSite, document.getSearchId());
					if (turSEResult != null) {
						addSNDocument(context.getUri(), fieldExtMap, facetMap, turSNSiteSearchDocumentsBean,
								turSEResult, true);
					}
				});
			}

			addSNDocument(context.getUri(), fieldExtMap, facetMap, turSNSiteSearchDocumentsBean, result, false);

			position++;
		}
		turSNSiteSearchResultsBean.setDocument(turSNSiteSearchDocumentsBean);
		return turSNSiteSearchResultsBean;

	}

	private TurSNSiteSearchWidgetBean responseWidget(TurSNSiteSearchContext context, TurSNSite turSNSite,
			TurSNSiteFilterQueryBean turSNSiteFilterQueryBean, List<TurSNSiteFieldExt> turSNSiteFacetFieldExts,
			Map<String, TurSNSiteFieldExt> facetMap, TurSEResults turSEResults) {
		TurSNSiteSearchWidgetBean turSNSiteSearchWidgetBean = new TurSNSiteSearchWidgetBean();
		turSNSiteSearchWidgetBean.setFacet(responseFacet(context.getUri(), turSNSite,
				turSNSiteFilterQueryBean.getHiddenItems(), turSNSiteFacetFieldExts, facetMap, turSEResults));
		turSNSiteSearchWidgetBean.setFacetToRemove(responseFacetToRemove(context));
		turSNSiteSearchWidgetBean.setSimilar(responseMLT(turSNSite, turSEResults));
		turSNSiteSearchWidgetBean.setSpellCheck(responseSpellCheck(context, turSEResults.getSpellCheck()));
		return turSNSiteSearchWidgetBean;

	}

	private TurSNSiteSpellCheckBean responseSpellCheck(TurSNSiteSearchContext context,
			TurSESpellCheckResult turSESpellCheckResult) {
		return new TurSNSiteSpellCheckBean(context, turSESpellCheckResult);

	}

	private TurSNSiteSearchQueryContextBean responseQueryContext(TurSNSite turSNSite, TurSEResults turSEResults) {
		TurSNSiteSearchQueryContextQueryBean turSNSiteSearchQueryContextQueryBean = new TurSNSiteSearchQueryContextQueryBean();

		turSNSiteSearchQueryContextQueryBean.setQueryString(turSEResults.getQueryString());
		turSNSiteSearchQueryContextQueryBean.setSort(turSEResults.getSort());

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
								.addFilterQuery(uri, facet.getFacet() + ":" + facetItem.getAttribute()).toString());
						turSNSiteSearchFacetItemBeans.add(turSNSiteSearchFacetItemBean);
					});

					TurSNSiteSearchFacetLabelBean turSNSiteSearchFacetLabelBean = new TurSNSiteSearchFacetLabelBean();
					turSNSiteSearchFacetLabelBean.setLang(DEFAULT_LANGUAGE);
					turSNSiteSearchFacetLabelBean.setText(turSNSiteFieldExt.getFacetName());
					turSNSiteSearchFacetBean.setLabel(turSNSiteSearchFacetLabelBean);
					turSNSiteSearchFacetBean.setName(turSNSiteFieldExt.getName());
					turSNSiteSearchFacetBean.setDescription(turSNSiteFieldExt.getDescription());
					turSNSiteSearchFacetBean.setMultiValued(turSNSiteFieldExt.getMultiValued());
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

		if (context.getTurSEParameters().getFilterQueries() != null && !context.getTurSEParameters().getFilterQueries().isEmpty()) {

			List<TurSNSiteSearchFacetItemBean> turSNSiteSearchFacetToRemoveItemBeans = new ArrayList<>();
			context.getTurSEParameters().getFilterQueries().forEach(facetToRemove -> {
				String[] facetToRemoveParts = facetToRemove.split(":");
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
			turSNSiteSearchFacetToRemoveLabelBean.setLang(DEFAULT_LANGUAGE);
			turSNSiteSearchFacetToRemoveLabelBean.setText("Facets To Remove");
			turSNSiteSearchFacetToRemoveBean.setLabel(turSNSiteSearchFacetToRemoveLabelBean);
			turSNSiteSearchFacetToRemoveBean.setFacets(turSNSiteSearchFacetToRemoveItemBeans);
			return turSNSiteSearchFacetToRemoveBean;
		}
		return null;
	}

	private List<TurSNSiteSearchPaginationBean> responsePagination(URI uri, TurSEResults turSEResults) {

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

	private void setPreviousPage(URI uri, TurSEResults turSEResults,
			List<TurSNSiteSearchPaginationBean> turSNSiteSearchPaginationBeans) {
		TurSNSiteSearchPaginationBean turSNSiteSearchPaginationBean;
		if (turSEResults.getCurrentPage() <= turSEResults.getPageCount()) {
			turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
			turSNSiteSearchPaginationBean.setType(TurSNPaginationType.PREVIOUS);
			turSNSiteSearchPaginationBean.setHref(TurSNUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE,
					Integer.toString(turSEResults.getCurrentPage() - 1)).toString());
			turSNSiteSearchPaginationBean.setText("Previous");
			turSNSiteSearchPaginationBean.setPage(turSEResults.getCurrentPage() - 1);
			turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);
		}
	}

	private void setFirstPage(URI uri, List<TurSNSiteSearchPaginationBean> turSNSiteSearchPaginationBeans,
			TurSNSiteSearchPaginationBean turSNSiteSearchPaginationBean) {
		turSNSiteSearchPaginationBean.setType(TurSNPaginationType.FIRST);
		turSNSiteSearchPaginationBean
				.setHref(TurSNUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE, Integer.toString(1)).toString());
		turSNSiteSearchPaginationBean.setText("First");
		turSNSiteSearchPaginationBean.setPage(1);
		turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);
	}

	private void setLastPage(URI uri, TurSEResults turSEResults,
			List<TurSNSiteSearchPaginationBean> turSNSiteSearchPaginationBeans) {
		TurSNSiteSearchPaginationBean turSNSiteSearchPaginationBean;
		turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
		turSNSiteSearchPaginationBean.setType(TurSNPaginationType.LAST);
		turSNSiteSearchPaginationBean.setHref(TurSNUtils
				.addOrReplaceParameter(uri, TurSNParamType.PAGE, Integer.toString(turSEResults.getPageCount()))
				.toString());
		turSNSiteSearchPaginationBean.setText("Last");
		turSNSiteSearchPaginationBean.setPage(turSEResults.getPageCount());
		turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);
	}

	private void setNextPage(URI uri, TurSEResults turSEResults,
			List<TurSNSiteSearchPaginationBean> turSNSiteSearchPaginationBeans) {
		TurSNSiteSearchPaginationBean turSNSiteSearchPaginationBean;
		if (turSEResults.getCurrentPage() <= turSEResults.getPageCount()) {
			turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();

			turSNSiteSearchPaginationBean.setType(TurSNPaginationType.NEXT);
			turSNSiteSearchPaginationBean.setHref(TurSNUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE,
					Integer.toString(turSEResults.getCurrentPage() + 1)).toString());
			turSNSiteSearchPaginationBean.setText("Next");
			turSNSiteSearchPaginationBean.setPage(turSEResults.getCurrentPage() + 1);
			turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);

		}
	}

	private void setOthersPages(URI uri, List<TurSNSiteSearchPaginationBean> turSNSiteSearchPaginationBeans, int page) {
		TurSNSiteSearchPaginationBean turSNSiteSearchPaginationBean;
		turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
		turSNSiteSearchPaginationBean
				.setHref(TurSNUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE, Integer.toString(page)).toString());
		turSNSiteSearchPaginationBean.setText(Integer.toString(page));
		turSNSiteSearchPaginationBean.setType(TurSNPaginationType.PAGE);
		turSNSiteSearchPaginationBean.setPage(page);
		turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);
	}

	private void setCurrentPage(URI uri, List<TurSNSiteSearchPaginationBean> turSNSiteSearchPaginationBeans, int page) {
		TurSNSiteSearchPaginationBean turSNSiteSearchPaginationBean;
		turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
		turSNSiteSearchPaginationBean
				.setHref(TurSNUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE, Integer.toString(page)).toString());
		turSNSiteSearchPaginationBean.setType(TurSNPaginationType.CURRENT);
		turSNSiteSearchPaginationBean.setText(Integer.toString(page));
		turSNSiteSearchPaginationBean.setPage(page);
		turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);
	}

	private int setLastPagination(TurSEResults turSEResults, int lastPagination) {
		if (turSEResults.getCurrentPage() + 3 <= turSEResults.getPageCount()) {
			lastPagination = turSEResults.getCurrentPage() + 3;
		} else if (turSEResults.getCurrentPage() + 3 > turSEResults.getPageCount()) {
			lastPagination = turSEResults.getPageCount();
		}
		return lastPagination;
	}

	private int setFirstPagination(TurSEResults turSEResults, int firstPagination) {
		if (turSEResults.getCurrentPage() - 3 > 0) {
			firstPagination = turSEResults.getCurrentPage() - 3;
		} else if (turSEResults.getCurrentPage() - 3 <= 0) {
			firstPagination = 1;
		}
		return firstPagination;
	}

	private void addSNDocument(URI uri, Map<String, TurSNSiteFieldExt> fieldExtMap,
			Map<String, TurSNSiteFieldExt> facetMap, List<TurSNSiteSearchDocumentBean> turSNSiteSearchDocumentsBean,
			TurSEResult result, boolean isElevate) {
		TurSNSiteSearchDocumentBean turSNSiteSearchDocumentBean = new TurSNSiteSearchDocumentBean();
		Map<String, Object> turSEResultAttr = result.getFields();
		Set<String> attribs = turSEResultAttr.keySet();

		turSNSiteSearchDocumentBean.setElevate(isElevate);

		List<TurSNSiteSearchDocumentMetadataBean> turSNSiteSearchDocumentMetadataBeans = addMetadataFromDocument(uri,
				facetMap, turSEResultAttr);

		turSNSiteSearchDocumentBean.setMetadata(turSNSiteSearchDocumentMetadataBeans);

		setSourcefromDocument(turSNSiteSearchDocumentBean, turSEResultAttr);

		Map<String, Object> fields = addFieldsFromDocument(fieldExtMap, turSEResultAttr, attribs);
		turSNSiteSearchDocumentBean.setFields(fields);
		turSNSiteSearchDocumentsBean.add(turSNSiteSearchDocumentBean);
	}

	private List<TurSNSiteSearchDocumentMetadataBean> addMetadataFromDocument(URI uri,
			Map<String, TurSNSiteFieldExt> facetMap, Map<String, Object> turSEResultAttr) {
		List<TurSNSiteSearchDocumentMetadataBean> turSNSiteSearchDocumentMetadataBeans = new ArrayList<>();
		facetMap.keySet().forEach(facet -> {
			if (turSEResultAttr.containsKey(facet)) {
				if (turSEResultAttr.get(facet) instanceof ArrayList) {
					((ArrayList<?>) turSEResultAttr.get(facet)).forEach(facetValueObject -> addFilterQueryByType(uri,
							turSNSiteSearchDocumentMetadataBeans, facet, facetValueObject));
				} else {
					addFilterQueryByType(uri, turSNSiteSearchDocumentMetadataBeans, facet, turSEResultAttr.get(facet));
				}

			}
		});

		return turSNSiteSearchDocumentMetadataBeans;
	}

	private Map<String, Object> addFieldsFromDocument(Map<String, TurSNSiteFieldExt> fieldExtMap,
			Map<String, Object> turSEResultAttr, Set<String> attribs) {
		Map<String, Object> fields = new HashMap<>();
		attribs.forEach(attribute -> {
			if (!attribute.startsWith(TURING_ENTITY)) {
				addFieldandValueToMap(turSEResultAttr, fields, attribute, getFieldName(fieldExtMap, attribute));
			}
		});
		return fields;
	}

	private String getFieldName(Map<String, TurSNSiteFieldExt> fieldExtMap, String attribute) {
		String nodeName;
		if (fieldExtMap.containsKey(attribute)) {
			TurSNSiteFieldExt turSNSiteFieldExt = fieldExtMap.get(attribute);
			nodeName = turSNSiteFieldExt.getName();
		} else {
			nodeName = attribute;
		}
		return nodeName;
	}

	private void addFieldandValueToMap(Map<String, Object> turSEResultAttr, Map<String, Object> fields,
			String attribute, String nodeName) {
		if (nodeName != null && fields.containsKey(nodeName)) {
			addValueToExistingFieldMap(turSEResultAttr, fields, attribute, nodeName);
		} else {
			fields.put(nodeName, turSEResultAttr.get(attribute));

		}
	}

	@SuppressWarnings("unchecked")
	private void addValueToExistingFieldMap(Map<String, Object> turSEResultAttr, Map<String, Object> fields,
			String attribute, String nodeName) {
		if (!(fields.get(nodeName) instanceof List)) {
			List<Object> attributeValues = new ArrayList<>();
			attributeValues.add(fields.get(nodeName));
			attributeValues.add(turSEResultAttr.get(attribute));
			fields.put(nodeName, attributeValues);
		} else {
			((List<Object>) fields.get(nodeName)).add(turSEResultAttr.get(attribute));
		}
	}

	private void setSourcefromDocument(TurSNSiteSearchDocumentBean turSNSiteSearchDocumentBean,
			Map<String, Object> turSEResultAttr) {
		if (turSEResultAttr.containsKey(URL)) {
			turSNSiteSearchDocumentBean.setSource((String) turSEResultAttr.get(URL));
		}
	}

	private void addFilterQueryByType(URI uri,
			List<TurSNSiteSearchDocumentMetadataBean> turSNSiteSearchDocumentMetadataBeans, String facet,
			Object attrValue) {
		String facetValue = turSolrField.convertFieldToString(attrValue);
		TurSNSiteSearchDocumentMetadataBean turSNSiteSearchDocumentMetadataBean = new TurSNSiteSearchDocumentMetadataBean();
		turSNSiteSearchDocumentMetadataBean
				.setHref(TurSNUtils.addFilterQuery(uri, facet + ":" + facetValue).toString());
		turSNSiteSearchDocumentMetadataBean.setText(facetValue);
		turSNSiteSearchDocumentMetadataBeans.add(turSNSiteSearchDocumentMetadataBean);
	}
}
