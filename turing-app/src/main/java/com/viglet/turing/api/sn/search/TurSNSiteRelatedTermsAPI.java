/*
 * Copyright (C) 2016-2026 the original author or authors.
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

import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.LocaleUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.commons.sn.search.TurSNParamType;
import com.viglet.turing.persistence.dto.kb.TurRelatedTermSuggestionDto;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.sn.kb.TurRelatedTermService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T678 / §XL (Block AQ) — query-time "related concepts" suggestions, a sibling of
 * the Solr spell-check endpoint. Given a query, it returns the controlled-vocabulary
 * terms recognised in it and their {@code RELATED} (RT) neighbours, so a search UI
 * can offer "you may also be interested in" chips. Empty for an opted-out site or a
 * query that mentions no thesaurus term.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/sn/{siteName}/{localeRequest}/related-terms")
@Tag(name = "Semantic Navigation Related Terms",
        description = "Controlled-vocabulary related-concept suggestions (microthesaurus RT links)")
public class TurSNSiteRelatedTermsAPI {

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurRelatedTermService relatedTermService;

    public TurSNSiteRelatedTermsAPI(TurSNSiteRepository turSNSiteRepository,
            TurRelatedTermService relatedTermService) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.relatedTermService = relatedTermService;
    }

    @GetMapping
    @Operation(summary = "Related-concept suggestions for a query (microthesaurus RT links)")
    public List<TurRelatedTermSuggestionDto> relatedTerms(@PathVariable String siteName,
            @PathVariable String localeRequest,
            @RequestParam(name = TurSNParamType.QUERY) String q) {
        Locale locale = LocaleUtils.toLocale(localeRequest);
        return turSNSiteRepository.findByName(siteName)
                .map(site -> relatedTermService.suggest(site, locale, q))
                .orElseGet(List::of);
    }
}
