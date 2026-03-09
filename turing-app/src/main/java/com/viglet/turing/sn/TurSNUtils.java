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
package com.viglet.turing.sn;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.util.ForwardedHeaderUtils;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentMetadataBean;
import com.viglet.turing.commons.sn.search.TurSNParamType;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.solr.TurSolrField;

import jakarta.servlet.http.HttpServletRequest;

public class TurSNUtils {
    public static final String TURING_ENTITY = "turing_entity";
    public static final String DEFAULT_LANGUAGE = "en";
    public static final String URL = "url";

    private TurSNUtils() {
        throw new IllegalStateException("SN Utility class");
    }

    public static boolean isTrue(Integer value) {
        return Integer.valueOf(1).equals(value);
    }

    @NotNull
    public static String getCacheKey(String siteName, HttpServletRequest request) {
        return "%s_%s".formatted(siteName, request.getQueryString());
    }

    @NotNull
    public static TurSNSiteSearchContext getTurSNSiteSearchContext(TurSNConfig turSNConfig,
            String siteName, TurSNSearchParams turSNSearchParams, HttpServletRequest request) {
        return getTurSNSiteSearchContext(turSNConfig, siteName, turSNSearchParams, new TurSNSitePostParamsBean(),
                request);
    }

    @NotNull
    public static TurSNSiteSearchContext getTurSNSiteSearchContext(TurSNConfig turSNConfig,
            String siteName, TurSNSearchParams turSNSearchParams,
            TurSNSitePostParamsBean turSNSitePostParamsBean, HttpServletRequest request) {
        return new TurSNSiteSearchContext(siteName, turSNConfig,
                new TurSEParameters(turSNSearchParams, turSNSitePostParamsBean), turSNSearchParams.getLocale(),
                TurSNUtils.requestToURI(request), turSNSitePostParamsBean);
    }

    public static boolean hasCorrectedText(TurSESpellCheckResult turSESpellCheckResult) {
        return turSESpellCheckResult.isCorrected()
                && StringUtils.hasText(turSESpellCheckResult.getCorrectedText());
    }

    public static boolean isAutoCorrectionEnabled(TurSNSiteSearchContext context,
            TurSNSite turSNSite) {
        return context.getTurSEParameters().getAutoCorrectionDisabled() != 1
                && context.getTurSEParameters().getCurrentPage() == 1
                && turSNSite.getSpellCheck() == 1 && turSNSite.getSpellCheckFixes() == 1;
    }

    public static URI requestToURI(HttpServletRequest request) {
        ServletServerHttpRequest servletServerHttpRequest = new ServletServerHttpRequest(request);
        return ForwardedHeaderUtils.adaptFromForwardedHeaders(servletServerHttpRequest.getURI(),
                servletServerHttpRequest.getHeaders()).build().toUri();
    }

    public static URI addFilterQuery(URI uri, String fq) {
        List<NameValuePair> params = new URIBuilder(uri).getQueryParams();
        StringBuilder sbQueryString = new StringBuilder();
        AtomicBoolean alreadyExists = new AtomicBoolean(false);
        params.stream().filter(nameValuePair -> nameValuePair.getValue() != null)
                .forEach(nameValuePair -> {
                    String decodedValue = URLDecoder.decode(nameValuePair.getValue(), StandardCharsets.UTF_8);
                    if (nameValuePair.getName().equals(TurSNParamType.FILTER_QUERIES_DEFAULT)
                            && decodedValue.equals(fq)) {
                        alreadyExists.set(true);
                    }
                    resetPaginationOrAddParameter(sbQueryString, nameValuePair.getName(),
                            decodedValue);
                });
        if (!alreadyExists.get()) {
            TurCommonsUtils.addParameterToQueryString(sbQueryString,
                    TurSNParamType.FILTER_QUERIES_DEFAULT, fq);
        }
        return TurCommonsUtils.modifiedURI(uri, sbQueryString);
    }

    public static URI removeFilterQuery(URI uri, String fq) {
        List<NameValuePair> params = new URIBuilder(uri).getQueryParams();
        StringBuilder sbQueryString = new StringBuilder();
        params.stream().filter(nameValuePair -> nameValuePair.getValue() != null)
                .forEach(nameValuePair -> {
                    String decodedValue = URLDecoder.decode(nameValuePair.getValue(), StandardCharsets.UTF_8);
                    if (!(decodedValue.equals(fq) && nameValuePair.getName()
                            .equals(TurSNParamType.FILTER_QUERIES_DEFAULT))) {
                        resetPaginationOrAddParameter(sbQueryString, nameValuePair.getName(),
                                decodedValue);
                    }
                });
        return TurCommonsUtils.modifiedURI(uri, sbQueryString);
    }

