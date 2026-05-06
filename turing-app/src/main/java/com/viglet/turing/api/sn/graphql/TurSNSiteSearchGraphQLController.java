/*
 * Copyright (C) 2016-2025 the original author or authors.
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

package com.viglet.turing.api.sn.graphql;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.util.CollectionUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchGroupBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchResultsBean;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.domain.sn.TurSNSiteDomain;
import com.viglet.turing.domain.sn.TurSNSiteFieldExtDomain;
import com.viglet.turing.domain.sn.TurSNSiteFieldExtRepositoryPort;
import com.viglet.turing.domain.sn.TurSNSiteLocaleRepositoryPort;
import com.viglet.turing.domain.sn.TurSNSiteRepositoryPort;
import com.viglet.turing.sn.TurSNSearchProcess;
import com.viglet.turing.sn.TurSNUtils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * GraphQL Controller for Semantic Navigation Search API.
 * 
 * @author Alexandre Oliveira
 * @since 2025.03
 */
@Slf4j
@Controller
public class TurSNSiteSearchGraphQLController {

    private final TurSNSearchProcess turSNSearchProcess;
    private final TurSNSiteRepositoryPort turSNSiteRepositoryPort;
    private final TurSNSiteFieldExtRepositoryPort turSNSiteFieldExtRepositoryPort;
    private final TurSNSiteLocaleRepositoryPort turSNSiteLocaleRepositoryPort;

    public TurSNSiteSearchGraphQLController(TurSNSearchProcess turSNSearchProcess,
            TurSNSiteRepositoryPort turSNSiteRepositoryPort,
            TurSNSiteFieldExtRepositoryPort turSNSiteFieldExtRepositoryPort,
            TurSNSiteLocaleRepositoryPort turSNSiteLocaleRepositoryPort) {
        this.turSNSearchProcess = turSNSearchProcess;
        this.turSNSiteRepositoryPort = turSNSiteRepositoryPort;
        this.turSNSiteFieldExtRepositoryPort = turSNSiteFieldExtRepositoryPort;
        this.turSNSiteLocaleRepositoryPort = turSNSiteLocaleRepositoryPort;
    }

    @QueryMapping
    public TurSNSiteSearchBean siteSearch(@Argument String siteName,
            @Argument TurSNSearchParamsInput searchParams,
            @Argument String locale) {

        String resolvedSiteName = resolveSiteName(siteName);

        log.debug("GraphQL siteSearch called with siteName: {}, params: {}, locale: {}",
                resolvedSiteName, searchParams, locale);

        // Convert GraphQL input to internal search params
        TurSNSearchParams turSNSearchParams = convertToTurSNSearchParams(searchParams, locale);

        // Get locale, prioritizing the direct locale parameter over searchParams locale
        String localeToUse = getLocale(locale, turSNSearchParams, resolvedSiteName);

        Locale localeObj = LocaleUtils.toLocale(localeToUse);

        // Check if site exists and has the specified language
        if (!turSNSearchProcess.existsByTurSNSiteAndLanguage(resolvedSiteName, localeObj)) {
            log.warn("Site {} (resolved from {}) with locale {} not found or not supported",
                    resolvedSiteName, siteName, localeToUse);
            return new TurSNSiteSearchBean();
        }

        return turSNSiteRepositoryPort.findByNameIgnoreCase(resolvedSiteName)
                .map(site -> {
                    // Get the current HTTP request to create proper search context
                    ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder
                            .getRequestAttributes();

                    HttpServletRequest request;
                    if (requestAttributes != null) {
                        request = requestAttributes.getRequest();
                    } else {
                        // Create a mock request for GraphQL context
                        request = createMockRequestForGraphQL(turSNSearchParams);
                    }

                    TurSNSiteSearchContext turSNSiteSearchContext = getTurSNSiteSearchContext(
                            resolvedSiteName, turSNSearchParams, request, site);

                    TurSNSiteSearchBean searchResponse = turSNSearchProcess.search(turSNSiteSearchContext);
                    enrichDynamicFieldsBySite(searchResponse, site, turSNSearchParams.getFl());
                    return searchResponse;
                })
                .orElse(new TurSNSiteSearchBean());
    }

    @QueryMapping
    public List<String> siteNames() {
        return turSNSiteRepositoryPort.findAllNamesOrderedByNameIgnoreCase();
    }

    private String resolveSiteName(String siteName) {
        if (StringUtils.isBlank(siteName)) {
            return siteName;
        }
        if (turSNSiteRepositoryPort.findByNameIgnoreCase(siteName).isPresent()) {
            return siteName;
        }

        List<String> siteNames = turSNSiteRepositoryPort.findAllNamesOrderedByNameIgnoreCase();
        Map<String, String> enumToSiteName = TurSNSiteGraphQLNameUtils
                .buildEnumToSiteNameMap(siteNames);
        String resolved = TurSNSiteGraphQLNameUtils.resolveGraphQLSiteArgument(siteName,
                enumToSiteName);
        if (!Objects.equals(resolved, siteName)) {
            return resolved;
        }

        String hyphenCandidate = siteName.toLowerCase(Locale.ROOT).replace('_', '-');
        if (turSNSiteRepositoryPort.findByNameIgnoreCase(hyphenCandidate).isPresent()) {
            return hyphenCandidate;
        }

        return siteNames.stream()
                .filter(candidate -> TurSNSiteGraphQLNameUtils
                        .toGraphQLEnumValue(candidate)
                        .equalsIgnoreCase(siteName))
                .findFirst()
                .orElse(siteName);
    }

