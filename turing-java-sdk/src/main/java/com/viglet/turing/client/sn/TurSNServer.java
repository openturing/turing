/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.viglet.turing.client.sn;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.viglet.turing.api.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.api.sn.bean.TurSNSiteSearchQueryContext;
import com.viglet.turing.api.sn.bean.TurSNSiteSpotlightDocumentBean;
import com.viglet.turing.client.sn.TurSNQuery.ORDER;
import com.viglet.turing.client.sn.autocomplete.TurSNAutoCompleteQuery;
import com.viglet.turing.client.sn.credentials.TurUsernamePasswordCredentials;
import com.viglet.turing.client.sn.didyoumean.TurSNDidYouMean;
import com.viglet.turing.client.sn.facet.TurSNFacetFieldList;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.client.sn.job.TurSNJobUtils;
import com.viglet.turing.client.sn.pagination.TurSNPagination;
import com.viglet.turing.client.sn.response.QueryTurSNResponse;
import com.viglet.turing.client.sn.spotlight.TurSNSpotlightDocument;

import java.util.logging.*;

/**
 * Connect to Turing AI Server.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.4
 */
public class TurSNServer {

	private static Logger logger = Logger.getLogger(TurSNServer.class.getName());

	private static final String SITE_NAME_DEFAULT = "Sample";

	private static final String LOCALE_DEFAULT = "en_US";

	private static final String PROVIDER_NAME_DEFAULT = "turing-java-sdk";
	private String turSNServer;

	private TurSNQuery turSNQuery;

	private URL serverURL;

	private String siteName;

	private String locale;

	private TurUsernamePasswordCredentials credentials;

	private String providerName;

	@Deprecated
	public TurSNServer(String turSNServer) {
		super();
		this.turSNServer = turSNServer;
		this.siteName = SITE_NAME_DEFAULT;
		this.locale = LOCALE_DEFAULT;
		this.providerName = PROVIDER_NAME_DEFAULT;
	}

	public TurSNServer(URL serverURL, String siteName) {
		super();
		this.serverURL = serverURL;
		this.siteName = siteName;
		this.locale = LOCALE_DEFAULT;
		this.turSNServer = String.format("%s/api/sn/%s", this.serverURL, this.siteName, this.locale);
		this.providerName = PROVIDER_NAME_DEFAULT;
	}

	public TurSNServer(URL serverURL, String siteName, String locale) {
		super();
		this.serverURL = serverURL;
		this.siteName = siteName;
		this.locale = locale;
		this.turSNServer = String.format("%s/api/sn/%s", this.serverURL, this.siteName);
		this.providerName = PROVIDER_NAME_DEFAULT;
	}

	public TurSNServer(URL serverURL, String siteName, String locale, TurUsernamePasswordCredentials credentials) {
		super();
		this.serverURL = serverURL;
		this.siteName = siteName;
		this.locale = locale;
		this.turSNServer = String.format("%s/api/sn/%s", this.serverURL, this.siteName);
		this.credentials = credentials;
		this.providerName = PROVIDER_NAME_DEFAULT;
	}

	public TurSNServer(URL serverURL, TurUsernamePasswordCredentials credentials) {
		super();
		this.serverURL = serverURL;
		this.siteName = SITE_NAME_DEFAULT;
		this.locale = LOCALE_DEFAULT;
		this.turSNServer = String.format("%s/api/sn/%s", this.serverURL, this.siteName);
		this.credentials = credentials;
		this.providerName = PROVIDER_NAME_DEFAULT;

	}

	public String getTuringServer() {
		return turSNServer;
	}

	public void setTuringServer(String turingServer) {
		this.turSNServer = turingServer;
	}

	public URL getServerURL() {
		return serverURL;
	}

	public void setServerURL(URL serverURL) {
		this.serverURL = serverURL;
	}

	public String getSiteName() {
		return siteName;
	}

	public void setSiteName(String siteName) {
		this.siteName = siteName;
	}

	public String getLocale() {
		return locale;
	}

	public void setLocale(String locale) {
		this.locale = locale;
	}

	public TurUsernamePasswordCredentials getCredentials() {
		return credentials;
	}

	public void setCredentials(TurUsernamePasswordCredentials credentials) {
		this.credentials = credentials;
	}

	public String getProviderName() {
		return providerName;
	}

	public void setProviderName(String providerName) {
		this.providerName = providerName;
	}

	public void importItems(TurSNJobItems turSNJobItems, boolean showOutput) {
		TurSNJobUtils.importItems(turSNJobItems, this, showOutput);
	}

	public void importItems(TurSNJobItems turSNJobItems) {
		if (credentials != null) {
			importItems(turSNJobItems, false);
		} else {
			logger.severe("No credentials to import Items");
		}
	}