    public static URI removeFilterQueryByFieldNames(URI uri, List<String> fieldNames) {
        List<NameValuePair> params = new URIBuilder(uri).getQueryParams();
        StringBuilder sbQueryString = new StringBuilder();
        params.forEach(nameValuePair -> {
            if (nameValuePair.getName().equals(TurSNParamType.FILTER_QUERIES_DEFAULT)) {
                TurCommonsUtils.getKeyValueFromColon(nameValuePair.getValue()).ifPresent(kv -> {
                    if (!(fieldNames
                            .contains(URLDecoder.decode(kv.getKey(), StandardCharsets.UTF_8)))) {
                        TurCommonsUtils.addParameterToQueryString(sbQueryString,
                                nameValuePair.getName(), nameValuePair.getValue());
                    }
                });
            } else {
                TurCommonsUtils.addParameterToQueryString(sbQueryString, nameValuePair.getName(),
                        nameValuePair.getValue());
            }
        });
        return TurCommonsUtils.modifiedURI(uri, sbQueryString);
    }

    public static List<String> filterQueryByFieldNames(URI uri, List<String> fieldNames) {
        List<String> filterQueries = new ArrayList<>();
        List<NameValuePair> params = new URIBuilder(uri).getQueryParams();
        params.forEach(nameValuePair -> {
            if (nameValuePair.getName().equals(TurSNParamType.FILTER_QUERIES_DEFAULT)) {
                TurCommonsUtils.getKeyValueFromColon(nameValuePair.getValue()).ifPresent(kv -> {
                    if ((fieldNames
                            .contains(URLDecoder.decode(kv.getKey(), StandardCharsets.UTF_8)))) {
                        filterQueries.add(nameValuePair.getValue());
                    }
                });
            }
        });
        return filterQueries;
    }

    public static URI removeFilterQueryByFieldName(URI uri, String fieldName) {
        return removeFilterQueryByFieldNames(uri, Collections.singletonList(fieldName));
    }

    public static List<String> filterQueryByFieldName(URI uri, String fieldName) {
        return filterQueryByFieldNames(uri, Collections.singletonList(fieldName));
    }

    public static URI removeQueryStringParameter(URI uri, String field) {
        List<NameValuePair> params = new URIBuilder(uri).getQueryParams();
        StringBuilder sbQueryString = new StringBuilder();
        params.stream().filter(nameValuePair -> !(nameValuePair.getName().equals(field)))
                .forEach(nameValuePair -> TurCommonsUtils.addParameterToQueryString(sbQueryString,
                        nameValuePair.getName(), nameValuePair.getValue()));
        return TurCommonsUtils.modifiedURI(uri, sbQueryString);
    }

    private static void resetPaginationOrAddParameter(StringBuilder sbQueryString, String paramName,
            String paramValue) {
        TurCommonsUtils.addParameterToQueryString(sbQueryString, paramName,
                paramName.equals(TurSNParamType.PAGE) ? "1" : paramValue);
    }

    public static void addSNDocument(URI uri, Map<String, TurSNSiteFieldExtDto> fieldExtMap,
            Map<String, TurSNSiteFieldExtDto> facetMap,
            List<TurSNSiteSearchDocumentBean> turSNSiteSearchDocumentsBean, TurSEResult result,
            boolean isElevate) {
        addSNDocumentWithPosition(uri, fieldExtMap, facetMap, turSNSiteSearchDocumentsBean, result,
                isElevate, null);
    }

    public static void addSNDocumentWithPosition(URI uri,
            Map<String, TurSNSiteFieldExtDto> fieldExtMap,
            Map<String, TurSNSiteFieldExtDto> facetMap,
            List<TurSNSiteSearchDocumentBean> turSNSiteSearchDocumentsBean, TurSEResult result,
            boolean isElevate, Integer position) {
        TurSNSiteSearchDocumentBean documentBean = getDocumentFields(uri, fieldExtMap, facetMap, isElevate, result);
        if (position == null) {
            turSNSiteSearchDocumentsBean.add(documentBean);
        } else {
            turSNSiteSearchDocumentsBean.add(position, documentBean);
        }
    }