    private void enrichDynamicFieldsBySite(TurSNSiteSearchBean searchResponse, TurSNSiteDomain site,
            List<String> selectedFields) {
        if (searchResponse == null || site == null) {
            return;
        }

        List<TurSNSiteFieldExtDomain> siteFieldExtList = turSNSiteFieldExtRepositoryPort
                .findBySnSiteId(site.id());

        LinkedHashSet<String> fieldNames = new LinkedHashSet<>();
        Map<String, String> externalIdToName = new LinkedHashMap<>();
        siteFieldExtList.forEach(siteFieldExt -> {
            if (StringUtils.isNotBlank(siteFieldExt.name())) {
                String graphQLName = TurSNSiteGraphQLNameUtils
                        .toGraphQLFieldName(siteFieldExt.name());
                fieldNames.add(graphQLName);
            }
            if (StringUtils.isNotBlank(siteFieldExt.externalId())
                    && StringUtils.isNotBlank(siteFieldExt.name())) {
                String graphQLName = TurSNSiteGraphQLNameUtils
                        .toGraphQLFieldName(siteFieldExt.name());
                externalIdToName.putIfAbsent(siteFieldExt.externalId(), graphQLName);
            }
        });

        LinkedHashSet<String> selectedFieldNames = resolveSelectedFieldNames(selectedFields,
                fieldNames, externalIdToName);

        normalizeResultsDocuments(searchResponse.getResults(), fieldNames, externalIdToName,
                selectedFieldNames);
        if (!CollectionUtils.isEmpty(searchResponse.getGroups())) {
            searchResponse.getGroups().stream().map(TurSNSiteSearchGroupBean::getResults)
                    .forEach(groupResults -> normalizeResultsDocuments(groupResults,
                            fieldNames, externalIdToName, selectedFieldNames));
        }
    }

    private LinkedHashSet<String> resolveSelectedFieldNames(List<String> selectedFields,
            LinkedHashSet<String> fieldNames,
            Map<String, String> externalIdToName) {
        if (CollectionUtils.isEmpty(selectedFields)) {
            return new LinkedHashSet<>();
        }

        LinkedHashSet<String> selectedFieldNames = new LinkedHashSet<>();
        selectedFields.stream().filter(StringUtils::isNotBlank).map(String::trim)
                .forEach(field -> {
                    if (fieldNames.contains(field)) {
                        selectedFieldNames.add(field);
                    } else if (externalIdToName.containsKey(field)) {
                        selectedFieldNames.add(externalIdToName.get(field));
                    } else {
                        selectedFieldNames.add(field);
                    }
                });
        return selectedFieldNames;
    }

    private void normalizeResultsDocuments(TurSNSiteSearchResultsBean results,
            LinkedHashSet<String> fieldNames,
            Map<String, String> externalIdToName,
            LinkedHashSet<String> selectedFieldNames) {
        if (results == null || CollectionUtils.isEmpty(results.getDocument())) {
            return;
        }

        results.getDocument().forEach(document -> document
                .setFields(normalizeDocumentFields(document, fieldNames, externalIdToName,
                        selectedFieldNames)));
    }

    private Map<String, Object> normalizeDocumentFields(TurSNSiteSearchDocumentBean document,
            LinkedHashSet<String> fieldNames,
            Map<String, String> externalIdToName,
            LinkedHashSet<String> selectedFieldNames) {
        Map<String, Object> normalizedFields = new LinkedHashMap<>();
        Set<String> baseFields = CollectionUtils.isEmpty(selectedFieldNames)
                ? fieldNames
                : selectedFieldNames;
        baseFields.forEach(fieldName -> normalizedFields.put(fieldName, null));

        if (document == null || CollectionUtils.isEmpty(document.getFields())) {
            return normalizedFields;
        }

        document.getFields().forEach((rawKey, value) -> {
            if (StringUtils.isBlank(rawKey)) {
                return;
            }

            String fieldName = resolveFieldName(rawKey, fieldNames, externalIdToName);
            if (!CollectionUtils.isEmpty(selectedFieldNames)
                    && !selectedFieldNames.contains(fieldName)) {
                return;
            }
            normalizedFields.put(fieldName, value);
        });
        return normalizedFields;
    }

    private String resolveFieldName(String rawKey,
            LinkedHashSet<String> fieldNames,
            Map<String, String> externalIdToName) {
        if (fieldNames.contains(rawKey)) {
            return rawKey;
        }
        if (externalIdToName.containsKey(rawKey)) {
            return externalIdToName.get(rawKey);
        }
        return TurSNSiteGraphQLNameUtils.toGraphQLFieldName(rawKey);
    }

