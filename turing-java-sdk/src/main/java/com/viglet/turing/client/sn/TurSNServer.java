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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.auth.credentials.TurUsernamePasswordCredentials;
import com.viglet.turing.client.sn.TurSNQuery.ORDER;
import com.viglet.turing.client.sn.autocomplete.TurSNAutoCompleteQuery;
import com.viglet.turing.client.sn.didyoumean.TurSNDidYouMean;
import com.viglet.turing.client.sn.facet.TurSNFacetFieldList;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.client.sn.job.TurSNJobUtils;
import com.viglet.turing.client.sn.pagination.TurSNPagination;
import com.viglet.turing.client.sn.response.QueryTurSNResponse;
import com.viglet.turing.client.sn.spotlight.TurSNSpotlightDocument;
import com.viglet.turing.client.sn.utils.TurSNClientUtils;
import com.viglet.turing.commons.sn.bean.*;
import com.viglet.turing.commons.sn.search.TurSNParamType;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.net.URIBuilder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Connect to Turing AI Server.
 *
 * @author Alexandre Oliveira
 * @since 0.3.4
 */
@Getter
@Setter
@Slf4j
public class TurSNServer {

    private static final String SITE_NAME_DEFAULT = "Sample";

    private static final Locale LOCALE_DEFAULT = Locale.US;

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

    private String snServer;

    private TurSNQuery turSNQuery;

    private URL serverURL;

    private String siteName;

    private Locale locale;

    private TurSNSitePostParamsBean turSNSitePostParams;

    private TurUsernamePasswordCredentials credentials;


    private String apiKey;

    private String providerName;

    @Deprecated
    public TurSNServer(String snServer) {
        super();
        this.snServer = snServer;
        this.siteName = SITE_NAME_DEFAULT;
        this.locale = LOCALE_DEFAULT;
        this.credentials = null;
        this.providerName = PROVIDER_NAME_DEFAULT;
        this.turSNSitePostParams = new TurSNSitePostParamsBean();
        this.turSNSitePostParams.setUserId(null);
        this.turSNSitePostParams.setPopulateMetrics(true);
    }

    public TurSNServer(URL serverURL, String siteName, Locale locale, TurUsernamePasswordCredentials credentials,
                       String userId) {
        super();
        this.serverURL = serverURL;
        this.siteName = siteName;
        this.locale = locale;
        this.snServer = String.format("%s/api/sn/%s", this.serverURL, this.siteName);
        this.credentials = credentials;
        this.providerName = PROVIDER_NAME_DEFAULT;
        this.turSNSitePostParams = new TurSNSitePostParamsBean();
        this.turSNSitePostParams.setUserId(userId);
        this.turSNSitePostParams.setPopulateMetrics(true);

    }

    public TurSNServer(URL serverURL, String siteName, Locale locale, TurApiKeyCredentials apiKeyCredentials,
                       String userId) {
        super();
        this.serverURL = serverURL;
        this.siteName = siteName;
        this.locale = locale;
        this.snServer = String.format("%s/api/sn/%s", this.serverURL, this.siteName);
        this.apiKey = apiKeyCredentials.getApiKey();
        this.providerName = PROVIDER_NAME_DEFAULT;
        this.turSNSitePostParams = new TurSNSitePostParamsBean();
        this.turSNSitePostParams.setUserId(userId);
        this.turSNSitePostParams.setPopulateMetrics(true);

    }

    public TurSNServer(URL serverURL, TurApiKeyCredentials apiKeyCredentials) {
        this(serverURL, SITE_NAME_DEFAULT, LOCALE_DEFAULT, apiKeyCredentials);
    }

    public TurSNServer(URL serverURL, String siteName) {
        this(serverURL, siteName, LOCALE_DEFAULT);
    }

    public TurSNServer(URL serverURL, String siteName, Locale locale) {
        this(serverURL, siteName, locale, (TurUsernamePasswordCredentials) null);
    }

