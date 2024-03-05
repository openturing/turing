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

package com.viglet.turing.api.sn.search;

import com.google.inject.Inject;
import com.viglet.turing.commons.se.TurSEFilterQueryParameters;
import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.bean.TurSNSearchLatestRequestBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteLocaleBean;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.commons.sn.search.TurSNParamType;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.sn.TurSNSearchProcess;
import com.viglet.turing.sn.TurSNUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.LocaleUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/sn/{siteName}/search")
@Tag(name = "Semantic Navigation Search", description = "Semantic Navigation Search API")
public class TurSNSiteSearchAPI {
    private final TurSNSearchProcess turSNSearchProcess;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;

    @Inject
    public TurSNSiteSearchAPI(TurSNSearchProcess turSNSearchProcess,
                              TurSNSiteRepository turSNSiteRepository,
                              TurSNSiteLocaleRepository turSNSiteLocaleRepository) {
        this.turSNSearchProcess = turSNSearchProcess;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
    }

    @GetMapping
    public ResponseEntity<TurSNSiteSearchBean> turSNSiteSearchSelectGet(
            @PathVariable String siteName,
            @RequestParam(required = false, name = TurSNParamType.QUERY) String q,
            @RequestParam(required = false, name = TurSNParamType.PAGE) Integer currentPage,
            @RequestParam(required = false, name = TurSNParamType.FILTER_QUERIES_DEFAULT) List<String> filterQueriesDefault,
            @RequestParam(required = false, name = TurSNParamType.FILTER_QUERIES_AND) List<String> filterQueriesAnd,
            @RequestParam(required = false, name = TurSNParamType.FILTER_QUERIES_OR) List<String> filterQueriesOr,
            @RequestParam(required = false, name = TurSNParamType.FILTER_QUERY_OPERATOR, defaultValue = "NONE")
            TurSNFilterQueryOperator fqOperator,
            @RequestParam(required = false, name = TurSNParamType.SORT) String sort,
            @RequestParam(required = false, name = TurSNParamType.ROWS, defaultValue = "10") Integer rows,
            @RequestParam(required = false, name = TurSNParamType.GROUP) String group,
            @RequestParam(required = false, name = TurSNParamType.AUTO_CORRECTION_DISABLED, defaultValue = "0")
            Integer autoCorrectionDisabled,
            @RequestParam(required = false, name = TurSNParamType.LOCALE) String localeRequest,
            HttpServletRequest request) {
        Locale locale = LocaleUtils.toLocale(localeRequest);
        if (existsByTurSNSiteAndLanguage(siteName, locale)) {
            return new ResponseEntity<>(turSNSearchProcess.search(new TurSNSiteSearchContext(siteName,
                    new TurSEParameters(q, new TurSEFilterQueryParameters(filterQueriesDefault, filterQueriesAnd,
                            filterQueriesOr, fqOperator), currentPage, sort, rows, group, autoCorrectionDisabled), locale,
                    TurSNUtils.requestToURI(request))), HttpStatus.OK);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    private boolean existsByTurSNSiteAndLanguage(String siteName, Locale locale) {
        return turSNSiteRepository.findByName(siteName).map(turSNSite ->
                turSNSiteLocaleRepository.existsByTurSNSiteAndLanguage(turSNSite, locale)).orElse(false);
    }

    @PostMapping
    public ResponseEntity<TurSNSiteSearchBean> turSNSiteSearchSelectPost(
            @PathVariable String siteName,
            @RequestParam(required = false, name = TurSNParamType.QUERY) String q,
            @RequestParam(required = false, name = TurSNParamType.PAGE) Integer currentPage,
            @RequestParam(required = false, name = TurSNParamType.FILTER_QUERIES_DEFAULT) List<String> filterQueriesDefault,
            @RequestParam(required = false, name = TurSNParamType.FILTER_QUERIES_AND) List<String> filterQueriesAnd,
            @RequestParam(required = false, name = TurSNParamType.FILTER_QUERIES_OR) List<String> filterQueriesOr,
            @RequestParam(required = false, name = TurSNParamType.FILTER_QUERY_OPERATOR, defaultValue = "NONE")
            TurSNFilterQueryOperator fqOperator,
            @RequestParam(required = false, name = TurSNParamType.SORT) String sort,
            @RequestParam(required = false, name = TurSNParamType.ROWS, defaultValue = "10") Integer rows,
            @RequestParam(required = false, name = TurSNParamType.GROUP) String group,
            @RequestParam(required = false, name = TurSNParamType.AUTO_CORRECTION_DISABLED, defaultValue = "0")
            Integer autoCorrectionDisabled,
            @RequestParam(required = false, name = TurSNParamType.LOCALE) String localeRequest,
            @RequestBody TurSNSitePostParamsBean turSNSitePostParamsBean,
            Principal principal,
            HttpServletRequest request) {
        if (principal != null) {
            Locale locale = LocaleUtils.toLocale(localeRequest);
            if (existsByTurSNSiteAndLanguage(siteName, locale)) {
                turSNSitePostParamsBean.setTargetingRules(
                        turSNSearchProcess.requestTargetingRules(turSNSitePostParamsBean.getTargetingRules()));
                return new ResponseEntity<>(turSNSearchProcess.search(new TurSNSiteSearchContext(siteName,
                        new TurSEParameters(q, new TurSEFilterQueryParameters(filterQueriesDefault, filterQueriesAnd,
                                filterQueriesOr, fqOperator), currentPage, sort, rows, group, autoCorrectionDisabled), locale,
                        TurSNUtils.requestToURI(request), turSNSitePostParamsBean)), HttpStatus.OK);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @GetMapping("locales")
    public List<TurSNSiteLocaleBean> turSNSiteSearchLocale(@PathVariable String siteName) {
        return turSNSiteRepository.findByName(siteName).map(turSNSite -> {
            try {
                return turSNSearchProcess.responseLocales(turSNSite, new URI(String.format("/api/sn/%s/search", siteName)));
            } catch (URISyntaxException e) {
                log.error(e.getMessage(), e);
            }
            return new ArrayList<TurSNSiteLocaleBean>();
        }).orElse(Collections.emptyList());
    }

    @PostMapping("latest")
    public ResponseEntity<List<String>> turSNSiteSearchLatestImpersonate(
            @PathVariable String siteName,
            @RequestParam(required = false, name = TurSNParamType.ROWS, defaultValue = "5") Integer rows,
            @RequestParam(name = TurSNParamType.LOCALE) String locale,
            @RequestBody Optional<TurSNSearchLatestRequestBean> turSNSearchLatestRequestBean, Principal principal) {
        if (principal != null) {
            return new ResponseEntity<>(turSNSearchProcess.latestSearches(siteName, locale,
                    isLatestImpersonate(turSNSearchLatestRequestBean, principal),
                    rows), HttpStatus.OK);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    private String isLatestImpersonate(Optional<TurSNSearchLatestRequestBean> turSNSearchLatestRequestBean, Principal principal) {
        if (turSNSearchLatestRequestBean.isPresent() && turSNSearchLatestRequestBean.get().getUserId() != null) {
            return turSNSearchLatestRequestBean.get().getUserId();
        } else {
            return principal.getName();
        }
    }
}
