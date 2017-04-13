package com.viglet.turing.api.otsn.search;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
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

import com.viglet.turing.solr.VigSolr;
import com.viglet.turing.se.facet.VigSEFacetMap;
import com.viglet.turing.se.facet.VigSEFacetMaps;
import com.viglet.turing.se.facet.VigSEFacetResult;
import com.viglet.turing.se.facet.VigSEFacetResultAttr;
import com.viglet.turing.se.field.VigSEFieldMap;
import com.viglet.turing.se.field.VigSEFieldMaps;
import com.viglet.turing.se.field.VigSEFieldType;
import com.viglet.turing.se.result.VigSEResult;
import com.viglet.turing.se.result.VigSEResultAttr;
import com.viglet.turing.se.result.VigSEResults;
import com.viglet.turing.se.similar.VigSESimilarResult;
import com.viglet.turing.se.similar.VigSESimilarResultAttr;

@Path("/otsn/search")
public class OTSNSearchAPI {

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

		Map<String, VigSEFieldMap> fieldMap = new VigSEFieldMaps().getFieldMaps();
		Map<String, VigSEFacetMap> facetMap = new VigSEFacetMaps().getFacetMaps();

		VigSEResults vigSEResults = null;
		JSONArray otsnResults = new JSONArray();

		VigSolr vigSolr = new VigSolr();
		try {
			vigSEResults = vigSolr.retrieveSolr(q, filterQueryModified, currentPage);
			List<VigSEResult> seResults = vigSEResults.getResults();
			System.out.println("getResults size:" + vigSEResults.getResults().size());
			for (VigSEResult result : seResults) {
				JSONObject otsnResult = new JSONObject();
				Map<String, VigSEResultAttr> vigSEResultAttr = result.getVigSEResultAttr();
				Set<String> attribs = vigSEResultAttr.keySet();

				otsnResult.put("otsn:elevate", false);

				JSONArray otsnMetadata = new JSONArray();
				for (Object facetObject : facetMap.keySet().toArray()) {
					String facet = (String) facetObject;
					if (vigSEResultAttr.containsKey(facet)) {
						JSONObject otsnMetadataItem = new JSONObject();
						if (vigSEResultAttr.get(facet).getAttrJSON().get(facet) instanceof ArrayList) {
							for (Object facetValueObject : (ArrayList) vigSEResultAttr.get(facet).getAttrJSON()
									.get(facet)) {
								String facetValue = (String) facetValueObject;
								otsnMetadataItem.put("href", this.addFilterQuery(uriInfo, facet + ":" + facetValue));
								otsnMetadataItem.put("text", facetValue);
							}
						} else {
							otsnMetadataItem.put("href", this.addFilterQuery(uriInfo,
									facet + ":" + vigSEResultAttr.get(facet).getAttrJSON().get(facet)));
							otsnMetadataItem.put("text", vigSEResultAttr.get(facet).getAttrJSON().get(facet));
						}
						otsnMetadata.put(otsnMetadataItem);
					}
				}

				otsnResult.put("dc:metadata", otsnMetadata);
				if (vigSEResultAttr.containsKey("url")) {
					otsnResult.put("dc:source", new JSONObject().put("rdf:resource",
							vigSEResultAttr.get("url").getAttrJSON().getString("url")));
				}
				for (String attribute : attribs) {
					// System.out.println("attribs: " + attribute);
					if (!attribute.startsWith("turing_entity")) {
						if (fieldMap.containsKey(attribute)) {
							VigSEFieldMap vigSEFieldMap = fieldMap.get(attribute);
							otsnResult.put(vigSEFieldMap.getAlias(),
									vigSEResultAttr.get(attribute).getAttrJSON().get(attribute));
						} else {
							otsnResult.put(attribute, vigSEResultAttr.get(attribute).getAttrJSON().get(attribute));
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
		int lastPagination = vigSEResults.getPageCount();

		if (vigSEResults.getCurrentPage() - 3 > 0) {
			firstPagination = vigSEResults.getCurrentPage() - 3;
		} else if (vigSEResults.getCurrentPage() - 3 <= 0) {
			firstPagination = 1;
		}
		if (vigSEResults.getCurrentPage() + 3 <= vigSEResults.getPageCount()) {
			lastPagination = vigSEResults.getCurrentPage() + 3;
		} else if (vigSEResults.getCurrentPage() + 3 > vigSEResults.getPageCount()) {
			lastPagination = vigSEResults.getPageCount();
		}

		if (vigSEResults.getCurrentPage() > vigSEResults.getPageCount()) {
			lastPagination = vigSEResults.getPageCount();
			if (vigSEResults.getPageCount() - 3 > 0) {
				firstPagination = vigSEResults.getPageCount() - 3;
			} else if (vigSEResults.getPageCount() - 3 <= 0) {
				firstPagination = 1;
			}
		}

		if (vigSEResults.getCurrentPage() > 1) {
			jsonOTSNPaginationPage = new JSONObject();
			jsonOTSNPaginationPage.put("class", "first");
			jsonOTSNPaginationPage.put("href", this.addOrReplaceParameter(uriInfo, "p", Integer.toString(1)));
			jsonOTSNPaginationPage.put("text", "Primeira");
			jsonOTSNPaginationPages.put(jsonOTSNPaginationPage);

			if (vigSEResults.getCurrentPage() <= vigSEResults.getPageCount()) {
				jsonOTSNPaginationPage = new JSONObject();
				jsonOTSNPaginationPage.put("class", "previous");
				jsonOTSNPaginationPage.put("href",
						this.addOrReplaceParameter(uriInfo, "p", Integer.toString(vigSEResults.getCurrentPage() - 1)));
				jsonOTSNPaginationPage.put("text", "Anterior");
				jsonOTSNPaginationPages.put(jsonOTSNPaginationPage);
			}

		}

		for (int page = firstPagination; page <= lastPagination; page++) {

			if (page == vigSEResults.getCurrentPage()) {
				jsonOTSNPaginationPage = new JSONObject();
				jsonOTSNPaginationPage.put("class", "current");
				jsonOTSNPaginationPage.put("text", Integer.toString(page));
				jsonOTSNPaginationPages.put(jsonOTSNPaginationPage);
			} else {
				jsonOTSNPaginationPage = new JSONObject();
				jsonOTSNPaginationPage.put("href", this.addOrReplaceParameter(uriInfo, "p", Integer.toString(page)));
				jsonOTSNPaginationPage.put("text", Integer.toString(page));
				jsonOTSNPaginationPages.put(jsonOTSNPaginationPage);

			}
		}
		if (vigSEResults.getCurrentPage() != vigSEResults.getPageCount() && vigSEResults.getPageCount() > 1) {
			if (vigSEResults.getCurrentPage() <= vigSEResults.getPageCount()) {
				jsonOTSNPaginationPage = new JSONObject();
				jsonOTSNPaginationPage.put("class", "next");
				jsonOTSNPaginationPage.put("href",
						this.addOrReplaceParameter(uriInfo, "p", Integer.toString(vigSEResults.getCurrentPage() + 1)));
				jsonOTSNPaginationPage.put("text", "Próxima");
				jsonOTSNPaginationPages.put(jsonOTSNPaginationPage);
			}

			jsonOTSNPaginationPage = new JSONObject();
			jsonOTSNPaginationPage.put("class", "last");
			jsonOTSNPaginationPage.put("href",
					this.addOrReplaceParameter(uriInfo, "p", Integer.toString(vigSEResults.getPageCount())));
			jsonOTSNPaginationPage.put("text", "Última");
			jsonOTSNPaginationPages.put(jsonOTSNPaginationPage);
		}

		// END Pagination

		jsonOTSNPagination.put("otsn:page", jsonOTSNPaginationPages);
		jsonRDFResource.put("rdf:resource", "http://semantic.opentext.com/otsn/result-set");
		jsonOTSNDocument.put("otsn:document", otsnResults);

		jsonOTSNQuery.put("rdf:Description",
				(new JSONObject()).put("rdf:resource", "http://semantic.opentext.com/otsn/query"));
		jsonOTSNQuery.put("otsn:query-string", vigSEResults.getQueryString());
		jsonOTSNQuery.put("otsn:sort", vigSEResults.getSort());

		// BEGIN Facet
		JSONObject jsonOTSNFacetWidget = new JSONObject();
		for (VigSEFacetResult facet : vigSEResults.getFacetResults()) {

			if (facetMap.containsKey(facet.getFacet()) && !hiddenFilterQuery.contains(facet.getFacet())
					&& facet.getVigSEFacetResultAttr().size() > 0) {
				VigSEFacetMap vigSEFacetdMap = facetMap.get(facet.getFacet());

				JSONObject jsonOTSNFacetWidgetEntityLabel = new JSONObject();
				JSONArray jsonOTSNFacetWidgetEntityItems = new JSONArray();
				JSONObject jsonOTSNFacetWidgetEntity = new JSONObject();

				for (Object facetItemObject : facet.getVigSEFacetResultAttr().values().toArray()) {

					JSONObject jsonOTSNFacetWidgetEntityItem = new JSONObject();

					VigSEFacetResultAttr facetItem = (VigSEFacetResultAttr) facetItemObject;
					jsonOTSNFacetWidgetEntityItem.put("facet-link",
							this.addFilterQuery(uriInfo, facet.getFacet() + ":" + facetItem.getAttribute()));
					jsonOTSNFacetWidgetEntityItem.put("label", facetItem.getAttribute());
					jsonOTSNFacetWidgetEntityItem.put("facet-count", Integer.toString(facetItem.getCount()));
					jsonOTSNFacetWidgetEntityItems.put(jsonOTSNFacetWidgetEntityItem);
				}

				jsonOTSNFacetWidgetEntityLabel.put("xml:lang", "en");
				jsonOTSNFacetWidgetEntityLabel.put("text", vigSEFacetdMap.getAlias());
				jsonOTSNFacetWidgetEntity.put("rdf:Description",
						(new JSONObject()).put("rdf:resource", vigSEFacetdMap.getRdf()));
				jsonOTSNFacetWidgetEntity.put("rdfs:label", jsonOTSNFacetWidgetEntityLabel);
				jsonOTSNFacetWidgetEntity.put("otsn-facet",
						(new JSONObject()).put("facet", jsonOTSNFacetWidgetEntityItems));

				jsonOTSNFacetWidget.put(vigSEFacetdMap.getInternal(), jsonOTSNFacetWidgetEntity);

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
		for (VigSESimilarResult similar : vigSEResults.getSimilarResults()) {
			JSONObject jsonOTSNSimilarWidgetItem = new JSONObject();
			for (Object similarItemObject : similar.getVigSESimilarResultAttr().values().toArray()) {
				VigSESimilarResultAttr similarItem = (VigSESimilarResultAttr) similarItemObject;
				jsonOTSNSimilarWidgetItem.put(similarItem.getAttribute(), similarItem.getValue());
			}
			jsonOTSNSimilarWidgetItems.put(jsonOTSNSimilarWidgetItem);
		}

		jsonOTSNWidget.put("otsn:similar-widget", jsonOTSNSimilarWidgetItems);

		// END Similar
		jsonOTSNQueryContext.put("otsn:pageEnd", 7);
		jsonOTSNQueryContext.put("otsn:pageStart", vigSEResults.getStart());
		jsonOTSNQueryContext.put("otsn:pageCount", vigSEResults.getPageCount());
		jsonOTSNQueryContext.put("otsn:page", vigSEResults.getCurrentPage());
		jsonOTSNQueryContext.put("otsn:count", vigSEResults.getNumFound());
		jsonOTSNQueryContext.put("otsn:limit", vigSEResults.getLimit());
		jsonOTSNQueryContext.put("otsn:offset", 0);
		jsonOTSNQueryContext.put("otsn:response-time", vigSEResults.getElapsedTime());
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