    private @NonNull String getLocale(String locale, TurSNSearchParams turSNSearchParams,
            String siteName) {
        String localeToUse = null;
        if (StringUtils.isNotBlank(locale)) {
            localeToUse = locale;
        } else if (turSNSearchParams.getLocale() != null) {
            localeToUse = turSNSearchParams.getLocale().toString();
        }
        if (StringUtils.isBlank(localeToUse)) {
            localeToUse = turSNSiteRepositoryPort.findByNameIgnoreCase(siteName)
                    .flatMap(site -> turSNSiteLocaleRepositoryPort
                            .findFirstLocaleBySnSiteIdOrderByPosition(site.id()))
                    .map(siteLocale -> siteLocale.language().toString())
                    .orElse("en");
        }
        return localeToUse;
    }

    private TurSNSearchParams convertToTurSNSearchParams(TurSNSearchParamsInput input, String locale) {
        TurSNSearchParams params = new TurSNSearchParams();

        if (input == null) {
            return params;
        }
        params.setQ(StringUtils.defaultIfBlank(input.getQ(), "*"));
        params.setP(input.getP() != null ? input.getP() : 1);
        params.setRows(input.getRows() != null ? input.getRows() : -1);
        params.setSort(StringUtils.defaultIfBlank(input.getSort(), "relevance"));
        params.setGroup(input.getGroup());
        params.setNfpr(input.getNfpr() != null ? input.getNfpr() : 1);
        params.setFq(input.getFq());
        params.setFqAnd(input.getFqAnd());
        params.setFqOr(input.getFqOr());
        params.setFl(input.getFl());

        setFilterQueryOperator(input, params);
        setFilterQueryItemOperator(input, params);

        params.setLocale(LocaleUtils.toLocale(getInputLocale(input, locale)));

        return params;
    }

    private static String getInputLocale(TurSNSearchParamsInput input, String locale) {
        return StringUtils.isNotBlank(input.getLocale()) ? input.getLocale() : locale;
    }

    private static void setFilterQueryItemOperator(TurSNSearchParamsInput input, TurSNSearchParams params) {
        if (StringUtils.isNotBlank(input.getFqiOp())) {
            try {
                params.setFqiOp(TurSNFilterQueryOperator.valueOf(input.getFqiOp().toUpperCase()));
            } catch (IllegalArgumentException e) {
                params.setFqiOp(TurSNFilterQueryOperator.NONE);
            }
        }
    }

    private static void setFilterQueryOperator(TurSNSearchParamsInput input, TurSNSearchParams params) {
        if (StringUtils.isNotBlank(input.getFqOp())) {
            try {
                params.setFqOp(TurSNFilterQueryOperator.valueOf(input.getFqOp().toUpperCase()));
            } catch (IllegalArgumentException e) {
                params.setFqOp(TurSNFilterQueryOperator.NONE);
            }
        }
    }

    private HttpServletRequest createMockRequestForGraphQL(TurSNSearchParams params) {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setMethod("POST");
        mockRequest.setRequestURI("/graphql");
        mockRequest.setContextPath("/");
        mockRequest.setServerName("localhost");
        mockRequest.setServerPort(2700);

        // Add search parameters as query parameters for URI construction
        if (params != null) {
            if (StringUtils.isNotBlank(params.getQ())) {
                mockRequest.addParameter("q", params.getQ());
            }
            if (params.getP() != null) {
                mockRequest.addParameter("p", params.getP().toString());
            }
            if (params.getRows() != null) {
                mockRequest.addParameter("rows", params.getRows().toString());
            }
            if (StringUtils.isNotBlank(params.getSort())) {
                mockRequest.addParameter("sort", params.getSort());
            }
            if (params.getLocale() != null) {
                mockRequest.addParameter("locale", params.getLocale().toString());
            }
        }

        return mockRequest;
    }

    private TurSNSiteSearchContext getTurSNSiteSearchContext(String siteName,
            TurSNSearchParams turSNSearchParams, HttpServletRequest request, TurSNSiteDomain site) {

        TurSNConfig turSNConfig = getTurSNConfig(site);
        return TurSNUtils.getTurSNSiteSearchContext(turSNConfig, siteName,
                turSNSearchParams, request);
    }

    private TurSNConfig getTurSNConfig(TurSNSiteDomain turSNSite) {
        TurSNConfig turSNConfig = new TurSNConfig();
        turSNConfig.setHlEnabled(turSNSite.hl() != null && turSNSite.hl() == 1
                && hasSiteHLFields(turSNSite));
        return turSNConfig;
    }

    private boolean hasSiteHLFields(TurSNSiteDomain turSNSite) {
        return turSNSiteFieldExtRepositoryPort
                .existsBySnSiteIdAndHlAndEnabled(turSNSite.id(), 1, 1);
    }
}