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
package com.viglet.turing.api.sn.search;

import java.util.Locale;

import org.apache.commons.lang3.LocaleUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.spellcheck.TurSNSiteSpellCheckBean;
import com.viglet.turing.commons.sn.search.TurSNParamType;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.sn.TurSNUtils;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Semantic Navigation spell-check endpoint. Resolves the site's search-engine
 * plugin ({@link TurSearchEnginePluginFactory}) and delegates to its
 * {@code spellCheck} seam, so "did you mean" works on any engine (Solr's
 * {@code /tur_spell} handler, Lucene's {@code DirectSpellChecker}, …) rather
 * than being hardwired to Solr (T686 / §XLI.2, Block AR).
 */
@RestController
@RequestMapping("/api/sn/{siteName}/{localeRequest}/spell-check")
@Tag(name = "Semantic Navigation Spell Check", description = "Semantic Navigation Spell Check API")
public class TurSNSiteSpellCheckAPI {
    private static final String RELEVANCE = "relevance";
    private final TurSearchEnginePluginFactory pluginFactory;
    private final TurSNSiteRepository turSNSiteRepository;

    public TurSNSiteSpellCheckAPI(TurSearchEnginePluginFactory pluginFactory,
            TurSNSiteRepository turSNSiteRepository) {
        this.pluginFactory = pluginFactory;
        this.turSNSiteRepository = turSNSiteRepository;
    }

    @GetMapping
    public TurSNSiteSpellCheckBean turSNSiteSpellCheck(@PathVariable String siteName,
            @PathVariable String localeRequest, @RequestParam(name = TurSNParamType.QUERY) String q,
            HttpServletRequest request) {
        Locale locale = LocaleUtils.toLocale(localeRequest);
        return turSNSiteRepository.findByNameIgnoreCase(siteName).map(turSNSite -> {
            TurSNSearchParams turSNSearchParams = new TurSNSearchParams();
            turSNSearchParams.setQ(q);
            turSNSearchParams.setRows(10);
            turSNSearchParams.setLocale(locale);
            turSNSearchParams.setP(1);
            turSNSearchParams.setSort(RELEVANCE);
            turSNSearchParams.setGroup(null);
            turSNSearchParams.setNfpr(0);
            TurSNConfig turSNConfig = new TurSNConfig();
            turSNConfig.setHlEnabled(false);
            TurSEParameters turSEParameters = new TurSEParameters(turSNSearchParams);
            TurSNSiteSearchContext turSNSiteSearchContext = new TurSNSiteSearchContext(siteName,
                    turSNConfig, turSEParameters, locale, TurSNUtils.requestToURI(request));
            return new TurSNSiteSpellCheckBean(turSNSiteSearchContext,
                    pluginFactory.getPluginForSite(turSNSite).spellCheck(siteName, q, locale));
        }).orElse(null);
    }
}
