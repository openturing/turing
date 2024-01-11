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
package com.viglet.turing.api.sn.queue;

import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProviders;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProvidersField;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersRepository;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.sn.TurSNConstants;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import com.viglet.turing.solr.TurSolrUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 */
@Slf4j
@Component
public class TurSNMergeProvidersProcess {
    private final TurSolrInstanceProcess turSolrInstanceProcess;
    private final TurSolr turSolr;
    private final TurSNSiteMergeProvidersRepository turSNSiteMergeProvidersRepository;

    public TurSNMergeProvidersProcess(TurSolrInstanceProcess turSolrInstanceProcess,
                                      TurSolr turSolr,
                                      TurSNSiteMergeProvidersRepository
                                              turSNSiteMergeProvidersRepository) {
        this.turSolrInstanceProcess = turSolrInstanceProcess;
        this.turSolr = turSolr;
        this.turSNSiteMergeProvidersRepository = turSNSiteMergeProvidersRepository;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> mergeDocuments(TurSNSite turSNSite,
                                              Map<String, Object> queueDocumentAttrs, Locale locale) {
        List<TurSNSiteMergeProviders> turSNSiteMergeProvidersList = turSNSiteMergeProvidersRepository
                .findByTurSNSite(turSNSite);
        if (!turSNSiteMergeProvidersList.isEmpty()) {
            TurSNSiteMergeProviders turSNSiteMergeProviders = turSNSiteMergeProvidersList.getFirst();
            if (queueDocumentAttrs.containsKey(TurSNConstants.PROVIDER_ATTRIBUTE)) {
                Object providerAttribute = queueDocumentAttrs.get(TurSNConstants.PROVIDER_ATTRIBUTE);
                List<String> queueDocumentProviders = new ArrayList<>();
                if (providerAttribute instanceof String stringValue) {
                    queueDocumentProviders.add(stringValue);
                } else if (providerAttribute instanceof ArrayList<?> arrayListValue) {
                    queueDocumentProviders.addAll((Collection<? extends String>) arrayListValue);
                }

                if (queueDocumentProviders.contains(turSNSiteMergeProviders.getProviderFrom())) {
                    return mergeFrom(queueDocumentAttrs, turSNSiteMergeProviders, locale);
                } else if (queueDocumentProviders.contains(turSNSiteMergeProviders.getProviderTo())) {
                    return mergeTo(queueDocumentAttrs, turSNSiteMergeProviders, locale);
                }
            }
        }
        return queueDocumentAttrs;
    }

    private Map<String, Object> mergeFrom(Map<String, Object> queueDocumentAttrs,
                                          TurSNSiteMergeProviders turSNSiteMergeProviders, Locale locale) {
        String relationValue = (String) queueDocumentAttrs.get(turSNSiteMergeProviders.getRelationFrom());
        List<SolrDocument> resultsFrom = solrDocumentsFrom(turSNSiteMergeProviders, relationValue, locale);
        List<SolrDocument> resultsTo = solrDocumentsTo(turSNSiteMergeProviders, relationValue, locale);
        List<SolrDocument> resultsFromAndTo = solrDocumentsFromAndTo(turSNSiteMergeProviders,
                turSNSiteMergeProviders.getRelationTo(), relationValue, locale);
        if (hasSolrDocuments(resultsFromAndTo)) {
            TurSEResult turSEResultFromAndTo = TurSolrUtils
                    .createTurSEResultFromDocument(resultsFromAndTo.getFirst());
            return doMergeContent(queueDocumentAttrs,
                    turSEResultFromAndTo.getFields(), turSNSiteMergeProviders);
        } else {
            if (hasSolrDocuments(resultsFrom) && hasSolrDocuments(resultsTo)) {
                deIndexSolrDocuments(turSNSiteMergeProviders, resultsFrom);
            }
            if (hasSolrDocuments(resultsTo)) {
                TurSEResult turSEResultTo = TurSolrUtils.createTurSEResultFromDocument(resultsTo.getFirst());
                return doMergeContent(queueDocumentAttrs, turSEResultTo.getFields(), turSNSiteMergeProviders);
            }
        }
        return queueDocumentAttrs;
    }

    private Map<String, Object> mergeTo(Map<String, Object> queueDocumentAttrs,
                                        TurSNSiteMergeProviders turSNSiteMergeProviders, Locale locale) {
        String relationValue = (String) queueDocumentAttrs.get(turSNSiteMergeProviders.getRelationTo());
        List<SolrDocument> resultsFrom = solrDocumentsFrom(turSNSiteMergeProviders, relationValue, locale);
        String idValue = (String) queueDocumentAttrs.get(TurSNConstants.ID_ATTRIBUTE);
        List<SolrDocument> resultsFromAndTo = solrDocumentsFromAndTo(turSNSiteMergeProviders,
                TurSNConstants.ID_ATTRIBUTE, idValue, locale);
        if (hasSolrDocuments(resultsFromAndTo)) {
            TurSEResult turSEResultFromAndTo = TurSolrUtils
                    .createTurSEResultFromDocument(resultsFromAndTo.getFirst());
            return doMergeContent(turSEResultFromAndTo.getFields(),
                    queueDocumentAttrs, turSNSiteMergeProviders);
        } else if (hasSolrDocuments(resultsFrom)) {
            TurSEResult turSEResultFrom = TurSolrUtils.createTurSEResultFromDocument(resultsFrom.getFirst());
            Map<String, Object> mergedDocumentAttributes = doMergeContent(turSEResultFrom.getFields(), queueDocumentAttrs,
                    turSNSiteMergeProviders);
            deIndexSolrDocuments(turSNSiteMergeProviders, resultsFrom);
            return mergedDocumentAttributes;
        }
        return queueDocumentAttrs;
    }

    private Map<String, Object> doMergeContent(Map<String, Object> attributesFrom, Map<String, Object> attributesTo,
                                               TurSNSiteMergeProviders turSNSiteMergeProviders) {
        addProviderToSEDocument(attributesTo, turSNSiteMergeProviders.getProviderFrom());
        addOverwrittenAttributesToSolrDocument(attributesFrom, attributesTo,
                turSNSiteMergeProviders.getOverwrittenFields());
        return attributesTo;
    }

    private boolean hasSolrDocuments(List<SolrDocument> resultsTo) {
        return resultsTo != null && !resultsTo.isEmpty();
    }

    private SolrDocumentList solrDocumentsTo(TurSNSiteMergeProviders turSNSiteMergeProviders,
                                             String relationValue,
                                             Locale locale) {
        Map<String, Object> queryMapTo = new HashMap<>();
        queryMapTo.put(turSNSiteMergeProviders.getRelationTo(), relationValue);
        queryMapTo.put(TurSNConstants.PROVIDER_ATTRIBUTE, turSNSiteMergeProviders.getProviderTo());
        return solrResultAnd(turSNSiteMergeProviders, queryMapTo, locale);
    }

    private SolrDocumentList solrDocumentsFrom(TurSNSiteMergeProviders turSNSiteMergeProviders,
                                               String relationValue,
                                               Locale locale) {
        Map<String, Object> queryMapFrom = new HashMap<>();
        queryMapFrom.put(turSNSiteMergeProviders.getRelationFrom(), relationValue);
        queryMapFrom.put(TurSNConstants.PROVIDER_ATTRIBUTE, turSNSiteMergeProviders.getProviderFrom());
        return solrResultAnd(turSNSiteMergeProviders, queryMapFrom, locale);
    }

    private SolrDocumentList solrDocumentsFromAndTo(TurSNSiteMergeProviders turSNSiteMergeProviders,
                                                    String relationAttrib, String relationValue, Locale locale) {
        Map<String, Object> queryMapFrom = new HashMap<>();
        queryMapFrom.put(relationAttrib, relationValue);
        queryMapFrom.put(TurSNConstants.PROVIDER_ATTRIBUTE, String.format("(\"%s\" AND \"%s\")",
                turSNSiteMergeProviders.getProviderFrom(), turSNSiteMergeProviders.getProviderFrom()));
        return solrResultAnd(turSNSiteMergeProviders, queryMapFrom, locale);
    }

    private void deIndexSolrDocuments(TurSNSiteMergeProviders turSNSiteMergeProviders, List<SolrDocument> results) {
        turSolrInstanceProcess
                .initSolrInstance(turSNSiteMergeProviders.getTurSNSite().getName(), turSNSiteMergeProviders.getLocale())
                .ifPresent(turSolrInstance -> results
                        .forEach(result -> turSolr.deIndexing(turSolrInstance,
                                result.get(TurSNConstants.ID_ATTRIBUTE).toString())));
    }

    private SolrDocumentList solrResultAnd(TurSNSiteMergeProviders turSNSiteMergeProviders,
                                           Map<String, Object> attributes, Locale locale) {
        return turSolrInstanceProcess
                .initSolrInstance(turSNSiteMergeProviders.getTurSNSite().getName(),
                        Optional.ofNullable(locale).orElse(turSNSiteMergeProviders.getLocale()))
                .map(turSolrInstance -> turSolr.solrResultAnd(turSolrInstance, attributes))
                .orElse(new SolrDocumentList());

    }

    private void addOverwrittenAttributesToSolrDocument(Map<String, Object> queueDocumentAttrs,
                                                        Map<String, Object> attributesTo,
                                                        Set<TurSNSiteMergeProvidersField> overwrittenFields) {
        queueDocumentAttrs.forEach((key, value) -> {
            if ((overwrittenFields != null) && overwrittenFields.stream()
                    .anyMatch(o -> o.getName().equals(key))) {
                attributesTo.put(key, value);

            }
        });
    }

    @SuppressWarnings("unchecked")
	private void addProviderToSEDocument(Map<String, Object> documentAttributes, String providerName) {
        Object providerAttribute = documentAttributes.get(TurSNConstants.PROVIDER_ATTRIBUTE);
        List<String> providers = new ArrayList<>();
        if (providerAttribute instanceof ArrayList) {
            providers = (ArrayList<String>) documentAttributes.get(TurSNConstants.PROVIDER_ATTRIBUTE);
        } else {
            providers.add((String) providerAttribute);
        }
        if (!providers.isEmpty()) {
            List<String> list = TurCommonsUtils.cloneListOfTermsAsString(providers);

            if (!providers.contains(providerName)) {
                list.add(providerName);
            }
            documentAttributes.put(TurSNConstants.PROVIDER_ATTRIBUTE, list);
        } else {
            log.debug("The providers attribute of Merge Providers is empty");
        }
    }
}
