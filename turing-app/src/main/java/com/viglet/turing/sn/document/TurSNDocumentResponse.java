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
package com.viglet.turing.sn.document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchResultsBean;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.sn.TurSNUtils;
import com.viglet.turing.sn.spotlight.TurSNSpotlightProcess;
import com.viglet.turing.solr.TurSolrInstance;

/**
 * Maps {@link TurSEResult}s into the two response shapes returned by the search
 * orchestrator: the full {@link TurSNSiteSearchResultsBean} (used by
 * {@code search}) and the lightweight {@link List} of field values or JSON
 * objects (used by {@code searchList}). Extracted from
 * {@code TurSNSearchProcess} so document mapping lives next to its dedicated
 * package and can be exercised in isolation.
 *
 * <p>Behaviour is intentionally identical to the previous inline implementation.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurSNDocumentResponse {

    private static final String DEFAULT_FIELD = "title";

    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNSpotlightProcess turSNSpotlightProcess;

    public TurSNDocumentResponse(TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
            TurSNSpotlightProcess turSNSpotlightProcess) {
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.turSNSpotlightProcess = turSNSpotlightProcess;
    }

    /**
     * Builds the {@link TurSNSiteSearchResultsBean} for a search response,
     * including spotlight documents when enabled on the site and a Solr
     * instance is available.
     */
    public TurSNSiteSearchResultsBean responseDocuments(TurSNSiteSearchContext context,
            TurSolrInstance turSolrInstance, TurSNSite turSNSite,
            Map<String, TurSNSiteFieldExtDto> facetMap, List<TurSEResult> seResults) {
        Map<String, TurSNSiteFieldExtDto> fieldExtMap = buildFieldExtMap(turSNSite);
        List<TurSNSiteSearchDocumentBean> documents = new ArrayList<>();
        seResults.forEach(result -> TurSNUtils.addSNDocument(context.getUri(), fieldExtMap,
                facetMap, documents, result, false));
        Optional.ofNullable(turSNSite).map(TurSNSite::getSpotlightWithResults)
                .filter(TurSNUtils::isTrue)
                .filter(r -> turSolrInstance != null)
                .ifPresent(r -> turSNSpotlightProcess.addSpotlightToResults(context,
                        turSolrInstance, turSNSite, facetMap, fieldExtMap, documents));
        return new TurSNSiteSearchResultsBean().setDocument(documents);
    }

    /**
     * Builds the lightweight list response: when a single field is requested
     * (or the default {@code title}) the list contains the raw field values;
     * otherwise each entry is a {@code Map<String, Object>} with the requested
     * fields.
     */
    public List<Object> responseList(TurSNSiteSearchContext context, List<TurSEResult> seResults) {
        List<Object> termList = new ArrayList<>();
        List<String> fields = getFieldListOrDefault(context);

        for (TurSEResult result : seResults) {
            if (fields.size() > 1) {
                termList.add(buildJsonObjectFromFields(result, fields));
            } else {
                Object attribute = result.getFields().get(fields.getFirst());
                if (attribute != null) {
                    termList.add(attribute);
                }
            }
        }
        return termList;
    }

    private Map<String, TurSNSiteFieldExtDto> buildFieldExtMap(TurSNSite turSNSite) {
        Map<String, TurSNSiteFieldExtDto> fieldExtMap = new HashMap<>();
        turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(turSNSite, 1).stream()
                .map(TurSNSiteFieldExtDto::new)
                .forEach(dto -> fieldExtMap.put(dto.getName(), dto));
        return fieldExtMap;
    }

    private static List<String> getFieldListOrDefault(TurSNSiteSearchContext context) {
        List<String> fields = context.getTurSEParameters().getFieldList();
        if (fields == null || fields.isEmpty()) {
            List<String> defaults = new ArrayList<>();
            defaults.add(DEFAULT_FIELD);
            return defaults;
        }
        return fields;
    }

    private static Map<String, Object> buildJsonObjectFromFields(TurSEResult result,
            List<String> fields) {
        Map<String, Object> jsonObject = new HashMap<>();
        for (String field : fields) {
            Object attribute = result.getFields().get(field);
            if (attribute != null) {
                jsonObject.put(field, attribute);
            }
        }
        return jsonObject;
    }
}