	public void deleteItemsByType(String typeName) {
		if (credentials != null) {
			TurSNJobUtils.deleteItemsByType(this, typeName);
		} else {
			logger.severe(String.format("No credentials to delete items by %s type", typeName));
		}
	}

	public List<TurSNLocale> getLocales() {

		try (CloseableHttpClient client = HttpClients.createDefault()) {
			return executeLocalesRequest(prepareLocalesRequest(), client);
		} catch (IOException | URISyntaxException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return Collections.emptyList();
	}

	public List<String> autoComplete(TurSNAutoCompleteQuery autoCompleteQuery) {

		try (CloseableHttpClient client = HttpClients.createDefault()) {
			return executeAutoCompleteRequest(prepareAutoCompleteRequest(autoCompleteQuery), client);
		} catch (IOException | URISyntaxException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return Collections.emptyList();
	}

	private List<TurSNLocale> executeLocalesRequest(HttpGet httpGet, CloseableHttpClient client)
			throws IOException, ClientProtocolException, JsonParseException, JsonMappingException {
		HttpResponse response = client.execute(httpGet);
		HttpEntity entity = response.getEntity();
		String result = EntityUtils.toString(entity, StandardCharsets.UTF_8);
		return new ObjectMapper().readValue(result, new TypeReference<List<TurSNLocale>>() {
		});
	}

	private List<String> executeAutoCompleteRequest(HttpGet httpGet, CloseableHttpClient client)
			throws IOException, ClientProtocolException, JsonParseException, JsonMappingException {
		HttpResponse response = client.execute(httpGet);
		HttpEntity entity = response.getEntity();
		String result = EntityUtils.toString(entity, StandardCharsets.UTF_8);

		return new ObjectMapper().readValue(result, new TypeReference<List<String>>() {
		});
	}

	private HttpGet prepareLocalesRequest() throws URISyntaxException {
		return prepareGetRequest(new URIBuilder(turSNServer + "/search/locales"));
	}

	private HttpGet prepareAutoCompleteRequest(TurSNAutoCompleteQuery autoCompleteQuery) throws URISyntaxException {
		URIBuilder turingURL = new URIBuilder(turSNServer + "/ac").addParameter("_setlocale", getLocale())
				.addParameter("q", autoCompleteQuery.getQuery())
				.addParameter("rows", Integer.toString(autoCompleteQuery.getRows()));

		return prepareGetRequest(turingURL);
	}

	public QueryTurSNResponse query(TurSNQuery turSNQuery) {
		this.turSNQuery = turSNQuery;

		try (CloseableHttpClient client = HttpClients.createDefault()) {
			return createTuringResponse(executeQueryRequest(prepareQueryRequest(), client));
		} catch (URISyntaxException | IOException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}

		return new QueryTurSNResponse();
	}

	private TurSNSiteSearchBean executeQueryRequest(HttpGet httpGet, CloseableHttpClient client)
			throws IOException, ClientProtocolException, JsonParseException, JsonMappingException {
		HttpResponse response = client.execute(httpGet);
		HttpEntity entity = response.getEntity();
		String result = EntityUtils.toString(entity, StandardCharsets.UTF_8);
		TurSNSiteSearchBean turSNSiteSearchBean = new ObjectMapper().readValue(result, TurSNSiteSearchBean.class);
		return turSNSiteSearchBean;
	}

	private HttpGet prepareQueryRequest() throws URISyntaxException {
		URIBuilder turingURL = new URIBuilder(turSNServer + "/search").addParameter("_setlocale", getLocale())
				.addParameter("q", this.turSNQuery.getQuery());
		rowsRequest(turingURL);
		fieldQueryRequest(turingURL);
		targetingRulesRequest(turingURL);
		sortRequest(turingURL);
		betweenDatesRequest(turingURL);
		pageNumberRequest(turingURL);

		return prepareGetRequest(turingURL);
	}

	private QueryTurSNResponse createTuringResponse(TurSNSiteSearchBean turSNSiteSearchBean) {
		QueryTurSNResponse queryTuringResponse = new QueryTurSNResponse();
		queryTuringResponse.setResults(setResultsResponse(turSNSiteSearchBean));
		queryTuringResponse.setPagination(new TurSNPagination(turSNSiteSearchBean.getPagination()));
		queryTuringResponse.setFacetFields(setFacetFieldsResponse(turSNSiteSearchBean));
		queryTuringResponse.setDidYouMean(new TurSNDidYouMean(turSNSiteSearchBean.getWidget().getSpellCheck()));
		queryTuringResponse
				.setSpotlightDocuments(setSpotlightDocumetsResponse(turSNSiteSearchBean.getWidget().getSpotlights()));
		return queryTuringResponse;
	}

	private List<TurSNSpotlightDocument> setSpotlightDocumetsResponse(
			List<TurSNSiteSpotlightDocumentBean> turSNSiteSpotlightDocumentBeans) {
		List<TurSNSpotlightDocument> turSNSpotlightDocuments = new ArrayList<>();
		turSNSiteSpotlightDocumentBeans.forEach(turSNSiteSpotlightDocumentBean -> turSNSpotlightDocuments
				.add(new TurSNSpotlightDocument(turSNSiteSpotlightDocumentBean)));
		return turSNSpotlightDocuments;
	}

	private TurSNFacetFieldList setFacetFieldsResponse(TurSNSiteSearchBean turSNSiteSearchBean) {
		TurSNFacetFieldList facetFields = new TurSNFacetFieldList(turSNSiteSearchBean.getWidget().getFacet(),
				turSNSiteSearchBean.getWidget().getFacetToRemove());
		return facetFields;
	}

	private TurSNDocumentList setResultsResponse(TurSNSiteSearchBean turSNSiteSearchBean) {
		List<TurSNDocument> turSNDocuments = new ArrayList<>();

		turSNSiteSearchBean.getResults().getDocument().forEach(turSNSiteSearchDocumentBean -> {
			TurSNDocument turSNDocument = new TurSNDocument();
			turSNDocument.setContent(turSNSiteSearchDocumentBean);
			turSNDocuments.add(turSNDocument);
		});

		TurSNSiteSearchQueryContext turSNSiteSearchQueryContext = turSNSiteSearchBean.getQueryContext();
		TurSNDocumentList turSNDocumentList = new TurSNDocumentList();
		turSNDocumentList.setTurSNDocuments(turSNDocuments);
		turSNDocumentList.setQueryContext(turSNSiteSearchQueryContext);
		return turSNDocumentList;
	}

	private HttpGet prepareGetRequest(URIBuilder turingURL) throws URISyntaxException {
		HttpGet httpGet = new HttpGet(turingURL.build());

		httpGet.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
		httpGet.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
		httpGet.setHeader(HttpHeaders.ACCEPT_ENCODING, StandardCharsets.UTF_8.name());
		logger.info(String.format("Viglet Turing Request: %s", turingURL.build().toString()));
		return httpGet;
	}

	private void pageNumberRequest(URIBuilder turingURL) {
		// Page Number
		if (this.turSNQuery.getPageNumber() > 0) {
			turingURL.addParameter("p", String.format(Integer.toString(this.turSNQuery.getPageNumber())));
		} else {
			turingURL.addParameter("p", "1");
		}
	}

	private void betweenDatesRequest(URIBuilder turingURL) {
		// Between Dates
		if (this.turSNQuery.getBetweenDates() != null) {
			TurSNClientBetweenDates turClientBetweenDates = this.turSNQuery.getBetweenDates();
			if (turClientBetweenDates.getField() != null && turClientBetweenDates.getStartDate() != null
					&& turClientBetweenDates.getEndDate() != null) {
				TimeZone tz = TimeZone.getTimeZone("UTC");
				DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
				df.setTimeZone(tz);

				String fieldDate = turClientBetweenDates.getField();
				String startDate = df.format(turClientBetweenDates.getStartDate());
				String endDate = df.format(turClientBetweenDates.getEndDate());

				turingURL.addParameter("fq[]", String.format("%s:[%s TO %s]", fieldDate, startDate, endDate));
			}
		}
	}

	private void sortRequest(URIBuilder turingURL) {
		// Sort
		if (this.turSNQuery.getSortField() != null) {
			TurSNSortField turSortField = this.turSNQuery.getSortField();

			if (turSortField.getSort() != null) {
				if (turSortField.getField() == null) {
					String orderMod = null;
					if (turSortField.getSort().name().equals(ORDER.desc.name())) {
						orderMod = "newest";
					} else if (turSortField.getSort().name().equals(ORDER.asc.name())) {
						orderMod = "oldest";
					} else {
						orderMod = "relevance";
					}
					turingURL.addParameter("sort", orderMod);
				} else {
					turingURL.addParameter("sort",
							String.format("%s %s", turSortField.getField(), turSortField.getSort().name()));
				}
			}
		}
	}

	private void targetingRulesRequest(URIBuilder turingURL) {
		// Targeting Rule
		if (this.turSNQuery.getTargetingRules() != null) {
			for (String targetingRule : this.turSNQuery.getTargetingRules()) {
				turingURL.addParameter("tr[]", targetingRule);
			}
		}
	}

	private void fieldQueryRequest(URIBuilder turingURL) {
		// Field Query
		if (this.turSNQuery.getFieldQueries() != null) {
			for (String fieldQuery : this.turSNQuery.getFieldQueries()) {
				turingURL.addParameter("fq[]", fieldQuery);
			}
		}
	}

	private void rowsRequest(URIBuilder turingURL) {
		// Rows
		if (this.turSNQuery.getRows() > 0) {
			turingURL.addParameter("rows", Integer.toString(this.turSNQuery.getRows()));
		}
	}
}
