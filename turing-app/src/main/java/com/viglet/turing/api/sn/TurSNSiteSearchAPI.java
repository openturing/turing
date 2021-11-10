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

package com.viglet.turing.api.sn;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightTerm;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightTermRepository;
import com.viglet.turing.se.facet.TurSEFacetResultAttr;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.se.similar.TurSESimilarResult;
import com.viglet.turing.sn.TurSNFieldType;

@RestController
@RequestMapping("/api/sn/{siteName}/search")
@Tag(name = "Semantic Navigation Search", description = "Semantic Navigation Search API")
public class TurSNSiteSearchAPI {
	static final Logger logger = LogManager.getLogger(TurSNSiteSearchAPI.class.getName());
	@Autowired
	TurSolr turSolr;
	@Autowired
	TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
	@Autowired
	TurSNSiteRepository turSNSiteRepository;
	@Autowired
	TurSolrField turSolrField;
	@Autowired
	TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;
	@Autowired
	TurSNSiteSpotlightTermRepository turSNSiteSpotlightTermRepository;

	public String addOrReplaceParameter(HttpServletRequest request, String paramName, String paramValue) {
		StringBuffer sbQueryString = new StringBuffer();
		boolean alreadyExists = false;
		@SuppressWarnings("unchecked")
		Map<String, String[]> queryParams = request.getParameterMap();
		try {
			for (Object queryParamObject : queryParams.keySet().toArray()) {
				String queryParam = (String) queryParamObject;
				for (String queryParamValue : queryParams.get(queryParam)) {
					if ((queryParam.equals(paramName) && !alreadyExists)) {
						alreadyExists = true;
						sbQueryString.append(queryParam + "=" + URLEncoder.encode(paramValue, "UTF-8") + "&");
					} else {
						sbQueryString.append(queryParam + "=" + URLEncoder.encode(queryParamValue, "UTF-8") + "&");
					}
				}
			}
			if (!alreadyExists) {
				sbQueryString.append(paramName + "=" + URLEncoder.encode(paramValue, "UTF-8") + "&");
			}
		} catch (UnsupportedEncodingException e) {
			logger.error(e);
		}

		String queryString = sbQueryString.toString().substring(0, sbQueryString.toString().length() - 1);

		return request.getRequestURI() + "?" + queryString;

	}

	public String addFilterQuery(HttpServletRequest request, String fq) {
		StringBuffer sbQueryString = new StringBuffer();
		boolean alreadyExists = false;
		@SuppressWarnings("unchecked")
		Map<String, String[]> queryParams = request.getParameterMap();
		try {
			for (Object queryParamObject : queryParams.keySet().toArray()) {
				String queryParam = (String) queryParamObject;
				for (String queryParamValue : queryParams.get(queryParam)) {
					if ((queryParamValue.equals(fq) && queryParam.equals("fq[]"))) {
						alreadyExists = true;
					}
					// Reset Page
					if ((queryParam.equals("p"))) {
						sbQueryString.append(queryParam + "=1" + "&");
					} else {
						sbQueryString.append(queryParam + "=" + URLEncoder.encode(queryParamValue, "UTF-8") + "&");
					}
				}
			}
			if (!alreadyExists) {
				sbQueryString.append("fq[]=" + URLEncoder.encode(fq, "UTF-8") + "&");
			}
		} catch (UnsupportedEncodingException e) {
			logger.error(e);
		}

		String queryString = sbQueryString.toString().substring(0, sbQueryString.toString().length() - 1);

		return request.getRequestURI() + "?" + queryString;

	}

