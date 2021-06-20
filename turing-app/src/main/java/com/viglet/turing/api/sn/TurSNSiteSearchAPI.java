/*
 * Copyright (C) 2016-2019 the original author or authors. 
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrField;

import io.swagger.annotations.Api;

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
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.facet.TurSEFacetResultAttr;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.sn.TurSNFieldType;

@RestController
@RequestMapping("/api/sn/{siteName}/search")
@Api(tags = "Semantic Navigation Search", description = "Semantic Navigation Search API")
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

	public String addOrReplaceParameter(HttpServletRequest request, String paramName, String paramValue) {
		StringBuffer sbQueryString = new StringBuffer();
		boolean alreadyExists = false;
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		String queryString = sbQueryString.toString().substring(0, sbQueryString.toString().length() - 1);

		return request.getRequestURI() + "?" + queryString;

	}

	public String addFilterQuery(HttpServletRequest request, String fq) {
		StringBuffer sbQueryString = new StringBuffer();
		boolean alreadyExists = false;
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		String queryString = sbQueryString.toString().substring(0, sbQueryString.toString().length() - 1);

		return request.getRequestURI() + "?" + queryString;

	}

	public String removeFilterQuery(HttpServletRequest request, String fq) {
		StringBuffer sbQueryString = new StringBuffer();

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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		String queryString = sbQueryString.toString().substring(0, sbQueryString.toString().length() - 1);

		return request.getRequestURI() + "?" + queryString;

	}

	@SuppressWarnings("unchecked")
	@GetMapping
	public TurSNSiteSearchBean turSNSiteSearchSelect(@PathVariable String siteName,
			@RequestParam(required = false, name = "q") String q,
			@RequestParam(required = false, name = "p") Integer currentPage,
			@RequestParam(required = false, name = "fq[]") List<String> fq,
			@RequestParam(required = false, name = "tr[]") List<String> tr,
			@RequestParam(required = false, name = "sort") String sort,
			@RequestParam(required = false, name = "rows") Integer rows, HttpServletRequest request)
			throws JSONException {

		if (currentPage == null || currentPage <= 0) {
			currentPage = 1;
		}
		if (rows == null) {
			rows = 0;
		}

		TurSNSite turSNSite = turSNSiteRepository.findByName(siteName);

		TurSNSiteSearchBean turSNSiteSearchBean = new TurSNSiteSearchBean();
		TurSNSiteSearchResultsBean turSNSiteSearchResultsBean = new TurSNSiteSearchResultsBean();

		List<TurSNSiteFieldExt> turSNSiteFieldExts = turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(turSNSite,
				1);
		List<TurSNSiteFieldExt> turSNSiteFacetFieldExts = turSNSiteFieldExtRepository
				.findByTurSNSiteAndFacetAndEnabled(turSNSite, 1, 1);

		Map<String, TurSNSiteFieldExt> fieldExtMap = new HashMap<String, TurSNSiteFieldExt>();

		for (TurSNSiteFieldExt turSNSiteFieldExt : turSNSiteFieldExts) {
			fieldExtMap.put(turSNSiteFieldExt.getName(), turSNSiteFieldExt);
		}

		if (currentPage <= 0) {
			currentPage = 1;
		}

		List<String> filterQueryModified = new ArrayList<String>();
		List<String> hiddenFilterQuery = new ArrayList<String>();
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

		Map<String, TurSNSiteFieldExt> facetMap = new HashMap<String, TurSNSiteFieldExt>();

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

		TurSEResults turSEResults = null;

		turSolr.init(turSNSite);
		try {

			turSEResults = turSolr.retrieveSolr(q, filterQueryModified, targetingRuleModified, currentPage.intValue(),
					sort, rows.intValue());
			if (turSEResults != null) {
				List<TurSEResult> seResults = turSEResults.getResults();
				// System.out.println("getResults size:" + turSEResults.getResults().size());
				List<TurSNSiteSearchDocumentBean> turSNSiteSearchDocumentsBean = new ArrayList<TurSNSiteSearchDocumentBean>();
				for (TurSEResult result : seResults) {
					TurSNSiteSearchDocumentBean turSNSiteSearchDocumentBean = new TurSNSiteSearchDocumentBean();

					Map<String, Object> turSEResultAttr = result.getFields();
					Set<String> attribs = turSEResultAttr.keySet();

					turSNSiteSearchDocumentBean.setElevate(false);
					List<TurSNSiteSearchDocumentMetadataBean> turSNSiteSearchDocumentMetadataBeans = new ArrayList<TurSNSiteSearchDocumentMetadataBean>();

					for (Object facetObject : facetMap.keySet().toArray()) {

						String facet = (String) facetObject;
						if (turSEResultAttr.containsKey(facet)) {

							if (turSEResultAttr.get(facet) instanceof ArrayList) {
								for (Object facetValueObject : (ArrayList) turSEResultAttr.get(facet)) {
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
								turSNSiteSearchDocumentMetadataBean
										.setHref(this.addFilterQuery(request, facet + ":" + facetValue));
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

						// System.out.println("attribs: " + attribute);
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
				turSNSiteSearchResultsBean.setDocument(turSNSiteSearchDocumentsBean);
				turSNSiteSearchBean.setResults(turSNSiteSearchResultsBean);
			} else {
				if (logger.isDebugEnabled()) {
					logger.debug("No Results");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (turSEResults != null) {
			// BEGIN Pagination
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

				turSNSiteSearchPaginationBean.setType("first");
				turSNSiteSearchPaginationBean.setHref(this.addOrReplaceParameter(request, "p", Integer.toString(1)));
				turSNSiteSearchPaginationBean.setText("FIRST");
				turSNSiteSearchPaginationBean.setPage(1);
				turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);
				if (turSEResults.getCurrentPage() <= turSEResults.getPageCount()) {
					turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
					turSNSiteSearchPaginationBean.setType("previous");
					turSNSiteSearchPaginationBean.setHref(this.addOrReplaceParameter(request, "p",
							Integer.toString(turSEResults.getCurrentPage() - 1)));
					turSNSiteSearchPaginationBean.setText("PREVIOUS");
					turSNSiteSearchPaginationBean.setPage(turSEResults.getCurrentPage() - 1);
					turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);
				}

			}

			for (int page = firstPagination; page <= lastPagination; page++) {

				if (page == turSEResults.getCurrentPage()) {
					turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
					turSNSiteSearchPaginationBean.setType("current");
					turSNSiteSearchPaginationBean.setText(Integer.toString(page));
					turSNSiteSearchPaginationBean.setPage(page);
					turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);

				} else {
					turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
					turSNSiteSearchPaginationBean
							.setHref(this.addOrReplaceParameter(request, "p", Integer.toString(page)));
					turSNSiteSearchPaginationBean.setText(Integer.toString(page));
					turSNSiteSearchPaginationBean.setPage(page);
					turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);

				}
			}
			if (turSEResults.getCurrentPage() != turSEResults.getPageCount() && turSEResults.getPageCount() > 1) {
				if (turSEResults.getCurrentPage() <= turSEResults.getPageCount()) {
					turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
					turSNSiteSearchPaginationBean.setType("next");
					turSNSiteSearchPaginationBean.setHref(this.addOrReplaceParameter(request, "p",
							Integer.toString(turSEResults.getCurrentPage() + 1)));
					turSNSiteSearchPaginationBean.setText("NEXT");
					turSNSiteSearchPaginationBean.setPage(turSEResults.getCurrentPage() + 1);
					turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);

				}

				turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
				turSNSiteSearchPaginationBean.setType("last");
				turSNSiteSearchPaginationBean.setHref(
						this.addOrReplaceParameter(request, "p", Integer.toString(turSEResults.getPageCount())));
				turSNSiteSearchPaginationBean.setText("LAST");
				turSNSiteSearchPaginationBean.setPage(turSEResults.getPageCount());
				turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);

			}

			// END Pagination
			turSNSiteSearchBean.setPagination(turSNSiteSearchPaginationBeans);

			TurSNSiteSearchQueryContextQueryBean turSNSiteSearchQueryContextQueryBean = new TurSNSiteSearchQueryContextQueryBean();

			turSNSiteSearchQueryContextQueryBean.setQueryString(turSEResults.getQueryString());
			turSNSiteSearchQueryContextQueryBean.setSort(turSEResults.getSort());

			TurSNSiteSearchWidgetBean turSNSiteSearchWidgetBean = new TurSNSiteSearchWidgetBean();
			// BEGIN Facet
			if (turSNSite.getFacet() == 1 && turSNSiteFacetFieldExts != null && turSNSiteFacetFieldExts.size() > 0) {

				List<TurSNSiteSearchFacetBean> turSNSiteSearchFacetBeans = new ArrayList<TurSNSiteSearchFacetBean>();
				for (TurSEFacetResult facet : turSEResults.getFacetResults()) {

					if (facetMap.containsKey(facet.getFacet()) && !hiddenFilterQuery.contains(facet.getFacet())
							&& facet.getTurSEFacetResultAttr().size() > 0) {
						TurSNSiteFieldExt turSNSiteFieldExt = facetMap.get(facet.getFacet());

						TurSNSiteSearchFacetBean turSNSiteSearchFacetBean = new TurSNSiteSearchFacetBean();
						List<TurSNSiteSearchFacetItemBean> turSNSiteSearchFacetItemBeans = new ArrayList<TurSNSiteSearchFacetItemBean>();
						for (Object facetItemObject : facet.getTurSEFacetResultAttr().values().toArray()) {

							TurSEFacetResultAttr facetItem = (TurSEFacetResultAttr) facetItemObject;

							TurSNSiteSearchFacetItemBean turSNSiteSearchFacetItemBean = new TurSNSiteSearchFacetItemBean();
							turSNSiteSearchFacetItemBean.setCount(facetItem.getCount());
							turSNSiteSearchFacetItemBean.setLabel(facetItem.getAttribute());
							turSNSiteSearchFacetItemBean.setLink(
									this.addFilterQuery(request, facet.getFacet() + ":" + facetItem.getAttribute()));
							turSNSiteSearchFacetItemBeans.add(turSNSiteSearchFacetItemBean);
						}

						TurSNSiteSearchFacetLabelBean turSNSiteSearchFacetLabelBean = new TurSNSiteSearchFacetLabelBean();
						turSNSiteSearchFacetLabelBean.setLang("en");
						turSNSiteSearchFacetLabelBean.setText(turSNSiteFieldExt.getFacetName());
						turSNSiteSearchFacetBean.setLabel(turSNSiteSearchFacetLabelBean);
						turSNSiteSearchFacetBean.setFacets(turSNSiteSearchFacetItemBeans);

						turSNSiteSearchFacetBeans.add(turSNSiteSearchFacetBean);
					}
				}

				turSNSiteSearchWidgetBean.setFacet(turSNSiteSearchFacetBeans);

				// BEGIN Facet Remove
				if (fq != null && fq.size() > 0) {

					TurSNSiteSearchFacetBean turSNSiteSearchFacetToRemoveBean = new TurSNSiteSearchFacetBean();
					List<TurSNSiteSearchFacetItemBean> turSNSiteSearchFacetToRemoveItemBeans = new ArrayList<TurSNSiteSearchFacetItemBean>();
					for (String facetToRemove : fq) {
						String[] facetToRemoveParts = facetToRemove.split(":");
						if (facetToRemoveParts.length == 2) {
							String facetToRemoveValue = facetToRemoveParts[1].replaceAll("\"", "");

							TurSNSiteSearchFacetItemBean turSNSiteSearchFacetToRemoveItemBean = new TurSNSiteSearchFacetItemBean();
							turSNSiteSearchFacetToRemoveItemBean.setLabel(facetToRemoveValue);
							turSNSiteSearchFacetToRemoveItemBean
									.setLink(this.removeFilterQuery(request, facetToRemove));
							turSNSiteSearchFacetToRemoveItemBeans.add(turSNSiteSearchFacetToRemoveItemBean);
						}
					}
					TurSNSiteSearchFacetLabelBean turSNSiteSearchFacetToRemoveLabelBean = new TurSNSiteSearchFacetLabelBean();
					turSNSiteSearchFacetToRemoveLabelBean.setLang("en");
					turSNSiteSearchFacetToRemoveLabelBean.setText("Facets To Remove");
					turSNSiteSearchFacetToRemoveBean.setLabel(turSNSiteSearchFacetToRemoveLabelBean);
					turSNSiteSearchFacetToRemoveBean.setFacets(turSNSiteSearchFacetToRemoveItemBeans);
					turSNSiteSearchWidgetBean.setFacetToRemove(turSNSiteSearchFacetToRemoveBean);
				}
				// END Facet Remove
			}

			// END Facet

			// BEGIN Similar
			if (turSNSite.getMlt() == 1 && turSEResults.getSimilarResults() != null
					&& turSEResults.getSimilarResults().size() > 0) {
				turSNSiteSearchWidgetBean.setSimilar(turSEResults.getSimilarResults());
			}
			// END Similar

			TurSNSiteSearchDefaultFieldsBean turSNSiteSearchDefaultFieldsBean = new TurSNSiteSearchDefaultFieldsBean();
			turSNSiteSearchDefaultFieldsBean.setDate(turSNSite.getDefaultDateField());
			turSNSiteSearchDefaultFieldsBean.setDescription(turSNSite.getDefaultDescriptionField());
			turSNSiteSearchDefaultFieldsBean.setImage(turSNSite.getDefaultImageField());
			turSNSiteSearchDefaultFieldsBean.setText(turSNSite.getDefaultTextField());
			turSNSiteSearchDefaultFieldsBean.setTitle(turSNSite.getDefaultTitleField());
			turSNSiteSearchDefaultFieldsBean.setUrl(turSNSite.getDefaultURLField());

			turSNSiteSearchBean.setWidget(turSNSiteSearchWidgetBean);
			TurSNSiteSearchQueryContextBean turSNSiteSearchQueryContextBean = new TurSNSiteSearchQueryContextBean();
			turSNSiteSearchQueryContextBean.setQuery(turSNSiteSearchQueryContextQueryBean);
			turSNSiteSearchQueryContextBean.setDefaultFields(turSNSiteSearchDefaultFieldsBean);

			turSNSiteSearchQueryContextBean.setPageEnd((int) turSEResults.getStart() + turSEResults.getLimit());
			turSNSiteSearchQueryContextBean.setPageStart((int) turSEResults.getStart() + 1);
			turSNSiteSearchQueryContextBean.setPageCount(turSEResults.getPageCount());
			turSNSiteSearchQueryContextBean.setPage(turSEResults.getCurrentPage());
			turSNSiteSearchQueryContextBean.setCount((int) turSEResults.getNumFound());
			turSNSiteSearchQueryContextBean.setLimit(turSEResults.getLimit());
			turSNSiteSearchQueryContextBean.setOffset(0); // Corrigir
			turSNSiteSearchQueryContextBean.setResponseTime(turSEResults.getElapsedTime());
			turSNSiteSearchQueryContextBean.setIndex(turSNSite.getName());

			turSNSiteSearchBean.setQueryContext(turSNSiteSearchQueryContextBean);
		} else {
			if (logger.isDebugEnabled()) {
				logger.debug("No Results");
			}
		}
		return turSNSiteSearchBean;
	}
}