    public TurSNServer(URL serverURL, String siteName, Locale locale, TurUsernamePasswordCredentials credentials) {
        this(serverURL, siteName, locale, credentials, credentials != null ? credentials.getUsername() : null);
    }

    public TurSNServer(URL serverURL, String siteName, Locale locale, TurApiKeyCredentials apiKeyCredentials) {
        this(serverURL, siteName, locale, apiKeyCredentials, null);
    }

    public TurSNServer(URL serverURL, String siteName, TurApiKeyCredentials apiKeyCredentials) {
        this(serverURL, siteName, LOCALE_DEFAULT, apiKeyCredentials, null);
    }

    public TurSNServer(URL serverURL, TurUsernamePasswordCredentials credentials) {
        this(serverURL, SITE_NAME_DEFAULT, LOCALE_DEFAULT, credentials,
                credentials != null ? credentials.getUsername() : null);
    }

    public List<String> getLatestSearches(int rows) {
        try {
            URIBuilder turingURL = new URIBuilder(snServer.concat(LATEST_SEARCHES_CONTEXT))
                    .addParameter(TurSNParamType.LOCALE, getLocale().toLanguageTag())
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
                TurSNClientUtils.authentication(httpPost, this.getCredentials(), this.getApiKey());
                requestLog(turingURL);
                return new ObjectMapper().readValue(openConnectionAndRequest(httpPost),
                        new TypeReference<>() {
                        });
            }
        } catch (JsonProcessingException | URISyntaxException e) {
            log.error(e.getMessage(), e);
        }
        return Collections.emptyList();

    }

    private static void requestLog(URIBuilder turingURL) throws URISyntaxException {
        log.debug("Viglet Turing Request: {}", turingURL.build().toString());
    }

    public void importItems(TurSNJobItems turSNJobItems, boolean showOutput) {
        TurSNJobUtils.importItems(turSNJobItems, this, showOutput);
    }

    public void deleteItemsByType(String typeName) {
        if (credentials != null) {
            TurSNJobUtils.deleteItemsByType(this, typeName);
        } else {
            log.error("No credentials to delete items by {} type", typeName);
        }
    }

    public List<TurSNLocale> getLocales() {

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            return executeLocaleRequest(prepareLocalesRequest(), client);
        } catch (IOException | URISyntaxException e) {
            log.error(e.getMessage(), e);
        }
        return Collections.emptyList();
    }

    public List<String> autoComplete(TurSNAutoCompleteQuery autoCompleteQuery) {

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            return executeAutoCompleteRequest(prepareAutoCompleteRequest(autoCompleteQuery), client);
        } catch (IOException | URISyntaxException e) {
            log.error(e.getMessage(), e);
        }
        return Collections.emptyList();
    }

    private List<TurSNLocale> executeLocaleRequest(HttpGet httpGet, CloseableHttpClient client)
            throws IOException {
        return new ObjectMapper().readValue(getHttpResponse(httpGet, client), new TypeReference<List<TurSNLocale>>() {
        });
    }

    private String getHttpResponse(HttpGet httpGet, CloseableHttpClient client) {
        try {
            return client.execute(httpGet, response ->
                    EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    private List<String> executeAutoCompleteRequest(HttpGet httpGet, CloseableHttpClient client)
            throws IOException {


        return new ObjectMapper().readValue(getHttpResponse(httpGet, client), new TypeReference<List<String>>() {
        });
    }

    private HttpGet prepareLocalesRequest() throws URISyntaxException {
        return prepareGetRequest(new URIBuilder(snServer.concat(LOCALES_CONTEXT)));
    }

    private HttpGet prepareAutoCompleteRequest(TurSNAutoCompleteQuery autoCompleteQuery) throws URISyntaxException {
        URIBuilder turingURL = new URIBuilder(snServer.concat(AUTO_COMPLETE_CONTEXT))
                .addParameter(TurSNParamType.LOCALE, getLocale().toLanguageTag())
                .addParameter(TurSNParamType.QUERY, autoCompleteQuery.getQuery())
                .addParameter(TurSNParamType.ROWS, Integer.toString(autoCompleteQuery.getRows()));

        return prepareGetRequest(turingURL);
    }

    public QueryTurSNResponse query(TurSNQuery turSNQuery) {
        this.turSNQuery = turSNQuery;
        try {
            String requestString = openConnectionAndRequest(prepareQueryRequest());
            if (requestString != null) {
                TurSNSiteSearchBean turSNSiteSearchBean = new ObjectMapper().readValue(requestString,
                        TurSNSiteSearchBean.class);
                return createTuringResponse(turSNSiteSearchBean);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return new QueryTurSNResponse();
    }

    private String openConnectionAndRequest(HttpUriRequestBase httpRequestBase) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            return executeQueryRequest(httpRequestBase, client);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    private String executeQueryRequest(HttpUriRequestBase httpRequestBase, CloseableHttpClient client) {
        try {
            return client.execute(httpRequestBase, response -> {
                if (response.getCode() == 200) {
                    return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                } else {
                    log.error("Error Connection. Status Code: {}", response.getCode());
                }
                return null;
            });
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    private HttpUriRequestBase prepareQueryRequest() {
        try {
            URIBuilder turingURL = new URIBuilder(snServer.concat(SEARCH_CONTEXT))
                    .addParameter(TurSNParamType.LOCALE, getLocale().toLanguageTag())
                    .addParameter(TurSNParamType.QUERY, this.turSNQuery.getQuery());

            groupByRequest(turingURL);
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
            log.error(e.getMessage(), e);
        }
        return null;
    }

    private void groupByRequest(URIBuilder turingURL) {
        if (this.turSNQuery.getGroupBy() != null && !this.turSNQuery.getGroupBy().trim().isEmpty()) {
            turingURL.addParameter(TurSNParamType.GROUP, this.turSNQuery.getGroupBy());
        }
    }

    private QueryTurSNResponse createTuringResponse(TurSNSiteSearchBean turSNSiteSearchBean) {
        QueryTurSNResponse queryTuringResponse = new QueryTurSNResponse();

        queryTuringResponse.setResults(
                setResultsResponse(turSNSiteSearchBean.getResults(), turSNSiteSearchBean.getQueryContext()));
        queryTuringResponse.setGroupResponse(setGroupResponse(turSNSiteSearchBean));
        queryTuringResponse.setPagination(new TurSNPagination(turSNSiteSearchBean.getPagination()));
        queryTuringResponse.setFacetFields(setFacetFieldsResponse(turSNSiteSearchBean));
        queryTuringResponse.setDidYouMean(new TurSNDidYouMean(turSNSiteSearchBean.getWidget().getSpellCheck()));
        queryTuringResponse
                .setSpotlightDocuments(setSpotlightDocumetsResponse(turSNSiteSearchBean.getWidget().getSpotlights()));
        return queryTuringResponse;
    }

    private TurSNGroupList setGroupResponse(TurSNSiteSearchBean turSNSiteSearchBean) {
        List<TurSNGroup> turSNGroups = new ArrayList<>();

        turSNSiteSearchBean.getGroups().forEach(groups -> {
            TurSNGroup turSNGroup = new TurSNGroup();
            turSNGroup.setCount(groups.getCount());
            turSNGroup.setLimit(groups.getLimit());
            turSNGroup.setName(groups.getName());
            turSNGroup.setPage(groups.getPage());
            turSNGroup.setPageCount(groups.getPageCount());
            turSNGroup.setPageEnd(groups.getPageEnd());
            turSNGroup.setPageStart(groups.getPageStart());
            turSNGroup.setPagination(new TurSNPagination(groups.getPagination()));
            turSNGroup.setResults(setResultsResponse(groups.getResults(), turSNSiteSearchBean.getQueryContext()));
            turSNGroups.add(turSNGroup);
        });

        TurSNGroupList turSNGroupList = new TurSNGroupList();
        turSNGroupList.setTurSNGroups(turSNGroups);

        return turSNGroupList;

    }

    private List<TurSNSpotlightDocument> setSpotlightDocumetsResponse(
            List<TurSNSiteSpotlightDocumentBean> turSNSiteSpotlightDocumentBeans) {
        List<TurSNSpotlightDocument> turSNSpotlightDocuments = new ArrayList<>();
        turSNSiteSpotlightDocumentBeans.forEach(turSNSiteSpotlightDocumentBean -> turSNSpotlightDocuments
                .add(new TurSNSpotlightDocument(turSNSiteSpotlightDocumentBean)));
        return turSNSpotlightDocuments;
    }

    private TurSNFacetFieldList setFacetFieldsResponse(TurSNSiteSearchBean turSNSiteSearchBean) {
        return new TurSNFacetFieldList(turSNSiteSearchBean.getWidget().getFacet(),
                turSNSiteSearchBean.getWidget().getFacetToRemove());
    }

    private TurSNDocumentList setResultsResponse(TurSNSiteSearchResultsBean turSNSiteSearchResultsBean,
                                                 TurSNSiteSearchQueryContextBean turSNSiteSearchQueryContextBean) {
        if (hasSearchResults(turSNSiteSearchResultsBean)) {
            List<TurSNDocument> turSNDocuments = new ArrayList<>();
            turSNSiteSearchResultsBean.getDocument().forEach(turSNSiteSearchDocumentBean -> {
                TurSNDocument turSNDocument = new TurSNDocument();
                turSNDocument.setContent(turSNSiteSearchDocumentBean);
                turSNDocuments.add(turSNDocument);
            });
            TurSNDocumentList turSNDocumentList = new TurSNDocumentList();
            turSNDocumentList.setTurSNDocuments(turSNDocuments);
            turSNDocumentList.setQueryContext(turSNSiteSearchQueryContextBean);
            return turSNDocumentList;
        }
        return new TurSNDocumentList();
    }

    private boolean hasSearchResults(TurSNSiteSearchResultsBean turSNSiteSearchResultsBean) {
        if (turSNSiteSearchResultsBean == null) {
            log.error("Empty result.");
            return false;
        }

        if (turSNSiteSearchResultsBean.getDocument() == null || turSNSiteSearchResultsBean.getDocument().isEmpty()) {

            log.debug("No results.");
            return false;
        }

        return true;
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

            TurSNClientUtils.authentication(httpPost, this.getCredentials(), this.getApiKey());

            requestLog(turingURL);
        } catch (JsonProcessingException | URISyntaxException e) {
            log.error(e.getMessage(), e);
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

            requestLog(turingURL);

        } catch (URISyntaxException e) {
            log.error(e.getMessage(), e);
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

                turingURL.addParameter(TurSNParamType.FILTER_QUERIES_DEFAULT,
                        String.format("%s:[%s TO %s]", fieldDate, startDate, endDate));
            }
        }
    }

    private void sortRequest(URIBuilder turingURL) {
        if (this.turSNQuery.getSortField() != null) {
            TurSNSortField turSortField = this.turSNQuery.getSortField();

            if (turSortField.getSort() != null) {
                if (turSortField.getField() == null) {
                    String orderMod;
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
                turingURL.addParameter(TurSNParamType.FILTER_QUERIES_DEFAULT, fieldQuery);
            }
        }
    }

    private void rowsRequest(URIBuilder turingURL) {
        if (this.turSNQuery.getRows() > 0) {
            turingURL.addParameter(TurSNParamType.ROWS, Integer.toString(this.turSNQuery.getRows()));
        }
    }
}
