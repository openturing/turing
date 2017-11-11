package com.viglet.turing.api.sn;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
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
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrField;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.facet.TurSEFacetResultAttr;
import com.viglet.turing.se.field.TurSEFieldMap;
import com.viglet.turing.se.field.TurSEFieldMaps;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResultAttr;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.se.similar.TurSESimilarResult;
import com.viglet.turing.se.similar.TurSESimilarResultAttr;
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
	public Response select(@PathParam("siteName") String siteName, @QueryParam("q") String q,
			@QueryParam("p") int currentPage, @QueryParam("fq[]") List<String> fq, @Context UriInfo uriInfo)
			throws JSONException {

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

		Map<String, TurSEFieldMap> fieldMap = new TurSEFieldMaps().getFieldMaps();

		TurSNSite turSNSite = turSNSiteRepository.findByName(siteName);

		List<TurSNSiteFieldExt> turSNSiteFacetFieldExts = turSNSiteFieldExtRepository
				.findByTurSNSiteAndFacetAndEnabled(turSNSite, 1, 1);

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
		JSONArray snResults = new JSONArray();

		turSolr.init(turSNSite);
		try {
			turSEResults = turSolr.retrieveSolr(q, filterQueryModified, currentPage);
			List<TurSEResult> seResults = turSEResults.getResults();
			// System.out.println("getResults size:" + turSEResults.getResults().size());
			for (TurSEResult result : seResults) {
				JSONObject snResult = new JSONObject();
				Map<String, TurSEResultAttr> turSEResultAttr = result.getTurSEResultAttr();
				Set<String> attribs = turSEResultAttr.keySet();

				snResult.put("elevate", false);

				JSONArray snMetadata = new JSONArray();
				for (Object facetObject : facetMap.keySet().toArray()) {
					String facet = (String) facetObject;
					if (turSEResultAttr.containsKey(facet)) {

						if (turSEResultAttr.get(facet).getAttrJSON().get(facet) instanceof ArrayList) {
							for (Object facetValueObject : (ArrayList) turSEResultAttr.get(facet).getAttrJSON()
									.get(facet)) {
								String facetValue = (String) facetValueObject;
								JSONObject snMetadataItem = new JSONObject();
								snMetadataItem.put("href", this.addFilterQuery(uriInfo, facet + ":" + facetValue));
								snMetadataItem.put("text", facetValue);
								snMetadata.put(snMetadataItem);
							}
						} else {
							JSONObject snMetadataItem = new JSONObject();
							String facetValue = turSolrField
									.convertFieldToString(turSEResultAttr.get(facet).getAttrJSON().get(facet));
							
							snMetadataItem.put("text", facetValue);
							snMetadataItem.put("href", this.addFilterQuery(uriInfo, facet + ":" + facetValue));

							snMetadata.put(snMetadataItem);
						}

					}
				}

				snResult.put("metadata", snMetadata);
				if (turSEResultAttr.containsKey("url")) {
					snResult.put("source", new JSONObject().put("resource",
							turSEResultAttr.get("url").getAttrJSON().getString("url")));
				}
				for (String attribute : attribs) {
					// System.out.println("attribs: " + attribute);
					if (!attribute.startsWith("turing_entity")) {
						if (fieldMap.containsKey(attribute)) {
							TurSEFieldMap turSEFieldMap = fieldMap.get(attribute);
							snResult.put(turSEFieldMap.getAlias(),
									turSEResultAttr.get(attribute).getAttrJSON().get(attribute));
						} else {
							snResult.put(attribute, turSEResultAttr.get(attribute).getAttrJSON().get(attribute));
						}
					}
				}
				snResults.put(snResult);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		JSONObject jsonSearch = new JSONObject();
		JSONObject jsonRDFDescription = new JSONObject();
		JSONObject jsonRDFResource = new JSONObject();
		JSONObject jsonSNDocument = new JSONObject();
		JSONObject jsonSNQueryContext = new JSONObject();
		JSONObject jsonSNQuery = new JSONObject();
		JSONObject jsonSNWidget = new JSONObject();
		JSONObject jsonSNPagination = new JSONObject();
		JSONArray jsonSNPaginationPages = new JSONArray();
		JSONObject jsonSNPaginationPage = new JSONObject();
		// BEGIN Pagination
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

		if (turSEResults.getCurrentPage() > 1) {
			jsonSNPaginationPage = new JSONObject();
			jsonSNPaginationPage.put("class", "first");
			jsonSNPaginationPage.put("href", this.addOrReplaceParameter(uriInfo, "p", Integer.toString(1)));
			jsonSNPaginationPage.put("page", 1);
			jsonSNPaginationPage.put("text", "FIRST");
			jsonSNPaginationPages.put(jsonSNPaginationPage);

			if (turSEResults.getCurrentPage() <= turSEResults.getPageCount()) {
				jsonSNPaginationPage = new JSONObject();
				jsonSNPaginationPage.put("class", "previous");
				jsonSNPaginationPage.put("href",
						this.addOrReplaceParameter(uriInfo, "p", Integer.toString(turSEResults.getCurrentPage() - 1)));
				jsonSNPaginationPage.put("page", turSEResults.getCurrentPage() - 1);
				jsonSNPaginationPage.put("text", "PREVIOUS");
				jsonSNPaginationPages.put(jsonSNPaginationPage);
			}

		}

		for (int page = firstPagination; page <= lastPagination; page++) {

			if (page == turSEResults.getCurrentPage()) {
				jsonSNPaginationPage = new JSONObject();
				jsonSNPaginationPage.put("class", "current");
				jsonSNPaginationPage.put("text", Integer.toString(page));
				jsonSNPaginationPages.put(jsonSNPaginationPage);
			} else {
				jsonSNPaginationPage = new JSONObject();
				jsonSNPaginationPage.put("href", this.addOrReplaceParameter(uriInfo, "p", Integer.toString(page)));
				jsonSNPaginationPage.put("page", page);
				jsonSNPaginationPage.put("text", Integer.toString(page));
				jsonSNPaginationPages.put(jsonSNPaginationPage);

			}
		}
		if (turSEResults.getCurrentPage() != turSEResults.getPageCount() && turSEResults.getPageCount() > 1) {
			if (turSEResults.getCurrentPage() <= turSEResults.getPageCount()) {
				jsonSNPaginationPage = new JSONObject();
				jsonSNPaginationPage.put("class", "next");
				jsonSNPaginationPage.put("href",
						this.addOrReplaceParameter(uriInfo, "p", Integer.toString(turSEResults.getCurrentPage() + 1)));
				jsonSNPaginationPage.put("page", turSEResults.getCurrentPage() + 1);
				jsonSNPaginationPage.put("text", "NEXT");
				jsonSNPaginationPages.put(jsonSNPaginationPage);
			}

			jsonSNPaginationPage = new JSONObject();
			jsonSNPaginationPage.put("class", "last");
			jsonSNPaginationPage.put("href",
					this.addOrReplaceParameter(uriInfo, "p", Integer.toString(turSEResults.getPageCount())));
			jsonSNPaginationPage.put("page", turSEResults.getPageCount());
			jsonSNPaginationPage.put("text", "LAST");
			jsonSNPaginationPages.put(jsonSNPaginationPage);
		}

		// END Pagination

		jsonSNPagination.put("page", jsonSNPaginationPages);
		jsonSNDocument.put("document", snResults);
		jsonSNQuery.put("query-string", turSEResults.getQueryString());
		jsonSNQuery.put("sort", turSEResults.getSort());

		if (turSNSite.getFacet() == 1) {
			// BEGIN Facet
			JSONObject jsonSNFacetWidget = new JSONObject();
			for (TurSEFacetResult facet : turSEResults.getFacetResults()) {

				if (facetMap.containsKey(facet.getFacet()) && !hiddenFilterQuery.contains(facet.getFacet())
						&& facet.getTurSEFacetResultAttr().size() > 0) {
					TurSNSiteFieldExt turSNSiteFieldExt = facetMap.get(facet.getFacet());

					JSONObject jsonSNFacetWidgetEntityLabel = new JSONObject();
					JSONArray jsonSNFacetWidgetEntityItems = new JSONArray();
					JSONObject jsonSNFacetWidgetEntity = new JSONObject();

					for (Object facetItemObject : facet.getTurSEFacetResultAttr().values().toArray()) {

						JSONObject jsonSNFacetWidgetEntityItem = new JSONObject();

						TurSEFacetResultAttr facetItem = (TurSEFacetResultAttr) facetItemObject;
						jsonSNFacetWidgetEntityItem.put("facet-link",
								this.addFilterQuery(uriInfo, facet.getFacet() + ":" + facetItem.getAttribute()));
						jsonSNFacetWidgetEntityItem.put("label", facetItem.getAttribute());
						jsonSNFacetWidgetEntityItem.put("facet-count", Integer.toString(facetItem.getCount()));
						jsonSNFacetWidgetEntityItems.put(jsonSNFacetWidgetEntityItem);
					}

					jsonSNFacetWidgetEntityLabel.put("lang", "en");
					jsonSNFacetWidgetEntityLabel.put("text", turSNSiteFieldExt.getFacetName());
					jsonSNFacetWidgetEntity.put("label", jsonSNFacetWidgetEntityLabel);
					jsonSNFacetWidgetEntity.put("facets",
							(new JSONObject()).put("facet", jsonSNFacetWidgetEntityItems));

					jsonSNFacetWidget.put(turSNSiteFieldExt.getName(), jsonSNFacetWidgetEntity);

				}
			}

			// BEGIN Facet Remove
			if (fq.size() > 0) {
				JSONObject jsonSNFacetToRemoveWidgetEntityLabel = new JSONObject();
				JSONArray jsonSNFacetToRemoveWidgetEntityItems = new JSONArray();
				JSONObject jsonSNFacetToRemoveWidgetEntity = new JSONObject();

				for (String facetToRemove : fq) {
					String[] facetToRemoveParts = facetToRemove.split(":");
					if (facetToRemoveParts.length == 2) {
						String facetToRemoveValue = facetToRemoveParts[1].replaceAll("\"", "");

						JSONObject jsonSNFacetToRemoveWidgetEntityItem = new JSONObject();
						jsonSNFacetToRemoveWidgetEntityItem.put("facet-link",
								this.removeFilterQuery(uriInfo, facetToRemove));
						jsonSNFacetToRemoveWidgetEntityItem.put("label", facetToRemoveValue);
						jsonSNFacetToRemoveWidgetEntityItems.put(jsonSNFacetToRemoveWidgetEntityItem);
					}
				}

				jsonSNFacetToRemoveWidgetEntityLabel.put("lang", "en");
				jsonSNFacetToRemoveWidgetEntityLabel.put("text", "Facets To Remove");
				jsonSNFacetToRemoveWidgetEntity.put("label", jsonSNFacetToRemoveWidgetEntityLabel);
				jsonSNFacetToRemoveWidgetEntity.put("facets",
						(new JSONObject()).put("facet", jsonSNFacetToRemoveWidgetEntityItems));
				jsonSNFacetWidget.put("facet-to-remove", jsonSNFacetToRemoveWidgetEntity);
			}

			// END Facet Remove

			jsonSNWidget.put("facet-widget", jsonSNFacetWidget);
			// END Facet
		}
		if (turSNSite.getMlt() == 1) {
			// BEGIN Similar
			JSONArray jsonSNSimilarWidgetItems = new JSONArray();
			for (TurSESimilarResult similar : turSEResults.getSimilarResults()) {
				JSONObject jsonSNSimilarWidgetItem = new JSONObject();
				for (Object similarItemObject : similar.getTurSESimilarResultAttr().values().toArray()) {
					TurSESimilarResultAttr similarItem = (TurSESimilarResultAttr) similarItemObject;
					jsonSNSimilarWidgetItem.put(similarItem.getAttribute(), similarItem.getValue());
				}
				jsonSNSimilarWidgetItems.put(jsonSNSimilarWidgetItem);
			}

			jsonSNWidget.put("similar-widget", jsonSNSimilarWidgetItems);

			// END Similar
		}
		jsonSNQueryContext.put("pageEnd", 7);
		jsonSNQueryContext.put("pageStart", turSEResults.getStart());
		jsonSNQueryContext.put("pageCount", turSEResults.getPageCount());
		jsonSNQueryContext.put("page", turSEResults.getCurrentPage());
		jsonSNQueryContext.put("count", turSEResults.getNumFound());
		jsonSNQueryContext.put("limit", turSEResults.getLimit());
		jsonSNQueryContext.put("offset", 0);
		jsonSNQueryContext.put("response-time", turSEResults.getElapsedTime());
		jsonSNQueryContext.put("query", jsonSNQuery);
		jsonSNQueryContext.put("index", turSNSite.getName());

		jsonRDFDescription.put("pagination", jsonSNPagination);
		jsonRDFDescription.put("widget", jsonSNWidget);
		jsonRDFDescription.put("results", jsonSNDocument);
		jsonRDFDescription.put("query-context", jsonSNQueryContext);
		jsonRDFDescription.put("type", jsonRDFResource);

		jsonSearch.put("description", jsonRDFDescription);

		return Response.status(200).entity(jsonSearch.toString()).build();
	}
}