    private static TurSNSiteSearchDocumentBean getDocumentFields(URI uri,
            Map<String, TurSNSiteFieldExtDto> fieldExtMap,
            Map<String, TurSNSiteFieldExtDto> facetMap, boolean isElevate, TurSEResult result) {
        return Optional.ofNullable(result.getFields())
                .map(turSEResultAttr -> TurSNSiteSearchDocumentBean.builder().elevate(isElevate)
                        .metadata(addMetadataFromDocument(uri, facetMap, turSEResultAttr))
                        .source(getSourceFromDocument(turSEResultAttr))
                        .fields(addFieldsFromDocument(fieldExtMap, turSEResultAttr,
                                turSEResultAttr.keySet()))
                        .build())
                .orElse(TurSNSiteSearchDocumentBean.builder().build());
    }

    private static Map<String, Object> addFieldsFromDocument(
            Map<String, TurSNSiteFieldExtDto> fieldExtMap, Map<String, Object> turSEResultAttr,
            Set<String> attributes) {
        Map<String, Object> fields = new HashMap<>();
        attributes.forEach(attribute -> {
            if (!attribute.startsWith(TURING_ENTITY)) {
                addFieldAndValueToMap(turSEResultAttr, fields, attribute,
                        getFieldName(fieldExtMap, attribute));
            }
        });
        return fields;
    }

    private static String getFieldName(Map<String, TurSNSiteFieldExtDto> fieldExtMap,
            String attribute) {
        return fieldExtMap.containsKey(attribute) ? fieldExtMap.get(attribute).getName()
                : attribute;
    }

    private static void addFieldAndValueToMap(Map<String, Object> turSEResultAttr,
            Map<String, Object> fields, String attribute, String nodeName) {
        if (nodeName != null && fields.containsKey(nodeName)) {
            addValueToExistingFieldMap(turSEResultAttr, fields, attribute, nodeName);
        } else {
            fields.put(nodeName, turSEResultAttr.get(attribute));

        }
    }

    private static void addValueToExistingFieldMap(Map<String, Object> turSEResultAttr,
            Map<String, Object> fields,
            String attribute,
            String nodeName) {
        Object existingValue = fields.get(nodeName);
        Object newValue = turSEResultAttr.get(attribute);

        if (existingValue instanceof List<?> listValue) {
            List<Object> mutableList = new ArrayList<>(listValue);
            mutableList.add(newValue);
            fields.put(nodeName, mutableList);
        } else {
            List<Object> newList = new ArrayList<>();
            if (existingValue != null)
                newList.add(existingValue);
            newList.add(newValue);
            fields.put(nodeName, newList);
        }
    }

    private static String getSourceFromDocument(Map<String, Object> turSEResultAttr) {
        if (turSEResultAttr.containsKey(URL)) {
            return (String) turSEResultAttr.get(URL);
        } else {
            return null;
        }
    }

    private static List<TurSNSiteSearchDocumentMetadataBean> addMetadataFromDocument(URI uri,
            Map<String, TurSNSiteFieldExtDto> facetMap, Map<String, Object> turSEResultAttr) {
        List<TurSNSiteSearchDocumentMetadataBean> documentMetadataBeans = new ArrayList<>();
        facetMap.keySet().forEach(facet -> {
            if (turSEResultAttr.containsKey(facet)) {
                if (turSEResultAttr.get(facet) instanceof ArrayList) {
                    ((ArrayList<?>) turSEResultAttr.get(facet))
                            .forEach(facetValueObject -> addFilterQueryByType(uri,
                                    documentMetadataBeans, facet, facetValueObject));
                } else {
                    addFilterQueryByType(uri, documentMetadataBeans, facet,
                            turSEResultAttr.get(facet));
                }

            }
        });
        return documentMetadataBeans;
    }

    private static void addFilterQueryByType(URI uri,
            List<TurSNSiteSearchDocumentMetadataBean> documentMetadataBeans, String facet,
            Object attrValue) {
        String facetValue = TurSolrField.convertFieldToString(attrValue);
        documentMetadataBeans.add(TurSNSiteSearchDocumentMetadataBean.builder()
                .href(TurSNUtils.addFilterQuery(uri, facet + ":" + facetValue).toString())
                .text(facetValue).build());
    }
}