	public String removeFilterQuery(HttpServletRequest request, String fq) {
		StringBuffer sbQueryString = new StringBuffer();

		@SuppressWarnings("unchecked")
		Map<String, String[]> queryParams = request.getParameterMap();
		try {
			for (Object queryParamObject : queryParams.keySet().toArray()) {
				String queryParam = (String) queryParamObject;
				for (String queryParamValue : queryParams.get(queryParam)) {
					if (!(queryParamValue.equals(fq) && queryParam.equals("fq[]"))) {
						if ((queryParam.equals("p"))) { // Reset Page
							sbQueryString.append(queryParam + "=1" + "&");
						} else {
							sbQueryString.append(queryParam + "=" + URLEncoder.encode(queryParamValue, "UTF-8") + "&");
						}
					}

				}
			}
		} catch (UnsupportedEncodingException e) {
			logger.error(e);
		}

		String queryString = sbQueryString.toString().substring(0, sbQueryString.toString().length() - 1);

		return request.getRequestURI() + "?" + queryString;

	}

	@GetMapping
	public TurSNSiteSearchBean turSNSiteSearchSelect(@PathVariable String siteName,
			@RequestParam(required = false, name = "q") String q,
			@RequestParam(required = false, name = "p") Integer currentPage,
			@RequestParam(required = false, name = "fq[]") List<String> fq,
			@RequestParam(required = false, name = "tr[]") List<String> tr,
			@RequestParam(required = false, name = "sort") String sort,
			@RequestParam(required = false, name = "rows") Integer rows, HttpServletRequest request) {

		TurSNSite turSNSite = turSNSiteRepository.findByName(siteName);

		List<String> targetingRuleModified = requestTargetingRules(tr);
	
		TurSNSiteFilterQueryBean turSNSiteFilterQueryBean = requestFilterQuery(fq);

		TurSNSiteSearchBean turSNSiteSearchBean = new TurSNSiteSearchBean();

		requestSolr(q, currentPage, sort, rows, turSNSite, turSNSiteFilterQueryBean, targetingRuleModified)
				.ifPresent(turSEResults -> {
					
					List<TurSNSiteFieldExt> turSNSiteFacetFieldExts = turSNSiteFieldExtRepository
							.findByTurSNSiteAndFacetAndEnabled(turSNSite, 1, 1);
					
					Map<String, TurSNSiteFieldExt> facetMap = setFacetMap(turSNSiteFacetFieldExts);

					turSNSiteSearchBean.setResults(responseDocuments(q, request, turSNSite, facetMap, turSEResults));
					turSNSiteSearchBean.setPagination(responsePagination(request, turSEResults));
					turSNSiteSearchBean.setWidget(responseWidget(fq, request, turSNSite, turSNSiteFilterQueryBean,
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
				facetMap.put(String.format("turing_entity_%s", turSNSiteFacetFieldExt.getName()),
						turSNSiteFacetFieldExt);
			} else {
				facetMap.put(turSNSiteFacetFieldExt.getName(), turSNSiteFacetFieldExt);
			}
			facetMap.put(turSNSiteFacetFieldExt.getName(), turSNSiteFacetFieldExt);
		}

		return facetMap;
	}

	private Optional<TurSEResults> requestSolr(String q, Integer currentPage, String sort, Integer rows,
			TurSNSite turSNSite, TurSNSiteFilterQueryBean turSNSiteFilterQueryBean, List<String> targetingRuleModified) {
		currentPage = currentPage == null || currentPage <= 0 ? 1 : currentPage;
		rows = rows == null ? 0 : rows;
		turSolr.init(turSNSite);
		try {

			TurSEResults turSEResults = turSolr.retrieveSolr(q, turSNSiteFilterQueryBean.getItems(), targetingRuleModified,
					currentPage.intValue(), sort, rows.intValue());
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
		if (fq != null) {
			for (String filterQuery : fq) {
				String[] filterParts = filterQuery.split(":");
				if (filterParts.length == 2) {
					if (!hiddenFilterQuery.contains(filterParts[0])) {
						hiddenFilterQuery.add(filterParts[0]);
					}
					if (!filterParts[1].startsWith("\"") && !filterParts[1].startsWith("[")) {
						filterParts[1] = "\"" + filterParts[1] + "\"";
						filterQueryModified.add(filterParts[0] + ":" + filterParts[1]);
					}
				} else {
					filterQueryModified.add(filterQuery);
				}

			}
		}

		turSNSiteFilterQueryBean.setHiddenItems(hiddenFilterQuery);
		turSNSiteFilterQueryBean.setItems(filterQueryModified);
		return turSNSiteFilterQueryBean;
	}

	private List<String> requestTargetingRules(List<String> tr) {
		// Targeting Rule
		List<String> targetingRuleModified = new ArrayList<String>();
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

	private TurSNSiteSearchResultsBean responseDocuments(String q, HttpServletRequest request, TurSNSite turSNSite,
			Map<String, TurSNSiteFieldExt> facetMap, TurSEResults turSEResults) {
		Map<String, TurSNSiteFieldExt> fieldExtMap = new HashMap<>();
		List<TurSNSiteFieldExt> turSNSiteFieldExts = turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(turSNSite,
				1);
		turSNSiteFieldExts
				.forEach(turSNSiteFieldExt -> fieldExtMap.put(turSNSiteFieldExt.getName(), turSNSiteFieldExt));

		TurSNSiteSearchResultsBean turSNSiteSearchResultsBean = new TurSNSiteSearchResultsBean();
		List<TurSNSiteSearchDocumentBean> turSNSiteSearchDocumentsBean = new ArrayList<TurSNSiteSearchDocumentBean>();
		List<TurSNSiteSpotlightTerm> turSNSiteSpotlightTerms = turSNSiteSpotlightTermRepository
				.findByNameIn(Arrays.asList(q.split(" ")));

		List<TurSNSiteSpotlight> turSNSiteSpotlights = turSNSiteSpotlightRepository
				.findDistinctByTurSNSiteAndTurSNSiteSpotlightTermsIn(turSNSite, turSNSiteSpotlightTerms);

		List<TurSEResult> seResults = turSEResults.getResults();

		Map<Integer, List<TurSNSiteSpotlightDocument>> turSNSiteSpotlightDocumentMap = new HashMap<>();
		turSNSiteSpotlights.forEach(spotlight -> {
			spotlight.getTurSNSiteSpotlightDocuments().forEach(document -> {
				if (turSNSiteSpotlightDocumentMap.containsKey(document.getPosition())) {
					turSNSiteSpotlightDocumentMap.get(document.getPosition()).add(document);
				} else {
					turSNSiteSpotlightDocumentMap.put(document.getPosition(), Arrays.asList(document));
				}
			});
		});

		int position = 1;
		for (TurSEResult result : seResults) {
			if (turSNSiteSpotlightDocumentMap.containsKey(position)) {
				List<TurSNSiteSpotlightDocument> turSNSiteSpotlightDocuments = turSNSiteSpotlightDocumentMap
						.get(position);
				turSNSiteSpotlightDocuments.forEach(document -> {
					TurSEResult turSEResult = turSolr.findById(document.getSearchId());
					if (turSEResult != null) {
						addSNDocument(request, fieldExtMap, facetMap, turSNSiteSearchDocumentsBean, turSEResult, true);
					}
				});
			}

			addSNDocument(request, fieldExtMap, facetMap, turSNSiteSearchDocumentsBean, result, false);

			position++;
		}
		turSNSiteSearchResultsBean.setDocument(turSNSiteSearchDocumentsBean);
		return turSNSiteSearchResultsBean;

	}

	private TurSNSiteSearchWidgetBean responseWidget(List<String> fq, HttpServletRequest request, TurSNSite turSNSite,TurSNSiteFilterQueryBean
			turSNSiteFilterQueryBean, List<TurSNSiteFieldExt> turSNSiteFacetFieldExts,
			Map<String, TurSNSiteFieldExt> facetMap, TurSEResults turSEResults) {
		TurSNSiteSearchWidgetBean turSNSiteSearchWidgetBean = new TurSNSiteSearchWidgetBean();
		turSNSiteSearchWidgetBean.setFacet(
				responseFacet(request, turSNSite, turSNSiteFilterQueryBean.getHiddenItems(), turSNSiteFacetFieldExts, facetMap, turSEResults));
		turSNSiteSearchWidgetBean.setFacetToRemove(responseFacetToRemove(fq, request));
		turSNSiteSearchWidgetBean.setSimilar(responseMLT(turSNSite, turSEResults));
		return turSNSiteSearchWidgetBean;

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

		if (turSNSite.getMlt() == 1 && turSEResults.getSimilarResults() != null
				&& !turSEResults.getSimilarResults().isEmpty()) {

			return turSEResults.getSimilarResults();
		}
		return null;
	}

	private List<TurSNSiteSearchFacetBean> responseFacet(HttpServletRequest request, TurSNSite turSNSite,
			List<String> hiddenFilterQuery, List<TurSNSiteFieldExt> turSNSiteFacetFieldExts,
			Map<String, TurSNSiteFieldExt> facetMap, TurSEResults turSEResults) {

		if (turSNSite.getFacet() == 1 && turSNSiteFacetFieldExts != null && !turSNSiteFacetFieldExts.isEmpty()) {

			List<TurSNSiteSearchFacetBean> turSNSiteSearchFacetBeans = new ArrayList<>();
			turSEResults.getFacetResults().forEach(facet -> {
				if (facetMap.containsKey(facet.getFacet()) && !hiddenFilterQuery.contains(facet.getFacet())
						&& !facet.getTurSEFacetResultAttr().isEmpty()) {
					TurSNSiteFieldExt turSNSiteFieldExt = facetMap.get(facet.getFacet());

					TurSNSiteSearchFacetBean turSNSiteSearchFacetBean = new TurSNSiteSearchFacetBean();
					List<TurSNSiteSearchFacetItemBean> turSNSiteSearchFacetItemBeans = new ArrayList<>();
					facet.getTurSEFacetResultAttr().values().forEach(facetItemObject -> {
						TurSEFacetResultAttr facetItem = (TurSEFacetResultAttr) facetItemObject;

						TurSNSiteSearchFacetItemBean turSNSiteSearchFacetItemBean = new TurSNSiteSearchFacetItemBean();
						turSNSiteSearchFacetItemBean.setCount(facetItem.getCount());
						turSNSiteSearchFacetItemBean.setLabel(facetItem.getAttribute());
						turSNSiteSearchFacetItemBean.setLink(
								this.addFilterQuery(request, facet.getFacet() + ":" + facetItem.getAttribute()));
						turSNSiteSearchFacetItemBeans.add(turSNSiteSearchFacetItemBean);
					});

					TurSNSiteSearchFacetLabelBean turSNSiteSearchFacetLabelBean = new TurSNSiteSearchFacetLabelBean();
					turSNSiteSearchFacetLabelBean.setLang("en");
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
		return null;
	}

	private TurSNSiteSearchFacetBean responseFacetToRemove(List<String> fq, HttpServletRequest request) {
		if (fq != null && !fq.isEmpty()) {

			List<TurSNSiteSearchFacetItemBean> turSNSiteSearchFacetToRemoveItemBeans = new ArrayList<>();
			fq.forEach(facetToRemove -> {
				String[] facetToRemoveParts = facetToRemove.split(":");
				if (facetToRemoveParts.length == 2) {
					String facetToRemoveValue = facetToRemoveParts[1].replaceAll("\"", "");

					TurSNSiteSearchFacetItemBean turSNSiteSearchFacetToRemoveItemBean = new TurSNSiteSearchFacetItemBean();
					turSNSiteSearchFacetToRemoveItemBean.setLabel(facetToRemoveValue);
					turSNSiteSearchFacetToRemoveItemBean.setLink(this.removeFilterQuery(request, facetToRemove));
					turSNSiteSearchFacetToRemoveItemBeans.add(turSNSiteSearchFacetToRemoveItemBean);
				}
			});
			TurSNSiteSearchFacetLabelBean turSNSiteSearchFacetToRemoveLabelBean = new TurSNSiteSearchFacetLabelBean();

			TurSNSiteSearchFacetBean turSNSiteSearchFacetToRemoveBean = new TurSNSiteSearchFacetBean();
			turSNSiteSearchFacetToRemoveLabelBean.setLang("en");
			turSNSiteSearchFacetToRemoveLabelBean.setText("Facets To Remove");
			turSNSiteSearchFacetToRemoveBean.setLabel(turSNSiteSearchFacetToRemoveLabelBean);
			turSNSiteSearchFacetToRemoveBean.setFacets(turSNSiteSearchFacetToRemoveItemBeans);
			return turSNSiteSearchFacetToRemoveBean;
		}
		return null;
	}

	private List<TurSNSiteSearchPaginationBean> responsePagination(HttpServletRequest request,
			TurSEResults turSEResults) {
		List<TurSNSiteSearchPaginationBean> turSNSiteSearchPaginationBeans = new ArrayList<TurSNSiteSearchPaginationBean>();

		int firstPagination = 1;
		int lastPagination = turSEResults.getPageCount();

		if (turSEResults.getCurrentPage() - 3 > 0) {
			firstPagination = turSEResults.getCurrentPage() - 3;
		} else if (turSEResults.getCurrentPage() - 3 <= 0) {
			firstPagination = 1;
		}
		if (turSEResults.getCurrentPage() + 3 <= turSEResults.getPageCount()) {
			lastPagination = turSEResults.getCurrentPage() + 3;
		} else if (turSEResults.getCurrentPage() + 3 > turSEResults.getPageCount()) {
			lastPagination = turSEResults.getPageCount();
		}

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

			turSNSiteSearchPaginationBean.setType(TurSNPaginationType.FIRST);
			turSNSiteSearchPaginationBean.setHref(this.addOrReplaceParameter(request, "p", Integer.toString(1)));
			turSNSiteSearchPaginationBean.setText("First");
			turSNSiteSearchPaginationBean.setPage(1);
			turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);
			if (turSEResults.getCurrentPage() <= turSEResults.getPageCount()) {
				turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
				turSNSiteSearchPaginationBean.setType(TurSNPaginationType.PREVIOUS);
				turSNSiteSearchPaginationBean.setHref(
						this.addOrReplaceParameter(request, "p", Integer.toString(turSEResults.getCurrentPage() - 1)));
				turSNSiteSearchPaginationBean.setText("Previous");
				turSNSiteSearchPaginationBean.setPage(turSEResults.getCurrentPage() - 1);
				turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);
			}

		}

		for (int page = firstPagination; page <= lastPagination; page++) {

			if (page == turSEResults.getCurrentPage()) {
				turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
				turSNSiteSearchPaginationBean.setHref(this.addOrReplaceParameter(request, "p", Integer.toString(page)));
				turSNSiteSearchPaginationBean.setType(TurSNPaginationType.CURRENT);
				turSNSiteSearchPaginationBean.setText(Integer.toString(page));
				turSNSiteSearchPaginationBean.setPage(page);
				turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);

			} else {
				turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
				turSNSiteSearchPaginationBean.setHref(this.addOrReplaceParameter(request, "p", Integer.toString(page)));
				turSNSiteSearchPaginationBean.setText(Integer.toString(page));
				turSNSiteSearchPaginationBean.setType(TurSNPaginationType.PAGE);
				turSNSiteSearchPaginationBean.setPage(page);
				turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);

			}
		}
		if (turSEResults.getCurrentPage() != turSEResults.getPageCount() && turSEResults.getPageCount() > 1) {
			if (turSEResults.getCurrentPage() <= turSEResults.getPageCount()) {
				turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
			
				turSNSiteSearchPaginationBean.setType(TurSNPaginationType.NEXT);
				turSNSiteSearchPaginationBean.setHref(
						this.addOrReplaceParameter(request, "p", Integer.toString(turSEResults.getCurrentPage() + 1)));
				turSNSiteSearchPaginationBean.setText("Next");
				turSNSiteSearchPaginationBean.setPage(turSEResults.getCurrentPage() + 1);
				turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);

			}

			turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
			turSNSiteSearchPaginationBean.setType(TurSNPaginationType.LAST);
			turSNSiteSearchPaginationBean
					.setHref(this.addOrReplaceParameter(request, "p", Integer.toString(turSEResults.getPageCount())));
			turSNSiteSearchPaginationBean.setText("Last");
			turSNSiteSearchPaginationBean.setPage(turSEResults.getPageCount());
			turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);
		}
		return turSNSiteSearchPaginationBeans;

	}

	@SuppressWarnings("unchecked")
	private void addSNDocument(HttpServletRequest request, Map<String, TurSNSiteFieldExt> fieldExtMap,
			Map<String, TurSNSiteFieldExt> facetMap, List<TurSNSiteSearchDocumentBean> turSNSiteSearchDocumentsBean,
			TurSEResult result, boolean isElevate) {
		TurSNSiteSearchDocumentBean turSNSiteSearchDocumentBean = new TurSNSiteSearchDocumentBean();
		Map<String, Object> turSEResultAttr = result.getFields();
		Set<String> attribs = turSEResultAttr.keySet();

		turSNSiteSearchDocumentBean.setElevate(isElevate);
		List<TurSNSiteSearchDocumentMetadataBean> turSNSiteSearchDocumentMetadataBeans = new ArrayList<TurSNSiteSearchDocumentMetadataBean>();

		for (Object facetObject : facetMap.keySet().toArray()) {

			String facet = (String) facetObject;
			if (turSEResultAttr.containsKey(facet)) {

				if (turSEResultAttr.get(facet) instanceof ArrayList) {
					for (Object facetValueObject : (ArrayList<?>) turSEResultAttr.get(facet)) {
						String facetValue = turSolrField.convertFieldToString(facetValueObject);
						TurSNSiteSearchDocumentMetadataBean turSNSiteSearchDocumentMetadataBean = new TurSNSiteSearchDocumentMetadataBean();
						turSNSiteSearchDocumentMetadataBean
								.setHref(this.addFilterQuery(request, facet + ":" + facetValue));
						turSNSiteSearchDocumentMetadataBean.setText(facetValue);
						turSNSiteSearchDocumentMetadataBeans.add(turSNSiteSearchDocumentMetadataBean);
					}
				} else {
					String facetValue = turSolrField.convertFieldToString(turSEResultAttr.get(facet));
					TurSNSiteSearchDocumentMetadataBean turSNSiteSearchDocumentMetadataBean = new TurSNSiteSearchDocumentMetadataBean();
					turSNSiteSearchDocumentMetadataBean.setHref(this.addFilterQuery(request, facet + ":" + facetValue));
					turSNSiteSearchDocumentMetadataBean.setText(facetValue);
					turSNSiteSearchDocumentMetadataBeans.add(turSNSiteSearchDocumentMetadataBean);
				}

			}

		}
		turSNSiteSearchDocumentBean.setMetadata(turSNSiteSearchDocumentMetadataBeans);

		if (turSEResultAttr.containsKey("url")) {
			turSNSiteSearchDocumentBean.setSource((String) turSEResultAttr.get("url"));
		}

		Map<String, Object> fields = new HashMap<String, Object>();
		for (String attribute : attribs) {
			if (!attribute.startsWith("turing_entity")) {
				String nodeName = null;
				if (fieldExtMap.containsKey(attribute)) {
					TurSNSiteFieldExt turSNSiteFieldExt = fieldExtMap.get(attribute);
					nodeName = turSNSiteFieldExt.getName();
				} else {
					nodeName = attribute;
				}
				if (nodeName != null && fields.containsKey(nodeName)) {
					if (!(fields.get(nodeName) instanceof List)) {
						List<Object> attributeValues = new ArrayList<Object>();
						attributeValues.add(fields.get(nodeName));
						attributeValues.add(turSEResultAttr.get(attribute));
						fields.put(nodeName, attributeValues);
					} else {
						((List<Object>) fields.get(nodeName)).add(turSEResultAttr.get(attribute));
					}
				} else {
					fields.put(nodeName, turSEResultAttr.get(attribute));

				}
			}

		}
		turSNSiteSearchDocumentBean.setFields(fields);
		turSNSiteSearchDocumentsBean.add(turSNSiteSearchDocumentBean);
	}
}
