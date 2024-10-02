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

@Slf4j
@Component
public class TurSNAutoComplete {
    private static final String SPACE_CHAR = " ";
    private final TurSolr turSolr;
    private final TurSEStopWord turSEStopword;
    private final TurSolrInstanceProcess turSolrInstanceProcess;

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
                // se o tamanho do array é par.
                if (q.endsWith(SPACE_CHAR)) {
                    numberOfWordsFromQuery++;
                }

                List<String> autoCompleteListFormatted = createFormattedList(turSEResults, instance,
                        numberOfWordsFromQuery);
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
        }
        return autoCompleteListFormatted;
    }

    private boolean hasSuggestions(SpellCheckResponse turSEResults) {
        return turSEResults != null && turSEResults.getSuggestions() != null
                && !turSEResults.getSuggestions().isEmpty();
    }

}
