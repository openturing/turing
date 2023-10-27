/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.sn.ac;

import com.viglet.turing.se.TurSEStopword;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import lombok.extern.slf4j.Slf4j;
import org.apache.solr.client.solrj.response.SpellCheckResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
@Slf4j
@Component
public class TurSNAutoComplete {
	private static final String SPACE_CHAR = " ";
	private static final boolean USE_BIGGER_TERMS = false;
	private static final boolean USE_TERMS_QUERY_EQUALS_AUTO_COMPLETE = true;
	private static final boolean USE_REPEAT_QUERY_TEXT_ON_AUTOCOMPLETE = false;
	@Autowired
	private TurSolr turSolr;
	@Autowired
	private TurSEStopword turSEStopword;
	@Autowired
	private TurSolrInstanceProcess turSolrInstanceProcess;

	public List<String> autoComplete(String siteName, String q, String locale, long rows) {
	
		if (q.length() > 1) {
			return turSolrInstanceProcess.initSolrInstance(siteName, locale).map(instance -> {
				SpellCheckResponse turSEResults = executeAutoCompleteFromSE(instance, q);
				int numberOfWordsFromQuery = q.split(SPACE_CHAR).length;
				if (q.endsWith(SPACE_CHAR)) {
					numberOfWordsFromQuery++;
				}
				List<String> autoCompleteListFormatted = createFormattedList(turSEResults, instance,
						numberOfWordsFromQuery);
				List<String> autoCompleteListShrink = removeDuplicatedTerms(autoCompleteListFormatted,
						numberOfWordsFromQuery, q);
				return autoCompleteListShrink.stream().limit(rows).toList();
			}).orElse(Collections.emptyList());
		} else {
			return Collections.emptyList();
		}
	}

	
	private SpellCheckResponse executeAutoCompleteFromSE(TurSolrInstance turSolrInstance, String q) {
		SpellCheckResponse turSEResults = null;
		try {
			turSEResults = turSolr.autoComplete(turSolrInstance, q);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return turSEResults;
	}

	private List<String> createFormattedList(SpellCheckResponse turSEResults, TurSolrInstance turSolrInstance,
			int numberOfWordsFromQuery) {
		List<String> autoCompleteListFormatted = new ArrayList<>();
		if (hasSuggestions(turSEResults)) {
			List<String> autoCompleteList = turSEResults.getSuggestions().get(0).getAlternatives();
			TurSNAutoCompleteListData autoCompleteListData = new TurSNAutoCompleteListData(turSEStopword.getStopWords(turSolrInstance));
			autoCompleteList.forEach(autoCompleteItem -> processTerm(numberOfWordsFromQuery, autoCompleteListFormatted,
					autoCompleteListData, autoCompleteItem));
		}
		return autoCompleteListFormatted;
	}

	private void processTerm(int numberOfWordsFromQuery, List<String> autoCompleteListFormatted,
			TurSNAutoCompleteListData autoCompleteListData, String autoCompleteItem) {
		if (isAddTermToList(autoCompleteListData, numberOfWordsFromQuery, autoCompleteItem)) {
			autoCompleteListFormatted.add(autoCompleteItem);
		}
	}

	private boolean isAddTermToList(TurSNAutoCompleteListData autoCompleteListData, int numberOfWordsFromQuery,
			String autoCompleteItem) {
		String[] autoCompleteItemTokens = autoCompleteItem.split(SPACE_CHAR);
		int numberOfWordsFromAutoCompleteItem = autoCompleteItemTokens.length;
		String autoCompleteItemFirstToken = autoCompleteItemTokens[0];
		String autoCompleteItemLastToken = autoCompleteItemTokens[autoCompleteItemTokens.length - 1];
		boolean numberOfWordsIsEquals = numberOfWordsFromQuery == numberOfWordsFromAutoCompleteItem;
		boolean firstWordIsStopword = autoCompleteListData.getStopWords().contains(autoCompleteItemFirstToken);
		boolean lastWordIsStopword = autoCompleteListData.getStopWords().contains(autoCompleteItemLastToken);

        return (!USE_TERMS_QUERY_EQUALS_AUTO_COMPLETE || numberOfWordsIsEquals) && !firstWordIsStopword && !lastWordIsStopword;
	}

	private boolean hasSuggestions(SpellCheckResponse turSEResults) {
		return turSEResults != null && turSEResults.getSuggestions() != null
				&& !turSEResults.getSuggestions().isEmpty();
	}

	@SuppressWarnings("unused")
	private List<String> removeDuplicatedTerms(List<String> autoCompleteList, int numberOfWordsFromQuery, String termQuery) {
		List<String> autoCompleteWithoutDuplicated = autoCompleteList.stream().distinct().collect(Collectors.toList());
		if (USE_REPEAT_QUERY_TEXT_ON_AUTOCOMPLETE && autoCompleteWithoutDuplicated.isEmpty()) {
			autoCompleteWithoutDuplicated.add(termQuery);
		}
		return USE_BIGGER_TERMS ? biggerTerms(autoCompleteWithoutDuplicated) : autoCompleteWithoutDuplicated;
	}

	private List<String> biggerTerms(List<String> autoCompleteWithoutDuplicated) {
		List<String> autoCompleteOnlyBiggerTerms = new ArrayList<>();
		for (String term : autoCompleteWithoutDuplicated) {
			List<String> resultList = autoCompleteWithoutDuplicated.stream().filter(s -> s.startsWith(term))
					.toList();
			if (resultList.size() == 1) {
				autoCompleteOnlyBiggerTerms.add(resultList.get(0));
			}
		}

		return autoCompleteOnlyBiggerTerms;
	}

	
}
