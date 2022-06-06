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

package com.viglet.turing.client.sn.sample;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.viglet.turing.client.sn.HttpTurSNServer;
import com.viglet.turing.client.sn.TurSNDocumentList;
import com.viglet.turing.client.sn.TurSNGroupList;
import com.viglet.turing.client.sn.TurSNQuery;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.autocomplete.TurSNAutoCompleteQuery;
import com.viglet.turing.client.sn.credentials.TurUsernamePasswordCredentials;
import com.viglet.turing.client.sn.didyoumean.TurSNDidYouMean;
import com.viglet.turing.client.sn.pagination.TurSNPagination;
import com.viglet.turing.client.sn.response.QueryTurSNResponse;
import com.viglet.turing.client.sn.spotlight.TurSNSpotlightDocument;

/**
 * Sample code to use this SDK.
 * 
 * @since 0.3.4
 */
public class TurSNClientSample {
	private static Logger logger = Logger.getLogger(TurSNServer.class.getName());

	private static final String TURING_URL = "http://localhost:2700";
	private static final String TURING_SITE = "Sample";
	private static final String TURING_LOCALE = "en_US";
	private static final String TURING_USERNAME = "admin";
	private static final String TURING_PASSWORD = "admin";
	private static final String TURING_USERID = "user1";
	private static final String QUERY = "tast";
	private static final String FILTER_QUERY = "type:Page";
	private static final String AUTO_COMPLETE_TERM = "vig";

	public static void main(String[] args) {

		HttpTurSNServer turSNServer;
		try {
			System.out.println("--- Locales");
			locales();

			turSNServer = new HttpTurSNServer(new URL(TURING_URL), TURING_SITE, TURING_LOCALE,
					new TurUsernamePasswordCredentials(TURING_USERNAME, TURING_PASSWORD), TURING_USERID);

			System.out.println("--- Auto complete");
			autoComplete(turSNServer);

			System.out.println("--- Latest Searches");
			latestSearches(turSNServer);

			System.out.println("--- Query");
			QueryTurSNResponse response = query(args, turSNServer);

			System.out.println("--- Spotlight Documents");
			spolight(response);

			System.out.println("--- Pagination");
			pagination(response.getPagination());

			System.out.println("--- Facet");
			facet(response);

			System.out.println("--- Did You Mean");
			didYouMean(response);

			System.out.println("--- Group By");
			groupBy(args, turSNServer);

		} catch (MalformedURLException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
	}

	private static void locales() throws MalformedURLException {
		HttpTurSNServer turSNServer;
		turSNServer = new HttpTurSNServer(new URL(TURING_URL), TURING_SITE);
		turSNServer.getLocales().forEach(System.out::println);
	}

	private static void latestSearches(HttpTurSNServer turSNServer) {
		System.out.println(turSNServer.getLatestSearches(10).size());
		turSNServer.getLatestSearches(10).forEach(System.out::println);
	}

	private static QueryTurSNResponse query(String[] args, HttpTurSNServer turSNServer) {
		TurSNQuery query = new TurSNQuery();
		if (args.length > 0) {
			query.setQuery(args[0]);
		} else {
			query.setQuery(QUERY); // fix to test
		}
		query.setFieldQueries(Arrays.asList(FILTER_QUERY));
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
				System.out.println("Group Name: ".concat(group.getName()));
				pagination(group.getPagination());
				
				group.getResults().forEach(result -> {
					if (result.getContent() != null && result.getContent().getFields() != null) {
						System.out.println(result.getContent().getFields().get("title"));
					}
				}); 
			});
		}

	}

	private static void spolight(QueryTurSNResponse response) {
		List<TurSNSpotlightDocument> turSNSpotlightDocuments = response.getSpotlightDocuments();
		if (turSNSpotlightDocuments != null) {
			turSNSpotlightDocuments.forEach(turSNSpotlightDocument -> System.out
					.println(String.format("%s %s %s", turSNSpotlightDocument.getPosition(),
							turSNSpotlightDocument.getTitle(), turSNSpotlightDocument.getContent())));
		}
	}

	private static void pagination(TurSNPagination turSNPagination) {
		if (turSNPagination != null) {
			turSNPagination.getAllPages().forEach(page -> {
				System.out.println(page.getLabel());
				page.getQueryParams()
						.ifPresent(queryParam -> queryParam.entrySet().forEach(param -> showKeyValue(param)));
				System.out.println(" ");
			});

			System.out.println("---");
			turSNPagination.getLastPage().ifPresent(page -> System.out.println(page.getLabel()));
		}
	}

	private static void facet(QueryTurSNResponse response) {
		if (response.getFacetFields() != null) {
			response.getFacetFields().forEach(facetFields -> {
				System.out.println(String.format("Facet: %s - %s - %s - %s", facetFields.getLabel(),
						facetFields.getName(), facetFields.getDescription(), facetFields.getType()));
				facetFields.getValues().forEach(facetField -> {
					System.out.println(String.format("%s (%s)", facetField.getLabel(), facetField.getCount()));
					facetField.getQueryParams()
							.ifPresent(queryParam -> queryParam.entrySet().forEach(param -> showKeyValue(param)));
				});

			});
			response.getFacetFields().getFacetWithRemovedValues().ifPresent(facetToRemove -> {
				System.out.println("---");
				System.out.println(facetToRemove.getLabel());
				facetToRemove.getValues().forEach(value -> {
					System.out.println(value.getLabel());
					value.getQueryParams()
							.ifPresent(queryParam -> queryParam.entrySet().forEach(param -> showKeyValue(param)));
				});

			});
		}
	}

	private static void showKeyValue(Entry<String, List<String>> param) {
		System.out.println(String.format("%s %s", param.getKey(), param.getValue().toString()));
	}

	private static void didYouMean(QueryTurSNResponse response) {
		TurSNDidYouMean turSNDidYouMean = response.getDidYouMean();
		if (turSNDidYouMean != null && turSNDidYouMean.isCorrectedText()) {
			System.out.println(String.format("Original Query %s: %s", turSNDidYouMean.getOriginal().getText(),
					turSNDidYouMean.getOriginal().getLink()));
			System.out.println(String.format("Correct Query %s: %s", turSNDidYouMean.getCorrected().getText(),
					turSNDidYouMean.getCorrected().getLink()));
		}
	}

	private static void autoComplete(HttpTurSNServer turSNServer) {
		TurSNAutoCompleteQuery autoCompleteQuery = new TurSNAutoCompleteQuery();
		autoCompleteQuery.setQuery(AUTO_COMPLETE_TERM);
		autoCompleteQuery.setRows(5);
		turSNServer.autoComplete(autoCompleteQuery).forEach(System.out::println);
	}
}
