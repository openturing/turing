/*
 * Copyright (C) 2016-2021 the original author or authors. 
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
import java.util.List;
import java.util.TimeZone;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.viglet.turing.api.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.api.sn.bean.TurSNSiteSearchQueryContext;
import com.viglet.turing.client.sn.TurSNQuery.ORDER;
import com.viglet.turing.client.sn.autocomplete.TurSNAutoCompleteQuery;
import com.viglet.turing.client.sn.facet.TurSNFacetFieldList;
import com.viglet.turing.client.sn.pagination.TurSNPagination;
import com.viglet.turing.client.sn.response.QueryTurSNResponse;

import java.util.logging.*;

/**
 * Connect to Turing AI Server.
 * 
 * @since 0.3.4
 */
public class TurSNServer {

	private static Logger logger = Logger.getLogger(TurSNServer.class.getName());

	private String turSNServer;

	private TurSNQuery turSNQuery;

	private URL serverURL;

	private String siteName;

	private String locale;

	@Deprecated
	public TurSNServer(String turSNServer) {
		super();
		this.turSNServer = turSNServer;

	}

	public TurSNServer(URL serverURL, String siteName) {
		super();
		this.serverURL = serverURL;
		this.siteName = siteName;
		this.locale = "default";
		this.turSNServer = String.format("%s/api/sn/%s", this.serverURL, this.siteName, this.locale);

	}

	public TurSNServer(URL serverURL, String siteName, String locale) {
		super();
		this.serverURL = serverURL;
		this.siteName = siteName;
		this.locale = locale;
		this.turSNServer = String.format("%s/api/sn/%s", this.serverURL, this.siteName);

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

	public List<String> autoComplete(TurSNAutoCompleteQuery autoCompleteQuery) {
		List<String> autoCompleteList = new ArrayList<>();
		URIBuilder turingURL;

		HttpGet httpGet;

		try {
			turingURL = new URIBuilder(turSNServer + "/ac").addParameter("q", autoCompleteQuery.getQuery())
					.addParameter("rows", Integer.toString(autoCompleteQuery.getRows()));

			httpGet = new HttpGet(turingURL.build());

			httpGet.setHeader("Accept", "application/json");
			httpGet.setHeader("Content-type", "application/json");
			httpGet.setHeader("Accept-Encoding", "UTF-8");
			HttpResponse response;

			logger.info(String.format("Viglet Turing Request: %s", turingURL.build().toString()));
			try (CloseableHttpClient client = HttpClients.createDefault()) {
				response = client.execute(httpGet);
				HttpEntity entity = response.getEntity();
				String result = EntityUtils.toString(entity, StandardCharsets.UTF_8);

				ObjectMapper objectMapper = new ObjectMapper();
				autoCompleteList = objectMapper.readValue(result, new TypeReference<List<String>>() {
				});
			}
		} catch (URISyntaxException | IOException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return autoCompleteList;
	}

	public QueryTurSNResponse query(TurSNQuery turSNQuery) {
		this.turSNQuery = turSNQuery;

		QueryTurSNResponse queryTuringResponse = new QueryTurSNResponse();
		URIBuilder turingURL;

		HttpGet httpGet;

		try {
			turingURL = new URIBuilder(turSNServer + "/search").addParameter("q", this.turSNQuery.getQuery());

			// Rows
			if (this.turSNQuery.getRows() > 0) {
				turingURL.addParameter("rows", Integer.toString(this.turSNQuery.getRows()));
			}

			// Field Query
			if (this.turSNQuery.getFieldQueries() != null) {
				for (String fieldQuery : this.turSNQuery.getFieldQueries()) {
					turingURL.addParameter("fq[]", fieldQuery);
				}
			}

			// Targeting Rule
			if (this.turSNQuery.getTargetingRules() != null) {
				for (String targetingRule : this.turSNQuery.getTargetingRules()) {
					turingURL.addParameter("tr[]", targetingRule);
				}
			}

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

			// Page Number
			if (this.turSNQuery.getPageNumber() > 0) {
				turingURL.addParameter("p", String.format(Integer.toString(this.turSNQuery.getPageNumber())));
			} else {
				turingURL.addParameter("p", "1");
			}

			httpGet = new HttpGet(turingURL.build());

			httpGet.setHeader("Accept", "application/json");
			httpGet.setHeader("Content-type", "application/json");
			httpGet.setHeader("Accept-Encoding", "UTF-8");
			HttpResponse response;

			logger.info(String.format("Viglet Turing Request: %s", turingURL.build().toString()));
			try (CloseableHttpClient client = HttpClients.createDefault()) {
				response = client.execute(httpGet);
				HttpEntity entity = response.getEntity();
				String result = EntityUtils.toString(entity, StandardCharsets.UTF_8);

				ObjectMapper objectMapper = new ObjectMapper();
				TurSNSiteSearchBean turSNSiteSearchBean = objectMapper.readValue(result, TurSNSiteSearchBean.class);

				TurSNDocumentList turSNDocumentList = new TurSNDocumentList();
				List<TurSNDocument> turSNDocuments = new ArrayList<>();

				turSNSiteSearchBean.getResults().getDocument().forEach(turSNSiteSearchDocumentBean -> {
					TurSNDocument turSNDocument = new TurSNDocument();
					turSNDocument.setContent(turSNSiteSearchDocumentBean);
					turSNDocuments.add(turSNDocument);
				});

				TurSNSiteSearchQueryContext turSNSiteSearchQueryContext = turSNSiteSearchBean.getQueryContext();

				turSNDocumentList.setTurSNDocuments(turSNDocuments);
				turSNDocumentList.setQueryContext(turSNSiteSearchQueryContext);

				queryTuringResponse.setResults(turSNDocumentList);
				queryTuringResponse.setPagination(new TurSNPagination(turSNSiteSearchBean.getPagination()));

				TurSNFacetFieldList facetFields = new TurSNFacetFieldList(turSNSiteSearchBean.getWidget().getFacet(),
						turSNSiteSearchBean.getWidget().getFacetToRemove());
				queryTuringResponse.setFacetFields(facetFields);
			}
		} catch (UnsupportedOperationException | IOException | URISyntaxException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}

		return queryTuringResponse;
	}
}
