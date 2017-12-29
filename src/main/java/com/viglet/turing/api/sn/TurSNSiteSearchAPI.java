package com.viglet.turing.api.sn;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrField;
import com.viglet.turing.api.sn.bean.TurSNSiteSearchBean;
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

@Component
@Path("/sn/{siteName}/search")
public class TurSNSiteSearchAPI {

	@Autowired
	TurSolr turSolr;
	@Autowired
	TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
	@Autowired
	TurSNSiteRepository turSNSiteRepository;
	@Autowired
	TurSolrField turSolrField;

	public String addOrReplaceParameter(UriInfo uriInfo, String paramName, String paramValue) {
		StringBuffer sbQueryString = new StringBuffer();
		boolean alreadyExists = false;
		MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
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

		return uriInfo.getAbsolutePath().getPath() + "?" + queryString;

	}

	public String addFilterQuery(UriInfo uriInfo, String fq) {
		StringBuffer sbQueryString = new StringBuffer();
		boolean alreadyExists = false;
		MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
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

		return uriInfo.getAbsolutePath().getPath() + "?" + queryString;

	}

	public String removeFilterQuery(UriInfo uriInfo, String fq) {
		StringBuffer sbQueryString = new StringBuffer();

		MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
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

		return uriInfo.getAbsolutePath().getPath() + "?" + queryString;

	}

	@GET
	@Produces("application/json")
	public TurSNSiteSearchBean select(@PathParam("siteName") String siteName, @QueryParam("q") String q,
			@QueryParam("p") int currentPage, @QueryParam("fq[]") List<String> fq, @QueryParam("sort") String sort,
			@Context UriInfo uriInfo) throws JSONException {

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
		MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
		for (Object queryParamObject : queryParams.keySet().toArray()) {
			String queryParam = (String) queryParamObject;
			for (String queryParamValue : queryParams.get(queryParam)) {
				// System.out.println("SNSearchAPI parameters:" + queryParam + " is " +
				// queryParamValue);
			}
		}

		List<String> filterQueryModified = new ArrayList<String>();
		List<String> hiddenFilterQuery = new ArrayList<String>();
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
		String[] filterQueryModifiedArr = new String[filterQueryModified.size()];
		filterQueryModifiedArr = filterQueryModified.toArray(filterQueryModifiedArr);

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
			turSEResults = turSolr.retrieveSolr(q, filterQueryModified, currentPage, sort);
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
										.setHref(this.addFilterQuery(uriInfo, facet + ":" + facetValue));
								turSNSiteSearchDocumentMetadataBean.setText(facetValue);
								turSNSiteSearchDocumentMetadataBeans.add(turSNSiteSearchDocumentMetadataBean);
							}
						} else {
							String facetValue = turSolrField.convertFieldToString(turSEResultAttr.get(facet));
							TurSNSiteSearchDocumentMetadataBean turSNSiteSearchDocumentMetadataBean = new TurSNSiteSearchDocumentMetadataBean();
							turSNSiteSearchDocumentMetadataBean
									.setHref(this.addFilterQuery(uriInfo, facet + ":" + facetValue));
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
						if (fieldExtMap.containsKey(attribute)) {
							TurSNSiteFieldExt turSNSiteFieldExt = fieldExtMap.get(attribute);
							fields.put(turSNSiteFieldExt.getName(), turSEResultAttr.get(attribute));
						} else {
							fields.put(attribute, turSEResultAttr.get(attribute));
						}
					}

				}
				turSNSiteSearchDocumentBean.setFields(fields);
				turSNSiteSearchDocumentsBean.add(turSNSiteSearchDocumentBean);

			}
			turSNSiteSearchResultsBean.setDocument(turSNSiteSearchDocumentsBean);
			turSNSiteSearchBean.setResults(turSNSiteSearchResultsBean);
		} catch (Exception e) {
			e.printStackTrace();
		}

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
			turSNSiteSearchPaginationBean.setHref(this.addOrReplaceParameter(uriInfo, "p", Integer.toString(1)));
			turSNSiteSearchPaginationBean.setText("FIRST");
			turSNSiteSearchPaginationBean.setPage(1);
			turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);
			if (turSEResults.getCurrentPage() <= turSEResults.getPageCount()) {
				turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
				turSNSiteSearchPaginationBean.setType("previous");
				turSNSiteSearchPaginationBean.setHref(
						this.addOrReplaceParameter(uriInfo, "p", Integer.toString(turSEResults.getCurrentPage() - 1)));
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
				turSNSiteSearchPaginationBean.setHref(this.addOrReplaceParameter(uriInfo, "p", Integer.toString(page)));
				turSNSiteSearchPaginationBean.setText(Integer.toString(page));
				turSNSiteSearchPaginationBean.setPage(page);
				turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);

			}
		}
		if (turSEResults.getCurrentPage() != turSEResults.getPageCount() && turSEResults.getPageCount() > 1) {
			if (turSEResults.getCurrentPage() <= turSEResults.getPageCount()) {
				turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
				turSNSiteSearchPaginationBean.setType("next");
				turSNSiteSearchPaginationBean.setHref(
						this.addOrReplaceParameter(uriInfo, "p", Integer.toString(turSEResults.getCurrentPage() + 1)));
				turSNSiteSearchPaginationBean.setText("NEXT");
				turSNSiteSearchPaginationBean.setPage(turSEResults.getCurrentPage() + 1);
				turSNSiteSearchPaginationBeans.add(turSNSiteSearchPaginationBean);

			}

			turSNSiteSearchPaginationBean = new TurSNSiteSearchPaginationBean();
			turSNSiteSearchPaginationBean.setType("last");
			turSNSiteSearchPaginationBean
					.setHref(this.addOrReplaceParameter(uriInfo, "p", Integer.toString(turSEResults.getPageCount())));
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
								this.addFilterQuery(uriInfo, facet.getFacet() + ":" + facetItem.getAttribute()));
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
			if (fq.size() > 0) {

				TurSNSiteSearchFacetBean turSNSiteSearchFacetToRemoveBean = new TurSNSiteSearchFacetBean();
				List<TurSNSiteSearchFacetItemBean> turSNSiteSearchFacetToRemoveItemBeans = new ArrayList<TurSNSiteSearchFacetItemBean>();
				for (String facetToRemove : fq) {
					String[] facetToRemoveParts = facetToRemove.split(":");
					if (facetToRemoveParts.length == 2) {
						String facetToRemoveValue = facetToRemoveParts[1].replaceAll("\"", "");

						TurSNSiteSearchFacetItemBean turSNSiteSearchFacetToRemoveItemBean = new TurSNSiteSearchFacetItemBean();
						turSNSiteSearchFacetToRemoveItemBean.setLabel(facetToRemoveValue);
						turSNSiteSearchFacetToRemoveItemBean.setLink(this.removeFilterQuery(uriInfo, facetToRemove));
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

		turSNSiteSearchBean.setWidget(turSNSiteSearchWidgetBean);
		TurSNSiteSearchQueryContextBean turSNSiteSearchQueryContextBean = new TurSNSiteSearchQueryContextBean();
		turSNSiteSearchQueryContextBean.setQuery(turSNSiteSearchQueryContextQueryBean);

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

		return turSNSiteSearchBean;
	}
}
