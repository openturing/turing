/*
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.turing.client.sn.sample;

import com.viglet.turing.client.sn.*;
import com.viglet.turing.client.sn.autocomplete.TurSNAutoCompleteQuery;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.sn.didyoumean.TurSNDidYouMean;
import com.viglet.turing.client.sn.pagination.TurSNPagination;
import com.viglet.turing.client.sn.response.QueryTurSNResponse;
import com.viglet.turing.client.sn.spotlight.TurSNSpotlightDocument;
import lombok.extern.slf4j.Slf4j;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sample code to use this SDK.
 * 
 * @since 0.3.4
 */

@Slf4j
public class TurSNClientSample {
	private static final Logger logger = Logger.getLogger(TurSNClientSample.class.getName());

	private static final String TURING_URL = "http://localhost:2700";
	private static final String TURING_SITE = "Sample";
	private static final Locale TURING_LOCALE = Locale.US;
	private static final String TURING_API_KEY = "apiKeySample";
	private static final String TURING_USERID = "user1";
	private static final String QUERY = "tast";
	private static final String FILTER_QUERY = "type:Page";
	private static final String AUTO_COMPLETE_TERM = "vig";

	public static void main(String[] args) {

		HttpTurSNServer turSNServer;
		try {
			log.info("--- Locales");
			locales();

			turSNServer = new HttpTurSNServer(URI.create(TURING_URL).toURL(), TURING_SITE, TURING_LOCALE,
					new TurApiKeyCredentials(TURING_API_KEY), TURING_USERID);

			log.info("--- Auto complete");
			autoComplete(turSNServer);

			log.info("--- Latest Searches");
			latestSearches(turSNServer);

			log.info("--- Query");
			QueryTurSNResponse response = query(args, turSNServer);

			log.info("--- Spotlight Documents");
			spolight(response);

			log.info("--- Pagination");
			pagination(response.getPagination());

			log.info("--- Facet");
			facet(response);

			log.info("--- Did You Mean");
			didYouMean(response);

			log.info("--- Group By");
			groupBy(args, turSNServer);

		} catch (MalformedURLException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
	}

	private static void locales() throws MalformedURLException {
		HttpTurSNServer turSNServer;
		turSNServer = new HttpTurSNServer(URI.create(TURING_URL).toURL(), TURING_SITE);
		turSNServer.getLocales().forEach(l -> log.info(String.valueOf(l)));
	}

	private static void latestSearches(HttpTurSNServer turSNServer) {
		log.info(String.valueOf(turSNServer.getLatestSearches(10).size()));
		turSNServer.getLatestSearches(10).forEach(log::info);
	}

	private static QueryTurSNResponse query(String[] args, HttpTurSNServer turSNServer) {
		TurSNQuery query = new TurSNQuery();
		if (args.length > 0) {
			query.setQuery(args[0]);
		} else {
			query.setQuery(QUERY); // fix to test
		}
		query.setFieldQueries(Collections.singletonList(FILTER_QUERY));
		query.setRows(1);
		query.setSortField(TurSNQuery.ORDER.asc);
		query.setPageNumber(1);
		QueryTurSNResponse response = turSNServer.query(query);
		@SuppressWarnings("unused")
		TurSNDocumentList turSNResults = response.getResults();
		return response;
	}

	private static void groupBy(String[] args, HttpTurSNServer turSNServer) {
		TurSNQuery query = new TurSNQuery();
		if (args.length > 0) {
			query.setQuery(args[0]);
		} else {
			query.setQuery(QUERY);
		}
		query.setRows(10);
		query.setGroupBy("type");
		query.setPageNumber(1);

		QueryTurSNResponse response = turSNServer.query(query);

		TurSNGroupList turSNGroupList = response.getGroupResponse();
		if (turSNGroupList != null) {
			turSNGroupList.forEach(group -> {
				log.info("Group Name: ".concat(group.getName()));
				pagination(group.getPagination());
				
				group.getResults().forEach(result -> {
					if (result.getContent() != null && result.getContent().getFields() != null) {
						log.info((String) result.getContent().getFields().get("title"));
					}
				}); 
			});
		}

	}

	private static void spolight(QueryTurSNResponse response) {
		List<TurSNSpotlightDocument> turSNSpotlightDocuments = response.getSpotlightDocuments();
		if (turSNSpotlightDocuments != null) {
			turSNSpotlightDocuments.forEach(turSNSpotlightDocument -> log.info("{} {} {}%n",
					turSNSpotlightDocument.getPosition(),
					turSNSpotlightDocument.getTitle(),
					turSNSpotlightDocument.getContent()));
		}
	}

	private static void pagination(TurSNPagination turSNPagination) {
		if (turSNPagination != null) {
			turSNPagination.getAllPages().forEach(page -> {
				log.info(page.getLabel());
				page.getQueryParams()
						.ifPresent(queryParam -> queryParam.entrySet().forEach(TurSNClientSample::showKeyValue));
				log.info(" ");
			});

			log.info("---");
			turSNPagination.getLastPage().ifPresent(page -> log.info(page.getLabel()));
		}
	}

	private static void facet(QueryTurSNResponse response) {
		if (response.getFacetFields() != null) {
			response.getFacetFields().forEach(facetFields -> {
				log.info("Facet: {} - {} - {} - {}n", facetFields.getLabel(),
						facetFields.getName(), facetFields.getDescription(), facetFields.getType());
				facetFields.getValues().forEach(facetField -> {
					log.info("{} ({})%n", facetField.getLabel(), facetField.getCount());
					facetField.getQueryParams()
							.ifPresent(queryParam -> queryParam.entrySet().forEach(TurSNClientSample::showKeyValue));
				});

			});
			response.getFacetFields().getFacetWithRemovedValues().ifPresent(facetToRemove -> {
				log.info("---");
				log.info(facetToRemove.getLabel());
				facetToRemove.getValues().forEach(value -> {
					log.info(value.getLabel());
					value.getQueryParams()
							.ifPresent(queryParam -> queryParam.entrySet().forEach(TurSNClientSample::showKeyValue));
				});

			});
		}
	}

	private static void showKeyValue(Entry<String, List<String>> param) {
		log.info("{} {}%n", param.getKey(), param.getValue().toString());
	}

	private static void didYouMean(QueryTurSNResponse response) {
		TurSNDidYouMean turSNDidYouMean = response.getDidYouMean();
		if (turSNDidYouMean != null && turSNDidYouMean.isCorrectedText()) {
			log.info("Original Query {}: {}%n", turSNDidYouMean.getOriginal().getText(),
					turSNDidYouMean.getOriginal().getLink());
			log.info("Correct Query {}: {}%n", turSNDidYouMean.getCorrected().getText(),
					turSNDidYouMean.getCorrected().getLink());
		}
	}

	private static void autoComplete(HttpTurSNServer turSNServer) {
		TurSNAutoCompleteQuery autoCompleteQuery = new TurSNAutoCompleteQuery();
		autoCompleteQuery.setQuery(AUTO_COMPLETE_TERM);
		autoCompleteQuery.setRows(5);
		turSNServer.autoComplete(autoCompleteQuery).forEach(log::info);
	}
}
