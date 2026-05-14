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

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.commons.sn.bean.TurSNSearchLatestRequestBean;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSiteLocaleBean;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.commons.sn.search.TurSNParamType;
import com.viglet.turing.sn.TurSNSearchProcess;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshotService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/sn/{siteName}/search")
@Tag(name = "Semantic Navigation Search", description = "Semantic Navigation Search API")
public class TurSNSiteSearchAPI {
        private static final String NONE = "NONE";
        private final TurSNSiteSearchService turSNSiteSearchService;
        private final TurSNSearchProcess turSNSearchProcess;
        private final TurSNSiteSearchSnapshotService snapshotService;

        public TurSNSiteSearchAPI(TurSNSiteSearchService turSNSiteSearchService,
                        TurSNSearchProcess turSNSearchProcess,
                        TurSNSiteSearchSnapshotService snapshotService) {
                this.turSNSiteSearchService = turSNSiteSearchService;
                this.turSNSearchProcess = turSNSearchProcess;
                this.snapshotService = snapshotService;
        }

        @GetMapping(value = "list", produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<List<Object>> turSNSiteSearchSelectListGet(
                        @PathVariable String siteName,
                        @ModelAttribute TurSNSearchParams turSNSearchParams,
                        @RequestParam(required = false, name = TurSNParamType.FILTER_QUERY_OPERATOR, defaultValue = NONE) TurSNFilterQueryOperator fqOperator,
                        @RequestParam(required = false, name = TurSNParamType.FILTER_QUERY_ITEM_OPERATOR, defaultValue = NONE) TurSNFilterQueryOperator fqItemOperator,
                        @RequestParam(required = false, name = TurSNParamType.LOCALE) Locale locale,
                        HttpServletRequest request) {
                if (locale == null) {
                        locale = turSNSiteSearchService.resolveDefaultLocale(siteName);
                }
                turSNSiteSearchService.setSearchParams(turSNSearchParams,
                                readFilterQueries(request, TurSNParamType.FILTER_QUERIES_DEFAULT),
                                readFilterQueries(request, TurSNParamType.FILTER_QUERIES_AND),
                                readFilterQueries(request, TurSNParamType.FILTER_QUERIES_OR),
                                fqOperator, fqItemOperator, locale);
                if (!turSNSearchProcess.existsByTurSNSiteAndLanguage(siteName, turSNSearchParams.getLocale())) {
                        return turSNSiteSearchService.notFoundResponse();
                }
                List<Object> termList = turSNSearchProcess
                                .searchList(turSNSiteSearchService.getTurSNSiteSearchContext(turSNSearchParams,
                                                request, siteName));
                return new ResponseEntity<>(termList, HttpStatus.OK);
        }

        @GetMapping
        public ResponseEntity<TurSNSiteSearchBean> turSNSiteSearchSelectGet(
                        @PathVariable String siteName,
                        @ModelAttribute TurSNSearchParams turSNSearchParams,
                        @RequestParam(required = false, name = TurSNParamType.FILTER_QUERY_OPERATOR, defaultValue = NONE) TurSNFilterQueryOperator fqOperator,
                        @RequestParam(required = false, name = TurSNParamType.FILTER_QUERY_ITEM_OPERATOR, defaultValue = NONE) TurSNFilterQueryOperator fqItemOperator,
                        @RequestParam(required = false, name = TurSNParamType.LOCALE) Locale locale,
                        HttpServletRequest request) {
                if (locale == null) {
                        locale = turSNSiteSearchService.resolveDefaultLocale(siteName);
                }
                turSNSiteSearchService.setSearchParams(turSNSearchParams,
                                readFilterQueries(request, TurSNParamType.FILTER_QUERIES_DEFAULT),
                                readFilterQueries(request, TurSNParamType.FILTER_QUERIES_AND),
                                readFilterQueries(request, TurSNParamType.FILTER_QUERIES_OR),
                                fqOperator, fqItemOperator, locale);
                if (!turSNSearchProcess.existsByTurSNSiteAndLanguage(siteName, turSNSearchParams.getLocale())) {
                        return turSNSiteSearchService.notFoundResponse();
                }
                return turSNSiteSearchService.executeGetSearch(turSNSearchParams, request, siteName);
        }

        @PostMapping
        public ResponseEntity<TurSNSiteSearchBean> turSNSiteSearchSelectPost(
                        @PathVariable String siteName,
                        @ModelAttribute TurSNSearchParams turSNSearchParams,
                        @RequestParam(required = false, name = TurSNParamType.FILTER_QUERY_OPERATOR, defaultValue = NONE) TurSNFilterQueryOperator fqOperator,
                        @RequestParam(required = false, name = TurSNParamType.FILTER_QUERY_ITEM_OPERATOR, defaultValue = NONE) TurSNFilterQueryOperator fqItemOperator,
                        @RequestParam(required = false, name = TurSNParamType.LOCALE) Locale locale,
                        @RequestBody TurSNSitePostParamsBean turSNSitePostParamsBean,
                        Principal principal,
                        HttpServletRequest request) {
                if (principal == null) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                }
                Locale resolvedLocale = turSNSiteSearchService.determineLocale(turSNSitePostParamsBean, locale);
                if (resolvedLocale == null) {
                        resolvedLocale = turSNSiteSearchService.resolveDefaultLocale(siteName);
                }
                turSNSiteSearchService.setSearchParams(turSNSearchParams,
                                readFilterQueries(request, TurSNParamType.FILTER_QUERIES_DEFAULT),
                                readFilterQueries(request, TurSNParamType.FILTER_QUERIES_AND),
                                readFilterQueries(request, TurSNParamType.FILTER_QUERIES_OR),
                                fqOperator, fqItemOperator, resolvedLocale);
                if (!turSNSearchProcess.existsByTurSNSiteAndLanguage(siteName, turSNSearchParams.getLocale())) {
                        return turSNSiteSearchService.notFoundResponse();
                }
                return turSNSiteSearchService.executePostSearch(turSNSearchParams,
                                turSNSitePostParamsBean, request, siteName);
        }

