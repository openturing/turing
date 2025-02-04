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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONException;

import com.fasterxml.jackson.core.type.TypeReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.client.ssl.TLSSocketConnectionFactory;
import com.viglet.turing.api.sn.bean.TurSNSearchLatestRequestBean;
import com.viglet.turing.api.sn.bean.TurSNSitePostParamsBean;
import com.viglet.turing.api.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.api.sn.bean.TurSNSiteSearchDocumentBean;
import com.viglet.turing.api.sn.bean.TurSNSiteSearchQueryContext;
import com.viglet.turing.api.sn.bean.TurSNSiteSpotlightDocumentBean;
import com.viglet.turing.api.sn.search.TurSNParamType;
import com.viglet.turing.client.sn.TurSNQuery.ORDER;
import com.viglet.turing.client.sn.autocomplete.TurSNAutoCompleteQuery;
import com.viglet.turing.client.sn.credentials.TurUsernamePasswordCredentials;
import com.viglet.turing.client.sn.didyoumean.TurSNDidYouMean;
import com.viglet.turing.client.sn.facet.TurSNFacetFieldList;
import com.viglet.turing.client.sn.pagination.TurSNPagination;
import com.viglet.turing.client.sn.response.QueryTurSNResponse;
import com.viglet.turing.client.sn.spotlight.TurSNSpotlightDocument;
import com.viglet.turing.client.sn.utils.TurSNClientUtils;

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

	private static final String UTF_8 = "UTF-8";

	private static final String ACCEPT_HEADER = "Accept";

	private static final String CONTENT_TYPE_HEADER = "Content-Type";

	private static final String ACCEPT_ENCODING_HEADER = "Accept-Encoding";
	
	private static final String APPLICATION_JSON = "application/json";

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
			URL turingURL = new URL(turSNServer.concat(LATEST_SEARCHES_CONTEXT));
			TurSNClientUtils.addURLParameter(turingURL, TurSNParamType.LOCALE, getLocale());
			TurSNClientUtils.addURLParameter(turingURL, TurSNParamType.ROWS, Integer.toString(rows));

			if (this.getCredentials() != null) {
				URL url = new URL(null, turingURL.toString(), new sun.net.www.protocol.https.Handler());
				HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();

				httpsURLConnection.setSSLSocketFactory(new TLSSocketConnectionFactory());
				httpsURLConnection.setRequestProperty(ACCEPT_HEADER, APPLICATION_JSON);
				httpsURLConnection.setRequestProperty(CONTENT_TYPE_HEADER, APPLICATION_JSON);
				httpsURLConnection.setRequestProperty(ACCEPT_ENCODING_HEADER, UTF_8);

				httpsURLConnection.setRequestMethod("POST");
				httpsURLConnection.setDoOutput(true);

				TurSNClientUtils.basicAuth(httpsURLConnection, this.getCredentials());
				
				if (this.getTurSNSitePostParams() != null && this.getTurSNSitePostParams().getUserId() != null) {
					TurSNSearchLatestRequestBean turSNSearchLatestRequestBean = new TurSNSearchLatestRequestBean();
					turSNSearchLatestRequestBean.setUserId(this.getTurSNSitePostParams().getUserId());
					String jsonResult = new ObjectMapper().writeValueAsString(turSNSearchLatestRequestBean);

					OutputStream os = httpsURLConnection.getOutputStream();
					byte[] input = jsonResult.getBytes(UTF_8);
					os.write(input, 0, input.length);
				}

				

				logger.fine(String.format("Viglet Turing Request: %s", turingURL.toString()));
				return new ObjectMapper().readValue(openConnectionAndRequest(httpsURLConnection),
						new TypeReference<List<String>>() {
						});
			}
		} catch (MalformedURLException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		} catch (IOException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return Collections.emptyList();

	}

	public List<TurSNLocale> getLocales() {
		return executeLocalesRequest(prepareLocalesRequest());

	}

	public List<String> autoComplete(TurSNAutoCompleteQuery autoCompleteQuery) {
		return executeAutoCompleteRequest(prepareAutoCompleteRequest(autoCompleteQuery));
	}

	private List<TurSNLocale> executeLocalesRequest(HttpsURLConnection httpsURLConnection) {

		try {

			int responseCode = httpsURLConnection.getResponseCode();
			String result = this.getTurResponseBody(httpsURLConnection, responseCode);
			return new ObjectMapper().readValue(result, new TypeReference<List<TurSNLocale>>() {
			});
		} catch (IOException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return null;
	}

	private List<String> executeAutoCompleteRequest(HttpsURLConnection httpsURLConnection) {
		String result;
		try {
			int responseCode = httpsURLConnection.getResponseCode();
			result = this.getTurResponseBody(httpsURLConnection, responseCode);
			return new ObjectMapper().readValue(result, new TypeReference<List<String>>() {
			});
		} catch (IOException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return null;

	}

	private HttpsURLConnection prepareLocalesRequest() {
		try {
			return prepareGetRequest(new URL(turSNServer.concat(LOCALES_CONTEXT)));
		} catch (MalformedURLException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return null;
	}

	private HttpsURLConnection prepareAutoCompleteRequest(TurSNAutoCompleteQuery autoCompleteQuery) {

		try {
			URL turingURL = new URL(turSNServer.concat(AUTO_COMPLETE_CONTEXT));
			TurSNClientUtils.addURLParameter(turingURL, TurSNParamType.LOCALE, getLocale());
			TurSNClientUtils.addURLParameter(turingURL, TurSNParamType.QUERY, autoCompleteQuery.getQuery());
			TurSNClientUtils.addURLParameter(turingURL, TurSNParamType.ROWS, Integer.toString(autoCompleteQuery.getRows()));
			return prepareGetRequest(turingURL);
		} catch (MalformedURLException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return null;
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

	private String openConnectionAndRequest(HttpsURLConnection httpsURLConnection) {
			return executeQueryRequest(httpsURLConnection);
	}

	private String executeQueryRequest(HttpsURLConnection httpsURLConnection) {
		try {
			int responseCode = httpsURLConnection.getResponseCode();
			return this.getTurResponseBody(httpsURLConnection, responseCode);
		} catch (IOException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return null;
	}

	private HttpsURLConnection prepareQueryRequest() {

		try {
			URL turingURL = new URL(turSNServer.concat(SEARCH_CONTEXT));
			TurSNClientUtils.addURLParameter(turingURL, TurSNParamType.LOCALE, getLocale());
			TurSNClientUtils.addURLParameter(turingURL, TurSNParamType.QUERY, this.turSNQuery.getQuery());

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

		} catch (MalformedURLException e) {
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
		List<TurSNSpotlightDocument> turSNSpotlightDocuments = new ArrayList<TurSNSpotlightDocument>();
		for (TurSNSiteSpotlightDocumentBean turSNSiteSpotlightDocumentBean : turSNSiteSpotlightDocumentBeans) {
			turSNSpotlightDocuments.add(new TurSNSpotlightDocument(turSNSiteSpotlightDocumentBean));
		}
		return turSNSpotlightDocuments;
	}

	private TurSNFacetFieldList setFacetFieldsResponse(TurSNSiteSearchBean turSNSiteSearchBean) {
		TurSNFacetFieldList facetFields = new TurSNFacetFieldList(turSNSiteSearchBean.getWidget().getFacet(),
				turSNSiteSearchBean.getWidget().getFacetToRemove());
		return facetFields;
	}

	private TurSNDocumentList setResultsResponse(TurSNSiteSearchBean turSNSiteSearchBean) {
		List<TurSNDocument> turSNDocuments = new ArrayList<TurSNDocument>();
		for (TurSNSiteSearchDocumentBean turSNSiteSearchDocumentBean : turSNSiteSearchBean.getResults().getDocument()) {
			TurSNDocument turSNDocument = new TurSNDocument();
			turSNDocument.setContent(turSNSiteSearchDocumentBean);
			turSNDocuments.add(turSNDocument);
		}

		TurSNSiteSearchQueryContext turSNSiteSearchQueryContext = turSNSiteSearchBean.getQueryContext();
		TurSNDocumentList turSNDocumentList = new TurSNDocumentList();
		turSNDocumentList.setTurSNDocuments(turSNDocuments);
		turSNDocumentList.setQueryContext(turSNSiteSearchQueryContext);
		return turSNDocumentList;
	}

	private HttpsURLConnection preparePostRequest(URL turingURL) {
		HttpsURLConnection httpsURLConnection = null;
		try {
			URL url = new URL(null, turingURL.toString(), new sun.net.www.protocol.https.Handler());
			httpsURLConnection = (HttpsURLConnection) url.openConnection();

			httpsURLConnection.setSSLSocketFactory(new TLSSocketConnectionFactory());
			httpsURLConnection.setRequestProperty(ACCEPT_HEADER, APPLICATION_JSON);
			httpsURLConnection.setRequestProperty(CONTENT_TYPE_HEADER, APPLICATION_JSON);
			httpsURLConnection.setRequestProperty(ACCEPT_ENCODING_HEADER, UTF_8);

			httpsURLConnection.setRequestMethod("POST");
			httpsURLConnection.setDoOutput(true);

			TurSNClientUtils.basicAuth(httpsURLConnection, this.getCredentials());
			
			String jsonResult = new ObjectMapper().writeValueAsString(this.getTurSNSitePostParams());

			OutputStream os = httpsURLConnection.getOutputStream();
			byte[] input = jsonResult.getBytes(UTF_8);
			os.write(input, 0, input.length);

			

			logger.fine(String.format("Viglet Turing Request: %s", turingURL.toString()));
		} catch (MalformedURLException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		} catch (IOException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		} catch (JSONException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}

		return httpsURLConnection;

	}

	private HttpsURLConnection prepareGetRequest(URL turingURL) {
		HttpsURLConnection httpsURLConnection = null;
		try {
			URL url = new URL(null, turingURL.toString(), new sun.net.www.protocol.https.Handler());
			httpsURLConnection = (HttpsURLConnection) url.openConnection();

			httpsURLConnection.setSSLSocketFactory(new TLSSocketConnectionFactory());
			httpsURLConnection.setRequestProperty(ACCEPT_HEADER, APPLICATION_JSON);
			httpsURLConnection.setRequestProperty(CONTENT_TYPE_HEADER, APPLICATION_JSON);
			httpsURLConnection.setRequestProperty(ACCEPT_ENCODING_HEADER, UTF_8);

			httpsURLConnection.setRequestMethod("GET");
			httpsURLConnection.setDoOutput(true);

		} catch (MalformedURLException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		} catch (IOException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		} catch (JSONException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}

		return httpsURLConnection;
	}

	private void pageNumberRequest(URL turingURL) {
		if (this.turSNQuery.getPageNumber() > 0) {
			TurSNClientUtils.addURLParameter(turingURL, TurSNParamType.PAGE, 
			String.format(Integer.toString(this.turSNQuery.getPageNumber())));
		} else {
			TurSNClientUtils.addURLParameter(turingURL,TurSNParamType.PAGE, PAGE_DEFAULT);
		}
	}

	private void betweenDatesRequest(URL turingURL) {
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
				TurSNClientUtils.addURLParameter(turingURL, TurSNParamType.FILTER_QUERIES, 
						String.format("%s:[%s TO %s]", fieldDate, startDate, endDate));
			}
		}
	}

	private void sortRequest(URL turingURL) {
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
					TurSNClientUtils.addURLParameter(turingURL, TurSNParamType.SORT, orderMod);
				} else {
					TurSNClientUtils.addURLParameter(turingURL, TurSNParamType.SORT,
							String.format("%s %s", turSortField.getField(), turSortField.getSort().name()));
				}
			}
		}
	}

	private void fieldQueryRequest(URL turingURL) {
		if (this.turSNQuery.getFieldQueries() != null) {
			for (String fieldQuery : this.turSNQuery.getFieldQueries()) {
				TurSNClientUtils.addURLParameter(turingURL, TurSNParamType.FILTER_QUERIES, fieldQuery);
			}
		}
	}

	private void rowsRequest(URL turingURL) {
		if (this.turSNQuery.getRows() > 0) {
			TurSNClientUtils.addURLParameter(turingURL, TurSNParamType.ROWS, Integer.toString(this.turSNQuery.getRows()));
		}
	}

	private String getTurResponseBody(HttpsURLConnection httpsURLConnection, int result) throws IOException {
		StringBuffer responseBody = new StringBuffer();
		if (result == 200) {
			BufferedReader br = new BufferedReader(new InputStreamReader(httpsURLConnection.getInputStream()));
			String strCurrentLine;
			while ((strCurrentLine = br.readLine()) != null) {
				responseBody.append(strCurrentLine);
			}
		} else {
			BufferedReader br = new BufferedReader(new InputStreamReader(httpsURLConnection.getErrorStream()));
			String strCurrentLine;
			while ((strCurrentLine = br.readLine()) != null) {
				responseBody.append(strCurrentLine);
			}
		}
		httpsURLConnection.disconnect();
		return responseBody.toString();
	}
}
