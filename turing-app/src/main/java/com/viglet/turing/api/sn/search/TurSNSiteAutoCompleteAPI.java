/*
 * Copyright (C) 2016-2022 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.api.sn.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.solr.client.solrj.response.SpellCheckResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import com.viglet.turing.se.TurSEStopword;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/sn/{siteName}/ac")
@Tag(name = "Semantic Navigation Auto Complete", description = "Semantic Navigation Auto Complete API")
public class TurSNSiteAutoCompleteAPI {
	private static final Log logger = LogFactory.getLog(TurSNSiteAutoCompleteAPI.class);
	private static final String SPACE_CHAR = " ";
	private static final boolean USE_BIGGER_TERMS = false;
	private static final boolean USE_TERMS_QUERY_EQUALS_AUTO_COMPLETE = true;
	@Autowired
	private TurSolr turSolr;
	@Autowired
	private TurSEStopword turSEStopword;
	@Autowired
	private TurSolrInstanceProcess turSolrInstanceProcess;

	@GetMapping
	public List<String> turSNSiteAutoComplete(@PathVariable String siteName,
			@RequestParam(required = true, name = TurSNParamType.QUERY) String q,
			@RequestParam(required = false, defaultValue = "20", name = TurSNParamType.ROWS) long rows,
			@RequestParam(required = false, name = TurSNParamType.LOCALE) String locale, HttpServletRequest request) {
		if (q.length() > 1 && !q.equals("*")) {
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
				return autoCompleteListShrink.stream().limit(rows).collect(Collectors.toList());
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
			logger.error(e.getMessage(), e);
		}
		return turSEResults;
	}

	private List<String> createFormattedList(SpellCheckResponse turSEResults, TurSolrInstance turSolrInstance,
			int numberOfWordsFromQuery) {
		List<String> autoCompleteListFormatted = new ArrayList<>();
		if (hasSuggestions(turSEResults)) {
			List<String> autoCompleteList = turSEResults.getSuggestions().get(0).getAlternatives();
			AutoCompleteListData autoCompleteListData = new AutoCompleteListData(turSolrInstance);
			autoCompleteList.forEach(autoCompleteItem -> processTerm(numberOfWordsFromQuery, autoCompleteListFormatted,
					autoCompleteListData, autoCompleteItem));
		}
		return autoCompleteListFormatted;
	}

	private void processTerm(int numberOfWordsFromQuery, List<String> autoCompleteListFormatted,
			AutoCompleteListData autoCompleteListData, String autoCompleteItem) {
		if (isAddTermToList(autoCompleteListData, numberOfWordsFromQuery, autoCompleteItem)) {
			autoCompleteListFormatted.add(autoCompleteItem);
		}
	}

	private boolean isAddTermToList(AutoCompleteListData autoCompleteListData, int numberOfWordsFromQuery,
			String autoCompleteItem) {
		String[] autoCompleteItemTokens = autoCompleteItem.split(SPACE_CHAR);
		int numberOfWordsFromAutoCompleteItem = autoCompleteItemTokens.length;
		String autoCompleteItemFirstToken = autoCompleteItemTokens[0];
		String autoCompleteItemLastToken = autoCompleteItemTokens[autoCompleteItemTokens.length - 1];
		boolean numberOfWordsIsEquals = numberOfWordsFromQuery == numberOfWordsFromAutoCompleteItem;
		boolean firstWordIsStopword = autoCompleteListData.getStopWords().contains(autoCompleteItemFirstToken);
		boolean lastWordIsStopword = autoCompleteListData.getStopWords().contains(autoCompleteItemLastToken);
		boolean addItem = true;

		if ((USE_TERMS_QUERY_EQUALS_AUTO_COMPLETE && !numberOfWordsIsEquals) || firstWordIsStopword || lastWordIsStopword) {
			addItem = false;
		}
		
		return addItem;
	}

	private boolean hasSuggestions(SpellCheckResponse turSEResults) {
		return turSEResults != null && turSEResults.getSuggestions() != null
				&& !turSEResults.getSuggestions().isEmpty();
	}

	private List<String> removeDuplicatedTerms(List<String> autoCompleteList, int numberOfWordsFromQuery, String termQuery) {
		List<String> autoCompleteWithoutDuplicated = autoCompleteList.stream().distinct().collect(Collectors.toList());
		if (autoCompleteWithoutDuplicated.isEmpty()) {
			autoCompleteWithoutDuplicated.add(termQuery);
		}
		return USE_BIGGER_TERMS ? biggerTerms(autoCompleteWithoutDuplicated) : autoCompleteWithoutDuplicated;
	}

	private List<String> biggerTerms(List<String> autoCompleteWithoutDuplicated) {
		List<String> autoCompleteOnlyBiggerTerms = new ArrayList<>();
		for (String term : autoCompleteWithoutDuplicated) {
			List<String> resultList = autoCompleteWithoutDuplicated.stream().filter(s -> s.startsWith(term))
					.collect(Collectors.toList());
			if (resultList.size() == 1) {
				autoCompleteOnlyBiggerTerms.add(resultList.get(0));
			}
		}

		return autoCompleteOnlyBiggerTerms;
	}

	class AutoCompleteListData {
		private List<String> stopWords = null;

		public AutoCompleteListData(TurSolrInstance turSolrInstance) {
			super();
			this.stopWords = turSEStopword.getStopWords(turSolrInstance);
		}

		public List<String> getStopWords() {
			return stopWords;
		}

		public void setStopWords(List<String> stopWords) {
			this.stopWords = stopWords;
		}
	}
}
