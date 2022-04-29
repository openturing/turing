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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.viglet.turing.client.sn.utils.TurSNClientUtils;
import com.viglet.turing.commons.sn.bean.TurSNSearchLatestRequestBean;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchQueryContext;
import com.viglet.turing.commons.sn.bean.TurSNSiteSpotlightDocumentBean;
import com.viglet.turing.commons.sn.search.TurSNParamType;

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

	private static final String PAGE_DEFAULT = "1";

	private static final String PROVIDER_NAME_DEFAULT = "turing-java-sdk";

	private static final String AUTO_COMPLETE_CONTEXT = "/ac";

	private static final String LOCALES_CONTEXT = "/search/locales";

	private static final String LATEST_SEARCHES_CONTEXT = "/search/latest";

	private static final String SEARCH_CONTEXT = "/search";

	private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";

	private static final String UTC_TIMEZONE = "UTC";

	private static final String NEWEST_SORT = "newest";

	private static final String OLDEST_SORT = "oldest";

	private static final String RELEVANCE_SORT = "relevance";

	private String turSNServer;

	private TurSNQuery turSNQuery;

	private URL serverURL;

	private String siteName;

	private String locale;

	private TurSNSitePostParamsBean turSNSitePostParams;

	private TurUsernamePasswordCredentials credentials;

	private String providerName;

	@Deprecated
	public TurSNServer(String turSNServer) {
		super();
		this.turSNServer = turSNServer;
		this.siteName = SITE_NAME_DEFAULT;
		this.locale = LOCALE_DEFAULT;
		this.credentials = null;
		this.providerName = PROVIDER_NAME_DEFAULT;
		this.turSNSitePostParams = new TurSNSitePostParamsBean();
		this.turSNSitePostParams.setUserId(null);
		this.turSNSitePostParams.setPopulateMetrics(true);
	}

	public TurSNServer(URL serverURL, String siteName, String locale, TurUsernamePasswordCredentials credentials,
			String userId) {
		super();
		this.serverURL = serverURL;
		this.siteName = siteName;
		this.locale = locale;
		this.turSNServer = String.format("%s/api/sn/%s", this.serverURL, this.siteName);
		this.credentials = credentials;
		this.providerName = PROVIDER_NAME_DEFAULT;
		this.turSNSitePostParams = new TurSNSitePostParamsBean();
		this.turSNSitePostParams.setUserId(userId);
		this.turSNSitePostParams.setPopulateMetrics(true);

	}

	public TurSNServer(URL serverURL, String siteName) {
		this(serverURL, siteName, LOCALE_DEFAULT);
	}

	public TurSNServer(URL serverURL, String siteName, String locale) {
		this(serverURL, siteName, locale, null);
	}

	public TurSNServer(URL serverURL, String siteName, String locale, TurUsernamePasswordCredentials credentials) {
		this(serverURL, siteName, locale, credentials, credentials != null ? credentials.getUsername() : null);
	}

	public TurSNServer(URL serverURL, TurUsernamePasswordCredentials credentials) {
		this(serverURL, SITE_NAME_DEFAULT, LOCALE_DEFAULT, credentials,
				credentials != null ? credentials.getUsername() : null);
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

	public TurSNSitePostParamsBean getTurSNSitePostParams() {
		return turSNSitePostParams;
	}

	public void setTurSNSitePostParams(TurSNSitePostParamsBean turSNSitePostParams) {
		this.turSNSitePostParams = turSNSitePostParams;
	}

	public List<String> getLatestSearches(int rows) {
		try {
			URIBuilder turingURL = new URIBuilder(turSNServer.concat(LATEST_SEARCHES_CONTEXT))
					.addParameter(TurSNParamType.LOCALE, getLocale())
					.addParameter(TurSNParamType.ROWS, Integer.toString(rows));

			if (this.getCredentials() != null) {
				HttpPost httpPost = new HttpPost(turingURL.build());

				httpPost.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
				httpPost.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
				httpPost.setHeader(HttpHeaders.ACCEPT_ENCODING, StandardCharsets.UTF_8.name());

				if (this.getTurSNSitePostParams() != null && this.getTurSNSitePostParams().getUserId() != null) {
					TurSNSearchLatestRequestBean turSNSearchLatestRequestBean = new TurSNSearchLatestRequestBean();
					turSNSearchLatestRequestBean.setUserId(this.getTurSNSitePostParams().getUserId());
					String jsonResult = new ObjectMapper().writeValueAsString(turSNSearchLatestRequestBean);
					httpPost.setEntity(new StringEntity(jsonResult, StandardCharsets.UTF_8));
				}
				TurSNClientUtils.basicAuth(httpPost, this.getCredentials());
				try {
					return new ObjectMapper().readValue(openConnectionAndRequest(httpPost),
							new TypeReference<List<String>>() {
							});
				} catch (IOException e) {
					logger.log(Level.SEVERE, e.getMessage(), e);
				}
				logger.fine(String.format("Viglet Turing Request: %s", turingURL.build().toString()));

			}
		} catch (JsonProcessingException | URISyntaxException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return Collections.emptyList();

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
		return prepareGetRequest(new URIBuilder(turSNServer.concat(LOCALES_CONTEXT)));
	}

	private HttpGet prepareAutoCompleteRequest(TurSNAutoCompleteQuery autoCompleteQuery) throws URISyntaxException {
		URIBuilder turingURL = new URIBuilder(turSNServer.concat(AUTO_COMPLETE_CONTEXT))
				.addParameter(TurSNParamType.LOCALE, getLocale())
				.addParameter(TurSNParamType.QUERY, autoCompleteQuery.getQuery())
				.addParameter(TurSNParamType.ROWS, Integer.toString(autoCompleteQuery.getRows()));

		return prepareGetRequest(turingURL);
	}

	public QueryTurSNResponse query(TurSNQuery turSNQuery) {
		this.turSNQuery = turSNQuery;
		try {
			TurSNSiteSearchBean turSNSiteSearchBean = new ObjectMapper()
					.readValue(openConnectionAndRequest(prepareQueryRequest()), TurSNSiteSearchBean.class);
			return createTuringResponse(turSNSiteSearchBean);
		} catch (IOException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return new QueryTurSNResponse();
	}

	private String openConnectionAndRequest(HttpRequestBase httpRequestBase) {
		try (CloseableHttpClient client = HttpClients.createDefault()) {
			return executeQueryRequest(httpRequestBase, client);
		} catch (IOException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return null;
	}

	private String executeQueryRequest(HttpRequestBase httpRequestBase, CloseableHttpClient client) {
		try {
			HttpResponse response = client.execute(httpRequestBase);
			String result = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
			return result;
		} catch (IOException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return null;
	}

	private HttpRequestBase prepareQueryRequest() {

		try {
			URIBuilder turingURL = new URIBuilder(turSNServer.concat(SEARCH_CONTEXT))
					.addParameter(TurSNParamType.LOCALE, getLocale())
					.addParameter(TurSNParamType.QUERY, this.turSNQuery.getQuery());

			rowsRequest(turingURL);
			fieldQueryRequest(turingURL);
			sortRequest(turingURL);
			betweenDatesRequest(turingURL);
			pageNumberRequest(turingURL);
			if (this.getCredentials() != null) {
				this.getTurSNSitePostParams().setPopulateMetrics(this.turSNQuery.isPopulateMetrics());
				this.getTurSNSitePostParams().setTargetingRules(this.turSNQuery.getTargetingRules());
				return preparePostRequest(turingURL);
			} else {
				return prepareGetRequest(turingURL);
			}

		} catch (URISyntaxException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return null;
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

	private HttpPost preparePostRequest(URIBuilder turingURL) {
		HttpPost httpPost = null;
		try {
			httpPost = new HttpPost(turingURL.build());

			httpPost.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
			httpPost.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
			httpPost.setHeader(HttpHeaders.ACCEPT_ENCODING, StandardCharsets.UTF_8.name());

			String jsonResult = new ObjectMapper().writeValueAsString(this.getTurSNSitePostParams());
			httpPost.setEntity(new StringEntity(jsonResult, StandardCharsets.UTF_8));

			TurSNClientUtils.basicAuth(httpPost, this.getCredentials());

			logger.fine(String.format("Viglet Turing Request: %s", turingURL.build().toString()));
		} catch (JsonProcessingException | URISyntaxException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}

		return httpPost;

	}

	private HttpGet prepareGetRequest(URIBuilder turingURL) {
		HttpGet httpGet = null;
		try {
			httpGet = new HttpGet(turingURL.build());
			httpGet.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
			httpGet.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
			httpGet.setHeader(HttpHeaders.ACCEPT_ENCODING, StandardCharsets.UTF_8.name());

			logger.fine(String.format("Viglet Turing Request: %s", turingURL.build().toString()));

		} catch (URISyntaxException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}

		return httpGet;
	}

	private void pageNumberRequest(URIBuilder turingURL) {
		if (this.turSNQuery.getPageNumber() > 0) {
			turingURL.addParameter(TurSNParamType.PAGE,
					String.format(Integer.toString(this.turSNQuery.getPageNumber())));
		} else {
			turingURL.addParameter(TurSNParamType.PAGE, PAGE_DEFAULT);
		}
	}

	private void betweenDatesRequest(URIBuilder turingURL) {
		if (this.turSNQuery.getBetweenDates() != null) {
			TurSNClientBetweenDates turClientBetweenDates = this.turSNQuery.getBetweenDates();
			if (turClientBetweenDates.getField() != null && turClientBetweenDates.getStartDate() != null
					&& turClientBetweenDates.getEndDate() != null) {
				TimeZone tz = TimeZone.getTimeZone(UTC_TIMEZONE);
				DateFormat df = new SimpleDateFormat(DATE_FORMAT);
				df.setTimeZone(tz);

				String fieldDate = turClientBetweenDates.getField();
				String startDate = df.format(turClientBetweenDates.getStartDate());
				String endDate = df.format(turClientBetweenDates.getEndDate());

				turingURL.addParameter(TurSNParamType.FILTER_QUERIES,
						String.format("%s:[%s TO %s]", fieldDate, startDate, endDate));
			}
		}
	}

	private void sortRequest(URIBuilder turingURL) {
		if (this.turSNQuery.getSortField() != null) {
			TurSNSortField turSortField = this.turSNQuery.getSortField();

			if (turSortField.getSort() != null) {
				if (turSortField.getField() == null) {
					String orderMod = null;
					if (turSortField.getSort().name().equals(ORDER.desc.name())) {
						orderMod = NEWEST_SORT;
					} else if (turSortField.getSort().name().equals(ORDER.asc.name())) {
						orderMod = OLDEST_SORT;
					} else {
						orderMod = RELEVANCE_SORT;
					}
					turingURL.addParameter(TurSNParamType.SORT, orderMod);
				} else {
					turingURL.addParameter(TurSNParamType.SORT,
							String.format("%s %s", turSortField.getField(), turSortField.getSort().name()));
				}
			}
		}
	}

	private void fieldQueryRequest(URIBuilder turingURL) {
		if (this.turSNQuery.getFieldQueries() != null) {
			for (String fieldQuery : this.turSNQuery.getFieldQueries()) {
				turingURL.addParameter(TurSNParamType.FILTER_QUERIES, fieldQuery);
			}
		}
	}

	private void rowsRequest(URIBuilder turingURL) {
		if (this.turSNQuery.getRows() > 0) {
			turingURL.addParameter(TurSNParamType.ROWS, Integer.toString(this.turSNQuery.getRows()));
		}
	}
}
