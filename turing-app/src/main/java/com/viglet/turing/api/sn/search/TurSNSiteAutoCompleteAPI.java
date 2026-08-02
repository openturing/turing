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
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.LocaleUtils;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSearchRequest;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.commons.sn.search.TurSNParamType;
import com.viglet.turing.sn.TurSNUtils;
import com.viglet.turing.sn.ac.TurSNAutoComplete;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/sn/{siteName}/ac")
@Tag(name = "Semantic Navigation Auto Complete", description = "Semantic Navigation Auto Complete API")
public class TurSNSiteAutoCompleteAPI {

        private static final int DEFAULT_ROWS = 20;

        private final TurSNAutoComplete turSNAutoComplete;
        private final TurSNSiteSearchService turSNSiteSearchService;

        public TurSNSiteAutoCompleteAPI(TurSNAutoComplete turSNAutoComplete,
                        TurSNSiteSearchService turSNSiteSearchService) {
                this.turSNAutoComplete = turSNAutoComplete;
                this.turSNSiteSearchService = turSNSiteSearchService;
        }

        @GetMapping
        public List<String> turSNSiteAutoComplete(@PathVariable String siteName,
                        TurSNSearchParams turSNSearchParams,
                        @RequestParam(required = false, name = TurSNParamType.FILTER_QUERIES_DEFAULT) List<String> filterQueriesDefault,
                        @RequestParam(required = false, name = TurSNParamType.FILTER_QUERIES_AND) List<String> filterQueriesAnd,
                        @RequestParam(required = false, name = TurSNParamType.FILTER_QUERIES_OR) List<String> filterQueriesOr,
                        @RequestParam(required = false, name = TurSNParamType.FILTER_QUERY_OPERATOR, defaultValue = "NONE") TurSNFilterQueryOperator fqOperator,
                        @RequestParam(required = false, name = TurSNParamType.FILTER_QUERY_ITEM_OPERATOR, defaultValue = "NONE") TurSNFilterQueryOperator fqItemOperator,
                        @RequestParam(required = false, name = TurSNParamType.LOCALE) String locale,
                        HttpServletRequest request) {
                turSNSearchParams.setFq(filterQueriesDefault);
                turSNSearchParams.setFqAnd(filterQueriesAnd);
                turSNSearchParams.setFqOr(filterQueriesOr);
                turSNSearchParams.setFqOp(fqOperator);
                turSNSearchParams.setFqiOp(fqItemOperator);
                turSNSearchParams.setLocale(LocaleUtils.toLocale(locale));
                if (turSNSearchParams.getRows() < 0) {
                        turSNSearchParams.setRows(DEFAULT_ROWS);
                }
                if (hasFilterQueries(turSNSearchParams)) {
                        return turSNAutoComplete.autoCompleteWithRegularSearch(siteName,
                                        turSNSearchParams, request);
                } else {
                        return turSNAutoComplete.autoComplete(siteName, turSNSearchParams.getQ(),
                                        LocaleUtils.toLocale(turSNSearchParams.getLocale()),
                                        turSNSearchParams.getRows());
                }
        }

        /**
         * T712 / §XLV — the POST-native autocomplete variant. It takes the same
         * anonymous JSON {@link TurSNSearchRequest} body as
         * {@code POST /api/sn/{siteName}/search/query}, so a suggestion request
         * narrowed by a filter value containing {@code & = + # %} or a space no
         * longer has to survive a query-string round-trip. The GET mapping is
         * untouched.
         *
         * <p>
         * The response is the same {@code List<String>} of suggestions — there are
         * no links in it, so {@code responseFormat} does not apply.
         * </p>
         *
         * @since 2026.3.4
         */
        @PostMapping(value = "query", consumes = MediaType.APPLICATION_JSON_VALUE,
                        produces = MediaType.APPLICATION_JSON_VALUE)
        public List<String> turSNSiteAutoCompleteQueryPost(@PathVariable String siteName,
                        @RequestBody TurSNSearchRequest searchRequest,
                        HttpServletRequest request) {
                Locale resolvedLocale = TurSNSearchRequestAdapter.resolveLocale(searchRequest);
                if (resolvedLocale == null) {
                        resolvedLocale = turSNSiteSearchService.resolveDefaultLocale(siteName);
                }
                TurSNSearchParams turSNSearchParams = TurSNSearchRequestAdapter
                                .toSearchParams(searchRequest, resolvedLocale);
                if (turSNSearchParams.getRows() < 0) {
                        turSNSearchParams.setRows(DEFAULT_ROWS);
                }
                if (hasFilterQueries(turSNSearchParams)) {
                        URI canonicalUri = TurSNSearchRequestAdapter.canonicalUri(
                                        TurSNUtils.requestToURI(request), searchRequest,
                                        resolvedLocale);
                        return turSNAutoComplete.autoCompleteWithRegularSearch(siteName,
                                        turSNSearchParams, canonicalUri,
                                        "%s_%s".formatted(siteName, canonicalUri.getRawQuery()));
                }
                return turSNAutoComplete.autoComplete(siteName, turSNSearchParams.getQ(),
                                resolvedLocale, turSNSearchParams.getRows());
        }

        private static boolean hasFilterQueries(TurSNSearchParams turSNSearchParams) {
                return !CollectionUtils.isEmpty(turSNSearchParams.getFq())
                                || !CollectionUtils.isEmpty(turSNSearchParams.getFqAnd())
                                || !CollectionUtils.isEmpty(turSNSearchParams.getFqOr());
        }

}
