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
package com.viglet.turing.sn;

import com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult;
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
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.tika.utils.StringUtils;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.util.ForwardedHeaderUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TurSNUtils {
    public static final String TURING_ENTITY = "turing_entity";
    public static final String DEFAULT_LANGUAGE = "en";
    public static final String URL = "url";

    private TurSNUtils() {
        throw new IllegalStateException("SN Utility class");
    }

    public static boolean hasCorrectedText(TurSESpellCheckResult turSESpellCheckResult) {
        return turSESpellCheckResult.isCorrected() && !StringUtils.isEmpty(turSESpellCheckResult.getCorrectedText());
    }

    public static boolean isAutoCorrectionEnabled(TurSNSiteSearchContext context, TurSNSite turSNSite) {
        return context.getTurSEParameters().getAutoCorrectionDisabled() != 1
                && context.getTurSEParameters().getCurrentPage() == 1 && turSNSite.getSpellCheck() == 1
                && turSNSite.getSpellCheckFixes() == 1;
    }

    public static URI requestToURI(HttpServletRequest request) {
        ServletServerHttpRequest servletServerHttpRequest = new ServletServerHttpRequest(request);
        return ForwardedHeaderUtils.adaptFromForwardedHeaders(servletServerHttpRequest.getURI(),
                servletServerHttpRequest.getHeaders()).build().toUri();
    }


    public static URI addFilterQuery(URI uri, String fq) {
        List<NameValuePair> params = URLEncodedUtils.parse(uri, StandardCharsets.UTF_8);
        StringBuilder sbQueryString = new StringBuilder();
        boolean alreadyExists = false;
        for (NameValuePair nameValuePair : params) {
            if (nameValuePair.getValue() != null) {
                if (nameValuePair.getName().equals(TurSNParamType.FILTER_QUERIES_DEFAULT) &&
                        nameValuePair.getValue().equals(fq)) {
                    alreadyExists = true;
                }
                resetPaginationOrAddParameter(sbQueryString, nameValuePair);
            }
        }
        if (!alreadyExists) {
            TurCommonsUtils.addParameterToQueryString(sbQueryString, TurSNParamType.FILTER_QUERIES_DEFAULT, fq);
        }

        return TurCommonsUtils.modifiedURI(uri, sbQueryString);
    }

    public static URI removeFilterQuery(URI uri, String fq) {
        List<NameValuePair> params = URLEncodedUtils.parse(uri, StandardCharsets.UTF_8);
        StringBuilder sbQueryString = new StringBuilder();
        for (NameValuePair nameValuePair : params) {
            if (!(java.net.URLDecoder.decode(nameValuePair.getValue(), StandardCharsets.UTF_8).equals(fq)
                    && nameValuePair.getName().equals(TurSNParamType.FILTER_QUERIES_DEFAULT))) {
                resetPaginationOrAddParameter(sbQueryString, nameValuePair);
            }
        }
        return TurCommonsUtils.modifiedURI(uri, sbQueryString);
    }

    public static URI removeQueryField(URI uri, String field) {
        List<NameValuePair> params = URLEncodedUtils.parse(uri, StandardCharsets.UTF_8);
        StringBuilder sbQueryString = new StringBuilder();

        for (NameValuePair nameValuePair : params) {
            if (!(nameValuePair.getName().equals(field))) {
                TurCommonsUtils.addParameterToQueryString(sbQueryString, nameValuePair.getName(), nameValuePair.getValue());
            }
        }

        return TurCommonsUtils.modifiedURI(uri, sbQueryString);
    }


    private static void resetPaginationOrAddParameter(StringBuilder sbQueryString, NameValuePair nameValuePair) {
        if ((nameValuePair.getName().equals(TurSNParamType.PAGE))) {
            TurCommonsUtils.addParameterToQueryString(sbQueryString, nameValuePair.getName(), "1");
        } else {
            TurCommonsUtils.addParameterToQueryString(sbQueryString, nameValuePair.getName(), nameValuePair.getValue());
        }
    }

    public static void addSNDocument(URI uri, Map<String, TurSNSiteFieldExtDto> fieldExtMap,
                                     Map<String, TurSNSiteFieldExtDto> facetMap, List<TurSNSiteSearchDocumentBean> turSNSiteSearchDocumentsBean,
                                     TurSEResult result, boolean isElevate) {
        addSNDocumentWithPosition(uri, fieldExtMap, facetMap, turSNSiteSearchDocumentsBean, result, isElevate, null);
    }

    public static void addSNDocumentWithPosition(URI uri, Map<String, TurSNSiteFieldExtDto> fieldExtMap,
                                                 Map<String, TurSNSiteFieldExtDto> facetMap, List<TurSNSiteSearchDocumentBean> turSNSiteSearchDocumentsBean,
                                                 TurSEResult result, boolean isElevate, Integer position) {
        TurSNSiteSearchDocumentBean turSNSiteSearchDocumentBean = new TurSNSiteSearchDocumentBean();
        Map<String, Object> turSEResultAttr = result.getFields();
        if (turSEResultAttr != null) {
            Set<String> attribs = turSEResultAttr.keySet();
            turSNSiteSearchDocumentBean.setElevate(isElevate);
            List<TurSNSiteSearchDocumentMetadataBean> turSNSiteSearchDocumentMetadataBeans = addMetadataFromDocument(uri,
                    facetMap, turSEResultAttr);
            turSNSiteSearchDocumentBean.setMetadata(turSNSiteSearchDocumentMetadataBeans);
            setSourceFromDocument(turSNSiteSearchDocumentBean, turSEResultAttr);
            Map<String, Object> fields = addFieldsFromDocument(fieldExtMap, turSEResultAttr, attribs);
            turSNSiteSearchDocumentBean.setFields(fields);
        }
        if (position == null) {
            turSNSiteSearchDocumentsBean.add(turSNSiteSearchDocumentBean);
        } else {
            turSNSiteSearchDocumentsBean.add(position, turSNSiteSearchDocumentBean);
        }
    }

    private static Map<String, Object> addFieldsFromDocument(Map<String, TurSNSiteFieldExtDto> fieldExtMap,
                                                             Map<String, Object> turSEResultAttr, Set<String> attribs) {
        Map<String, Object> fields = new HashMap<>();
        attribs.forEach(attribute -> {
            if (!attribute.startsWith(TURING_ENTITY)) {
                addFieldAndValueToMap(turSEResultAttr, fields, attribute, getFieldName(fieldExtMap, attribute));
            }
        });
        return fields;
    }

    private static String getFieldName(Map<String, TurSNSiteFieldExtDto> fieldExtMap, String attribute) {
        String nodeName;
        if (fieldExtMap.containsKey(attribute)) {
            TurSNSiteFieldExtDto turSNSiteFieldExtDto = fieldExtMap.get(attribute);
            nodeName = turSNSiteFieldExtDto.getName();
        } else {
            nodeName = attribute;
        }
        return nodeName;
    }

    private static void addFieldAndValueToMap(Map<String, Object> turSEResultAttr, Map<String, Object> fields,
                                              String attribute, String nodeName) {
        if (nodeName != null && fields.containsKey(nodeName)) {
            addValueToExistingFieldMap(turSEResultAttr, fields, attribute, nodeName);
        } else {
            fields.put(nodeName, turSEResultAttr.get(attribute));

        }
    }

    @SuppressWarnings("unchecked")
    private static void addValueToExistingFieldMap(Map<String, Object> turSEResultAttr, Map<String, Object> fields,
                                                   String attribute, String nodeName) {
        if (!(fields.get(nodeName) instanceof List)) {
            List<Object> attributeValues = new ArrayList<>();
            attributeValues.add(fields.get(nodeName));
            attributeValues.add(turSEResultAttr.get(attribute));
            fields.put(nodeName, attributeValues);
        } else {
            ((List<Object>) fields.get(nodeName)).add(turSEResultAttr.get(attribute));
        }
    }

    private static void setSourceFromDocument(TurSNSiteSearchDocumentBean turSNSiteSearchDocumentBean,
                                              Map<String, Object> turSEResultAttr) {
        if (turSEResultAttr.containsKey(URL)) {
            turSNSiteSearchDocumentBean.setSource((String) turSEResultAttr.get(URL));
        }
    }

    private static List<TurSNSiteSearchDocumentMetadataBean> addMetadataFromDocument(URI uri,
                                                                                     Map<String, TurSNSiteFieldExtDto> facetMap,
                                                                                     Map<String, Object> turSEResultAttr) {
        List<TurSNSiteSearchDocumentMetadataBean> turSNSiteSearchDocumentMetadataBeans = new ArrayList<>();
        if (facetMap != null) {
            facetMap.keySet()
                    .forEach(facet -> {
                        if (turSEResultAttr.containsKey(facet)) {
                            if (turSEResultAttr.get(facet) instanceof ArrayList) {
                                ((ArrayList<?>) turSEResultAttr.get(facet))
                                        .forEach(facetValueObject ->
                                                addFilterQueryByType(uri,
                                                turSNSiteSearchDocumentMetadataBeans, facet, facetValueObject));
                            } else {
                                addFilterQueryByType(uri, turSNSiteSearchDocumentMetadataBeans, facet,
                                        turSEResultAttr.get(facet));
                            }
                        }
                    });
        }
        return turSNSiteSearchDocumentMetadataBeans;
    }

    private static void addFilterQueryByType(URI uri,
                                             List<TurSNSiteSearchDocumentMetadataBean> turSNSiteSearchDocumentMetadataBeans, String facet,
                                             Object attrValue) {
        String facetValue = TurSolrField.convertFieldToString(attrValue);
        TurSNSiteSearchDocumentMetadataBean turSNSiteSearchDocumentMetadataBean = new TurSNSiteSearchDocumentMetadataBean();
        turSNSiteSearchDocumentMetadataBean
                .setHref(TurSNUtils.addFilterQuery(uri, facet + ":" + facetValue).toString());
        turSNSiteSearchDocumentMetadataBean.setText(facetValue);
        turSNSiteSearchDocumentMetadataBeans.add(turSNSiteSearchDocumentMetadataBean);
    }
}
