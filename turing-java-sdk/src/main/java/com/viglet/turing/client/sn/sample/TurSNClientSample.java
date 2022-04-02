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

package com.viglet.turing.client.sn.sample;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.viglet.turing.client.sn.HttpTurSNServer;
import com.viglet.turing.client.sn.TurSNDocumentList;
import com.viglet.turing.client.sn.TurSNQuery;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.autocomplete.TurSNAutoCompleteQuery;
import com.viglet.turing.client.sn.credentials.TurUsernamePasswordCredentials;
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

	@SuppressWarnings("unused")
	public static void main(String[] args) {

		HttpTurSNServer turSNServer;
		try {
			System.out.println("--- Locales");
			turSNServer = new HttpTurSNServer(new URL("http://localhost:2700"), "Sample");
			turSNServer.getLocales().forEach(System.out::println);

			System.out.println("--- Query");
			turSNServer = new HttpTurSNServer(new URL("http://localhost:2700"), "Sample", "en_US", new TurUsernamePasswordCredentials("admin", "admin"), "alexandre10");

			TurSNQuery query = new TurSNQuery();
			if (args.length > 0) {
				query.setQuery(args[0]);
			} else {
				query.setQuery("tast"); // fix to test
			}
			query.setFieldQueries(Arrays.asList("type:Page"));
			query.setRows(1);
			query.setSortField(TurSNQuery.ORDER.asc);
			query.setPageNumber(1);

			QueryTurSNResponse response = turSNServer.query(query);
			TurSNDocumentList turSNResults = response.getResults();

			System.out.println("--- Spotlight Documents");
			List<TurSNSpotlightDocument> turSNSpotlightDocuments = response.getSpotlightDocuments();
			turSNSpotlightDocuments.forEach(turSNSpotlightDocument -> System.out
					.println(String.format("%s %s %s", turSNSpotlightDocument.getPosition(),
							turSNSpotlightDocument.getTitle(), turSNSpotlightDocument.getContent())));

			System.out.println("--- Pagination");
			TurSNPagination turSNPagination = response.getPagination();
			turSNPagination.getAllPages().forEach(page -> {
				System.out.println(page.getLabel());
				page.getQueryParams().ifPresent(queryParam -> queryParam.entrySet()
						.forEach(param -> System.out.println(param.getKey() + " " + param.getValue().toString())));
			});
			System.out.println("---");
			turSNPagination.getLastPage().ifPresent(page -> System.out.println(page.getLabel()));

			System.out.println("---");
			response.getFacetFields().forEach(facetFields -> {
				System.out.println(String.format("Facet: %s - %s - %s - %s", facetFields.getLabel(),
						facetFields.getName(), facetFields.getDescription(), facetFields.getType()));
				facetFields.getValues().forEach(facetField -> {
					System.out.println(facetField.getLabel() + "(" + facetField.getCount() + ")");
					facetField.getQueryParams().ifPresent(queryParam -> queryParam.entrySet()
							.forEach(param -> System.out.println(param.getKey() + " " + param.getValue().toString())));
				});

			});
			response.getFacetFields().getFacetWithRemovedValues().ifPresent(facetToRemove -> {
				System.out.println("---");
				System.out.println(facetToRemove.getLabel());
				facetToRemove.getValues().forEach(value -> {
					System.out.println(value.getLabel());
					value.getQueryParams().ifPresent(queryParam -> queryParam.entrySet()
							.forEach(param -> System.out.println(param.getKey() + " " + param.getValue().toString())));
				});

			});
			System.out.println("--- Did You Mean");
			if (response.getDidYouMean().isCorrectedText()) {
				System.out.println(
						String.format("Original Query %s: %s", response.getDidYouMean().getOriginal().getText(),
								response.getDidYouMean().getOriginal().getLink()));
				System.out.println(
						String.format("Correct Query %s: %s", response.getDidYouMean().getCorrected().getText(),
								response.getDidYouMean().getCorrected().getLink()));
			}

			System.out.println("--- Auto complete");
			TurSNAutoCompleteQuery autoCompleteQuery = new TurSNAutoCompleteQuery();
			autoCompleteQuery.setQuery("vig");
			autoCompleteQuery.setRows(5);
			turSNServer.autoComplete(autoCompleteQuery).forEach(System.out::println);
		} catch (MalformedURLException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
	}
}
