package com.viglet.turing.api.otsn.search;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
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
import com.viglet.turing.se.facet.TurSEFacetMap;
import com.viglet.turing.se.facet.TurSEFacetMaps;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.facet.TurSEFacetResultAttr;
import com.viglet.turing.se.field.TurSEFieldMap;
import com.viglet.turing.se.field.TurSEFieldMaps;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResultAttr;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.se.similar.TurSESimilarResult;
import com.viglet.turing.se.similar.TurSESimilarResultAttr;

@Component
@Path("otsn/search")
public class TurOTSNSearchAPI {

	@Autowired
	TurSolr turSolr;

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
	@Path("theme/json")
	@Produces("application/json")
	public Response select(@QueryParam("q") String q, @QueryParam("p") int currentPage,
			@QueryParam("fq[]") List<String> fq, @Context UriInfo uriInfo) throws JSONException {

		if (currentPage <= 0) {
			currentPage = 1;
		}
		MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
		for (Object queryParamObject : queryParams.keySet().toArray()) {
			String queryParam = (String) queryParamObject;
			for (String queryParamValue : queryParams.get(queryParam)) {
				System.out.println("OTSNSearchAPI parameters:" + queryParam + " is " + queryParamValue);
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
		Map<String, TurSEFacetMap> facetMap = new TurSEFacetMaps().getFacetMaps();

		TurSEResults turSEResults = null;
		JSONArray otsnResults = new JSONArray();

		turSolr.init();
		try {
			turSEResults = turSolr.retrieveSolr(q, filterQueryModified, currentPage);
			List<TurSEResult> seResults = turSEResults.getResults();
			System.out.println("getResults size:" + turSEResults.getResults().size());
			for (TurSEResult result : seResults) {
				JSONObject otsnResult = new JSONObject();
				Map<String, TurSEResultAttr> turSEResultAttr = result.getTurSEResultAttr();
				Set<String> attribs = turSEResultAttr.keySet();

				otsnResult.put("otsn:elevate", false);

				JSONArray otsnMetadata = new JSONArray();
				for (Object facetObject : facetMap.keySet().toArray()) {
					String facet = (String) facetObject;
					if (turSEResultAttr.containsKey(facet)) {

						if (turSEResultAttr.get(facet).getAttrJSON().get(facet) instanceof ArrayList) {
							for (Object facetValueObject : (ArrayList) turSEResultAttr.get(facet).getAttrJSON()
									.get(facet)) {
								String facetValue = (String) facetValueObject;
								JSONObject otsnMetadataItem = new JSONObject();
								otsnMetadataItem.put("href", this.addFilterQuery(uriInfo, facet + ":" + facetValue));
								otsnMetadataItem.put("text", facetValue);
								otsnMetadata.put(otsnMetadataItem);
							}
						} else {
							JSONObject otsnMetadataItem = new JSONObject();
							if (turSEResultAttr.get(facet).getAttrJSON().get(facet) instanceof String[]) {
								String[] facetValue = (String[]) turSEResultAttr.get(facet).getAttrJSON().get(facet);
								otsnMetadataItem.put("text", facetValue[0]);
								otsnMetadataItem.put("href", this.addFilterQuery(uriInfo, facet + ":" + facetValue[0]));
							} else {
								String facetValue = (String) turSEResultAttr.get(facet).getAttrJSON().get(facet);
								otsnMetadataItem.put("text", facetValue);
								otsnMetadataItem.put("href", this.addFilterQuery(uriInfo, facet + ":" + facetValue));
							}

							otsnMetadata.put(otsnMetadataItem);
						}

					}
				}

				otsnResult.put("dc:metadata", otsnMetadata);
				if (turSEResultAttr.containsKey("url")) {
					otsnResult.put("dc:source", new JSONObject().put("rdf:resource",
							turSEResultAttr.get("url").getAttrJSON().getString("url")));
				}
				for (String attribute : attribs) {
					// System.out.println("attribs: " + attribute);
					if (!attribute.startsWith("turing_entity")) {
						if (fieldMap.containsKey(attribute)) {
							TurSEFieldMap turSEFieldMap = fieldMap.get(attribute);
							otsnResult.put(turSEFieldMap.getAlias(),
									turSEResultAttr.get(attribute).getAttrJSON().get(attribute));
						} else {
							otsnResult.put(attribute, turSEResultAttr.get(attribute).getAttrJSON().get(attribute));
						}
					}
				}
				otsnResults.put(otsnResult);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		JSONObject jsonSearch = new JSONObject();
		JSONObject jsonRDFDescription = new JSONObject();
		JSONObject jsonRDFResource = new JSONObject();
		JSONObject jsonOTSNDocument = new JSONObject();
		JSONObject jsonOTSNQueryContext = new JSONObject();
		JSONObject jsonOTSNQuery = new JSONObject();
		JSONObject jsonOTSNWidget = new JSONObject();
		JSONObject jsonOTSNPagination = new JSONObject();
		JSONArray jsonOTSNPaginationPages = new JSONArray();
		JSONObject jsonOTSNPaginationPage = new JSONObject();
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
			jsonOTSNPaginationPage = new JSONObject();
			jsonOTSNPaginationPage.put("class", "first");
			jsonOTSNPaginationPage.put("href", this.addOrReplaceParameter(uriInfo, "p", Integer.toString(1)));
			jsonOTSNPaginationPage.put("page", 1);
			jsonOTSNPaginationPage.put("text", "Primeira");
			jsonOTSNPaginationPages.put(jsonOTSNPaginationPage);

			if (turSEResults.getCurrentPage() <= turSEResults.getPageCount()) {
				jsonOTSNPaginationPage = new JSONObject();
				jsonOTSNPaginationPage.put("class", "previous");
				jsonOTSNPaginationPage.put("href",
						this.addOrReplaceParameter(uriInfo, "p", Integer.toString(turSEResults.getCurrentPage() - 1)));
				jsonOTSNPaginationPage.put("page", turSEResults.getCurrentPage() - 1);
				jsonOTSNPaginationPage.put("text", "Anterior");
				jsonOTSNPaginationPages.put(jsonOTSNPaginationPage);
			}

		}

		for (int page = firstPagination; page <= lastPagination; page++) {

			if (page == turSEResults.getCurrentPage()) {
				jsonOTSNPaginationPage = new JSONObject();
				jsonOTSNPaginationPage.put("class", "current");
				jsonOTSNPaginationPage.put("text", Integer.toString(page));
				jsonOTSNPaginationPages.put(jsonOTSNPaginationPage);
			} else {
				jsonOTSNPaginationPage = new JSONObject();
				jsonOTSNPaginationPage.put("href", this.addOrReplaceParameter(uriInfo, "p", Integer.toString(page)));
				jsonOTSNPaginationPage.put("page", page);
				jsonOTSNPaginationPage.put("text", Integer.toString(page));
				jsonOTSNPaginationPages.put(jsonOTSNPaginationPage);

			}
		}
		if (turSEResults.getCurrentPage() != turSEResults.getPageCount() && turSEResults.getPageCount() > 1) {
			if (turSEResults.getCurrentPage() <= turSEResults.getPageCount()) {
				jsonOTSNPaginationPage = new JSONObject();
				jsonOTSNPaginationPage.put("class", "next");
				jsonOTSNPaginationPage.put("href",
						this.addOrReplaceParameter(uriInfo, "p", Integer.toString(turSEResults.getCurrentPage() + 1)));
				jsonOTSNPaginationPage.put("page", turSEResults.getCurrentPage() + 1);
				jsonOTSNPaginationPage.put("text", "Próxima");
				jsonOTSNPaginationPages.put(jsonOTSNPaginationPage);
			}

			jsonOTSNPaginationPage = new JSONObject();
			jsonOTSNPaginationPage.put("class", "last");
			jsonOTSNPaginationPage.put("href",
					this.addOrReplaceParameter(uriInfo, "p", Integer.toString(turSEResults.getPageCount())));
			jsonOTSNPaginationPage.put("page", turSEResults.getPageCount());
			jsonOTSNPaginationPage.put("text", "Última");
			jsonOTSNPaginationPages.put(jsonOTSNPaginationPage);
		}

		// END Pagination

		jsonOTSNPagination.put("otsn:page", jsonOTSNPaginationPages);
		jsonRDFResource.put("rdf:resource", "http://semantic.opentext.com/otsn/result-set");
		jsonOTSNDocument.put("otsn:document", otsnResults);

		jsonOTSNQuery.put("rdf:Description",
				(new JSONObject()).put("rdf:resource", "http://semantic.opentext.com/otsn/query"));
		jsonOTSNQuery.put("otsn:query-string", turSEResults.getQueryString());
		jsonOTSNQuery.put("otsn:sort", turSEResults.getSort());

		// BEGIN Facet
		JSONObject jsonOTSNFacetWidget = new JSONObject();
		for (TurSEFacetResult facet : turSEResults.getFacetResults()) {

			if (facetMap.containsKey(facet.getFacet()) && !hiddenFilterQuery.contains(facet.getFacet())
					&& facet.getTurSEFacetResultAttr().size() > 0) {
				TurSEFacetMap turSEFacetdMap = facetMap.get(facet.getFacet());

				JSONObject jsonOTSNFacetWidgetEntityLabel = new JSONObject();
				JSONArray jsonOTSNFacetWidgetEntityItems = new JSONArray();
				JSONObject jsonOTSNFacetWidgetEntity = new JSONObject();

				for (Object facetItemObject : facet.getTurSEFacetResultAttr().values().toArray()) {

					JSONObject jsonOTSNFacetWidgetEntityItem = new JSONObject();

					TurSEFacetResultAttr facetItem = (TurSEFacetResultAttr) facetItemObject;
					jsonOTSNFacetWidgetEntityItem.put("facet-link",
							this.addFilterQuery(uriInfo, facet.getFacet() + ":" + facetItem.getAttribute()));
					jsonOTSNFacetWidgetEntityItem.put("label", facetItem.getAttribute());
					jsonOTSNFacetWidgetEntityItem.put("facet-count", Integer.toString(facetItem.getCount()));
					jsonOTSNFacetWidgetEntityItems.put(jsonOTSNFacetWidgetEntityItem);
				}

				jsonOTSNFacetWidgetEntityLabel.put("xml:lang", "en");
				jsonOTSNFacetWidgetEntityLabel.put("text", turSEFacetdMap.getAlias());
				jsonOTSNFacetWidgetEntity.put("rdf:Description",
						(new JSONObject()).put("rdf:resource", turSEFacetdMap.getRdf()));
				jsonOTSNFacetWidgetEntity.put("rdfs:label", jsonOTSNFacetWidgetEntityLabel);
				jsonOTSNFacetWidgetEntity.put("otsn-facet",
						(new JSONObject()).put("facet", jsonOTSNFacetWidgetEntityItems));

				jsonOTSNFacetWidget.put(turSEFacetdMap.getInternal(), jsonOTSNFacetWidgetEntity);

			}
		}

		// BEGIN Facet Remove
		if (fq.size() > 0) {
			JSONObject jsonOTSNFacetToRemoveWidgetEntityLabel = new JSONObject();
			JSONArray jsonOTSNFacetToRemoveWidgetEntityItems = new JSONArray();
			JSONObject jsonOTSNFacetToRemoveWidgetEntity = new JSONObject();

			for (String facetToRemove : fq) {
				String[] facetToRemoveParts = facetToRemove.split(":");
				if (facetToRemoveParts.length == 2) {
					String facetToRemoveValue = facetToRemoveParts[1].replaceAll("\"", "");

					JSONObject jsonOTSNFacetToRemoveWidgetEntityItem = new JSONObject();
					jsonOTSNFacetToRemoveWidgetEntityItem.put("facet-link",
							this.removeFilterQuery(uriInfo, facetToRemove));
					jsonOTSNFacetToRemoveWidgetEntityItem.put("label", facetToRemoveValue);
					jsonOTSNFacetToRemoveWidgetEntityItems.put(jsonOTSNFacetToRemoveWidgetEntityItem);
				}
			}

			jsonOTSNFacetToRemoveWidgetEntityLabel.put("xml:lang", "en");
			jsonOTSNFacetToRemoveWidgetEntityLabel.put("text", "Facets To Remove");
			jsonOTSNFacetToRemoveWidgetEntity.put("rdf:Description",
					(new JSONObject()).put("rdf:resource", "http://semantic.opentext.com/otsn/facetstoremove"));
			jsonOTSNFacetToRemoveWidgetEntity.put("rdfs:label", jsonOTSNFacetToRemoveWidgetEntityLabel);
			jsonOTSNFacetToRemoveWidgetEntity.put("otsn-facet",
					(new JSONObject()).put("facet", jsonOTSNFacetToRemoveWidgetEntityItems));
			jsonOTSNFacetWidget.put("otsn:facet-to-remove", jsonOTSNFacetToRemoveWidgetEntity);
		}

		// END Facet Remove

		jsonOTSNWidget.put("otsn:facet-widget", jsonOTSNFacetWidget);
		// END Facet

		// BEGIN Similar
		JSONArray jsonOTSNSimilarWidgetItems = new JSONArray();
		for (TurSESimilarResult similar : turSEResults.getSimilarResults()) {
			JSONObject jsonOTSNSimilarWidgetItem = new JSONObject();
			for (Object similarItemObject : similar.getTurSESimilarResultAttr().values().toArray()) {
				TurSESimilarResultAttr similarItem = (TurSESimilarResultAttr) similarItemObject;
				jsonOTSNSimilarWidgetItem.put(similarItem.getAttribute(), similarItem.getValue());
			}
			jsonOTSNSimilarWidgetItems.put(jsonOTSNSimilarWidgetItem);
		}

		jsonOTSNWidget.put("otsn:similar-widget", jsonOTSNSimilarWidgetItems);

		// END Similar
		jsonOTSNQueryContext.put("otsn:pageEnd", 7);
		jsonOTSNQueryContext.put("otsn:pageStart", turSEResults.getStart());
		jsonOTSNQueryContext.put("otsn:pageCount", turSEResults.getPageCount());
		jsonOTSNQueryContext.put("otsn:page", turSEResults.getCurrentPage());
		jsonOTSNQueryContext.put("otsn:count", turSEResults.getNumFound());
		jsonOTSNQueryContext.put("otsn:limit", turSEResults.getLimit());
		jsonOTSNQueryContext.put("otsn:offset", 0);
		jsonOTSNQueryContext.put("otsn:response-time", turSEResults.getElapsedTime());
		jsonOTSNQueryContext.put("otsn:query", jsonOTSNQuery);
		jsonOTSNQueryContext.put("otsn:index", "SebraeNA");
		jsonOTSNQueryContext.put("rdf:Description",
				(new JSONObject()).put("rdf:resource", "http://semantic.opentext.com/otsn/query-context"));

		jsonRDFDescription.put("otsn:pagination", jsonOTSNPagination);
		jsonRDFDescription.put("otsn:widget", jsonOTSNWidget);
		jsonRDFDescription.put("otsn:results", jsonOTSNDocument);
		jsonRDFDescription.put("otsn:query-context", jsonOTSNQueryContext);
		jsonRDFDescription.put("rdf:type", jsonRDFResource);

		jsonSearch.put("rdf:Description", jsonRDFDescription);

		return Response.status(200).entity(jsonSearch.toString()).build();
	}
}