        /**
         * Reads filter-query parameters straight from the request so a single
         * occurrence containing a comma (e.g. {@code fq[]=facet:1,2}) is kept
         * intact. Spring's {@code @RequestParam List<String>} returns the value
         * as a single {@code String} when there is only one match, which then
         * flows through {@code StringToCollectionConverter} and gets split on
         * commas — corrupting values that legitimately contain {@code ,}.
         */
        private static List<String> readFilterQueries(HttpServletRequest request, String name) {
                String[] values = request.getParameterValues(name);
                return values != null ? List.of(values) : null;
        }

        @GetMapping("locales")
        public List<TurSNSiteLocaleBean> turSNSiteSearchLocale(@PathVariable String siteName) {
                return snapshotService.getSnapshot(siteName, null).map(snap -> {
                        try {
                                return turSNSearchProcess.responseLocales(snap, new URI(
                                                String.format("/api/sn/%s/search", siteName)));
                        } catch (URISyntaxException e) {
                                log.error(e.getMessage(), e);
                        }
                        return TurSNSiteSearchService.getTurSNSiteLocaleBeans();
                }).orElse(Collections.emptyList());
        }

        /**
         * Returns the available sort options for a site: the three built-in options
         * (relevance, newest, oldest) plus any custom sorts configured for the site.
         *
         * @since 2026.1.14
         */
        @GetMapping("sort-options")
        public ResponseEntity<List<TurSNSiteSortOptionBean>> turSNSiteSearchSortOptions(
                        @PathVariable String siteName) {
                return snapshotService.getSnapshot(siteName, null)
                                .map(snap -> {
                                        List<TurSNSiteSortOptionBean> options = new ArrayList<>();
                                        options.add(new TurSNSiteSortOptionBean("relevance", "Relevance"));
                                        options.add(new TurSNSiteSortOptionBean("newest", "Newest"));
                                        options.add(new TurSNSiteSortOptionBean("oldest", "Oldest"));
                                        snap.customSorts()
                                                        .forEach(cs -> options.add(new TurSNSiteSortOptionBean(
                                                                        cs.getName(), cs.getDescription() != null
                                                                                        ? cs.getDescription()
                                                                                        : cs.getName())));
                                        return ResponseEntity.ok(options);
                                })
                                .orElse(ResponseEntity.notFound().build());
        }

        /**
         * Sort option DTO returned by the sort-options endpoint.
         *
         * @since 2026.1.14
         */
        public record TurSNSiteSortOptionBean(String value, String label) {}

        @PostMapping("latest")
        public ResponseEntity<List<String>> turSNSiteSearchLatestImpersonate(
                        @PathVariable String siteName,
                        @RequestParam(required = false, name = TurSNParamType.ROWS, defaultValue = "5") Integer rows,
                        @RequestParam(name = TurSNParamType.LOCALE) String locale,
                        @RequestBody Optional<TurSNSearchLatestRequestBean> turSNSearchLatestRequestBean,
                        Principal principal) {
                if (principal == null) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                }
                return new ResponseEntity<>(turSNSearchProcess.latestSearches(siteName, locale,
                                turSNSiteSearchService.isLatestImpersonate(turSNSearchLatestRequestBean, principal),
                                rows), HttpStatus.OK);
        }

}
