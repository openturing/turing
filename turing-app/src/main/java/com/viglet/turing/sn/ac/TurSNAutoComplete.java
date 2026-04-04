/*
 * Copyright (C) 2016-2022 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.sn.ac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import com.viglet.turing.api.sn.search.TurSNSiteSearchCachedAPI;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchResultsBean;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.se.TurSEStopWord;
import com.viglet.turing.sn.TurSNUtils;
import com.viglet.turing.solr.TurSolrInstanceProcess;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TurSNAutoComplete {
    private static final String SPACE_CHAR = " ";
    private final TurSEStopWord turSEStopword;
    private final TurSNSiteSearchCachedAPI turSNSiteSearchCachedAPI;
    private final TurSearchEnginePluginFactory pluginFactory;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSolrInstanceProcess turSolrInstanceProcess;

    public TurSNAutoComplete(TurSEStopWord turSEStopword,
            TurSNSiteSearchCachedAPI turSNSiteSearchCachedAPI,
            TurSearchEnginePluginFactory pluginFactory,
            TurSNSiteRepository turSNSiteRepository,
            TurSolrInstanceProcess turSolrInstanceProcess) {
        this.turSEStopword = turSEStopword;
        this.turSNSiteSearchCachedAPI = turSNSiteSearchCachedAPI;
        this.pluginFactory = pluginFactory;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSolrInstanceProcess = turSolrInstanceProcess;
    }

    @NotNull
    public List<String> autoCompleteWithRegularSearch(String siteName,
            TurSNSearchParams turSNSearchParams, HttpServletRequest request) {
        TurSNConfig turSNConfig = new TurSNConfig();
        turSNConfig.setHlEnabled(false);
        turSNSearchParams
                .setQ(String.format("%s:%s*", TurSNFieldName.TITLE, turSNSearchParams.getQ()));
        turSNSearchParams.setGroup(null);
        turSNSearchParams.setNfpr(0);
        turSNSearchParams.setP(1);
        return Optional
                .ofNullable(turSNSiteSearchCachedAPI.searchCached(
                        TurSNUtils.getCacheKey(siteName, request),
                        TurSNUtils.getTurSNSiteSearchContext(turSNConfig, siteName, turSNSearchParams, request)))
                .map(TurSNSiteSearchBean::getResults).map(TurSNSiteSearchResultsBean::getDocument)
                .map(documents -> {
                    List<String> termList = new ArrayList<>();
                    documents.forEach(document -> Optional.ofNullable(document.getFields())
                            .filter(fields -> fields.containsKey(TurSNFieldName.TITLE))
                            .map(fields -> termList
                                    .add(fields.get(TurSNFieldName.TITLE).toString())));
                    return termList;
                }).orElse(Collections.emptyList());
    }

    public List<String> autoComplete(String siteName, String q, Locale locale, long rows) {
        if (q.length() <= 1) {
            return Collections.emptyList();
        }
        return turSNSiteRepository.findByNameIgnoreCase(siteName).map(turSNSite -> {
            var plugin = pluginFactory.getPluginForSite(turSNSite);
            List<String> rawSuggestions = plugin.autoComplete(siteName, q, locale, rows);

            int numberOfWordsFromQuery = q.split(SPACE_CHAR).length;
            if (q.endsWith(SPACE_CHAR)) {
                numberOfWordsFromQuery++;
            }

            List<String> stopWords = turSolrInstanceProcess.initSolrInstance(siteName, locale)
                    .map(turSEStopword::getStopWords)
                    .orElse(Collections.emptyList());
            TurSNSuggestionFilter suggestionFilter = new TurSNSuggestionFilter(stopWords);
            suggestionFilter.automatonStrategyConfig(numberOfWordsFromQuery);
            List<String> filtered = suggestionFilter.filter(rawSuggestions);

            return filtered.stream().limit(rows >= 0 ? rows : 0).sorted().toList();
        }).orElse(Collections.emptyList());
    }
}
