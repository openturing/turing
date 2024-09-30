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

import com.google.inject.Inject;
import com.viglet.turing.se.TurSEStopWord;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import lombok.extern.slf4j.Slf4j;
import org.apache.solr.client.solrj.response.SpellCheckResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TurSNAutoComplete {
    private static final String SPACE_CHAR = " ";
    private static final boolean USE_BIGGER_TERMS = true;
    private static final boolean USE_TERMS_QUERY_EQUALS_AUTO_COMPLETE = true;
    private static final boolean USE_REPEAT_QUERY_TEXT_ON_AUTOCOMPLETE = false;
    private final TurSolr turSolr;
    private final TurSEStopWord turSEStopword;
    private final TurSolrInstanceProcess turSolrInstanceProcess;
    private boolean hasSpaceAtEnd = false;

    @Inject
    public TurSNAutoComplete(TurSolr turSolr, TurSEStopWord turSEStopword, TurSolrInstanceProcess turSolrInstanceProcess) {
        this.turSolr = turSolr;
        this.turSEStopword = turSEStopword;
        this.turSolrInstanceProcess = turSolrInstanceProcess;
    }

    public List<String> autoComplete(String siteName, String q, Locale locale, long rows) {
        // Only autocomplete if the query has more than one character
        if (q.length() > 1) {
            // Initialize Solr Instance
            return turSolrInstanceProcess.initSolrInstance(siteName, locale).map(instance -> {
                // Execute AutoComplete Solr API
                SpellCheckResponse turSEResults = executeAutoCompleteFromSE(instance, q);
                int numberOfWordsFromQuery = q.split(SPACE_CHAR).length;
                // Daria para inferir se há espaço no final da query se fizermos um split mantendo os delimitadores e vendo
                // se o tamanho do array é maior é par.
                if (q.endsWith(SPACE_CHAR)) {
                    numberOfWordsFromQuery++;
                }

                List<String> autoCompleteListFormatted = createFormattedList(turSEResults, instance,
                        numberOfWordsFromQuery);
//                List<String> autoCompleteListShrink = removeDuplicatedTerms(autoCompleteListFormatted,
//                        numberOfWordsFromQuery, q);
                return autoCompleteListFormatted.stream().limit(rows).toList();
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
        // if there are suggestions in the response.
        if (hasSuggestions(turSEResults)) {
            // autoCompleteList is the list of auto complete suggestions returned by Solr.
            List<String> autoCompleteList = turSEResults.getSuggestions().getFirst().getAlternatives();
            TurSNAutoCompleteListData autoCompleteListData = new TurSNAutoCompleteListData(turSEStopword.getStopWords(turSolrInstance));

            SuggestionFilter suggestionFilter = new SuggestionFilter(autoCompleteListData.getStopWords());
            suggestionFilter.automatonStrategyConfig(numberOfWordsFromQuery);
            autoCompleteListFormatted = suggestionFilter.filter(autoCompleteList);
            log.info("====================================");
            log.info("Original suggestions: {}", autoCompleteList);
            log.info("Filtered suggestions: {}", autoCompleteListFormatted);
        }
        return autoCompleteListFormatted;
    }


    /**
     * Processes a term and adds it to the auto-complete list if it meets the criteria.
     *
     * @param numberOfWordsFromQuery    the number of words from the query
     * @param autoCompleteListFormatted the list of formatted auto-complete items
     * @param autoCompleteListData      the data structure containing auto-complete list data
     * @param autoCompleteItem          the auto-complete item to be processed
     */
    private void processTerm(int numberOfWordsFromQuery, List<String> autoCompleteListFormatted,
                             TurSNAutoCompleteListData autoCompleteListData, String autoCompleteItem) {
        if (isAddTermToList(autoCompleteListData, numberOfWordsFromQuery, autoCompleteItem)) {
            autoCompleteListFormatted.add(autoCompleteItem);
        }
        log.info("Filtered autoComplete Item: {}", autoCompleteListFormatted);
    }

    /**
     * Determines whether a term should be added to the autocomplete list.
     *
     * @param autoCompleteListData   The data object containing the list of stop words.
     * @param numberOfWordsFromQuery The number of words in the query.
     * @param autoCompleteItem       The autocomplete item to be evaluated.
     * @return true if the term should be added to the autocomplete list, false otherwise.
     */
    private boolean isAddTermToList(TurSNAutoCompleteListData autoCompleteListData, int numberOfWordsFromQuery,
                                    String autoCompleteItem) {
        String[] autoCompleteItemTokens = autoCompleteItem.split(SPACE_CHAR);
        int numberOfWordsFromAutoCompleteItem = autoCompleteItemTokens.length;
        String autoCompleteItemFirstToken = autoCompleteItemTokens[0];
        String autoCompleteItemLastToken = autoCompleteItemTokens[autoCompleteItemTokens.length - 1];
        // Fiz a modificação aqui
        boolean numberOfWordsIsEquals = numberOfWordsFromQuery < numberOfWordsFromAutoCompleteItem;
        boolean firstWordIsStopWord = autoCompleteListData.getStopWords().contains(autoCompleteItemFirstToken);
        boolean lastWordIsStopWord = autoCompleteListData.getStopWords().contains(autoCompleteItemLastToken);

//		log.info("{} - Number Of Words From Query: {} - Number Of Words From AutoComplete Item: {}", numberOfWordsIsEquals, numberOfWordsFromQuery, numberOfWordsFromAutoCompleteItem);
//		log.info("First Word Is Stop Word: {} - Last Word Is Stop Word: {} - AutoComplete Item: {}", firstWordIsStopWord, lastWordIsStopWord, autoCompleteItem);

        // PARA IMPLEMENTAR ->
        // NUMBER OF WORDS FROM AUTOCOMPLETE TEM QUE SER MAIOR QUE NUMBER OF WORDS FROM QUERY

        // Conditions to add the term to the auto-complete list:
        // 1. The number of words in the query is equal to the number of words in the auto-complete item
        // 2. The first word of the auto-complete item is not a stop word.
        // 3. The last word of the auto-complete item is not a stop word.
        return (!USE_TERMS_QUERY_EQUALS_AUTO_COMPLETE || numberOfWordsIsEquals) && !firstWordIsStopWord && !lastWordIsStopWord;
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
                autoCompleteOnlyBiggerTerms.add(resultList.getFirst());
            }
        }

        return autoCompleteOnlyBiggerTerms;
    }


}
