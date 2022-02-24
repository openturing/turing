/*
 * Copyright (C) 2016-2021 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.turing.api.sn.queue;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProviders;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProvidersField;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersRepository;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.sn.TurSNConstants;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import com.viglet.turing.solr.TurSolrUtils;
import com.viglet.turing.utils.TurUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 */
@Component
public class TurSNMergeProvidersProcess {
    private static final Logger logger = LogManager.getLogger(TurSNMergeProvidersProcess.class);

    @Autowired
    private TurSolrInstanceProcess turSolrInstanceProcess;
    @Autowired
    private TurSolr turSolr;
    @Autowired
    private TurUtils turUtils;
    @Autowired
    private TurSNSiteMergeProvidersRepository turSNSiteMergeProvidersRepository;

    @SuppressWarnings("unchecked")
    public Map<String, Object> mergeDocuments(TurSNSite turSNSite,
                                              Map<String, Object> queueDocumentAttrs, String locale) {
        List<TurSNSiteMergeProviders> turSNSiteMergeProvidersList = turSNSiteMergeProvidersRepository
                .findByTurSNSite(turSNSite);
        if (!turSNSiteMergeProvidersList.isEmpty()) {
            TurSNSiteMergeProviders turSNSiteMergeProviders = turSNSiteMergeProvidersList.iterator().next();
            if (queueDocumentAttrs.containsKey(TurSNConstants.PROVIDER_ATTRIBUTE)) {
                Object providersAttribute = queueDocumentAttrs.get(TurSNConstants.PROVIDER_ATTRIBUTE);
                List<String> queueDocumentProviders = new ArrayList<>();
                if (providersAttribute instanceof String) {
                    queueDocumentProviders.add((String) providersAttribute);
                } else if (providersAttribute instanceof ArrayList) {
                    queueDocumentProviders.addAll((ArrayList<String>) providersAttribute);
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
                                          TurSNSiteMergeProviders turSNSiteMergeProviders, String locale) {
        String relationValue = (String) queueDocumentAttrs.get(turSNSiteMergeProviders.getRelationFrom());
        List<SolrDocument> resultsFrom = solrDocumentsFrom(turSNSiteMergeProviders, relationValue, locale);
        List<SolrDocument> resultsTo = solrDocumentsTo(turSNSiteMergeProviders, relationValue, locale);
        List<SolrDocument> resultsFromAndTo = solrDocumentsFromAndTo(turSNSiteMergeProviders,
                turSNSiteMergeProviders.getRelationTo(), relationValue, locale);
        if (hasSolrDocuments(resultsFromAndTo)) {
            TurSEResult turSEResultFromAndTo = TurSolrUtils
                    .createTurSEResultFromDocument(resultsFromAndTo.iterator().next());
            return doMergeContent(queueDocumentAttrs,
                    turSEResultFromAndTo.getFields(), turSNSiteMergeProviders);
        } else {
            if (hasSolrDocuments(resultsFrom) && hasSolrDocuments(resultsTo)) {
                desindexSolrDocuments(turSNSiteMergeProviders, resultsFrom);
            }
            if (hasSolrDocuments(resultsTo)) {
                TurSEResult turSEResultTo = TurSolrUtils.createTurSEResultFromDocument(resultsTo.iterator().next());
                return doMergeContent(queueDocumentAttrs, turSEResultTo.getFields(), turSNSiteMergeProviders);
            }
        }
        return queueDocumentAttrs;
    }

    private Map<String, Object> mergeTo(Map<String, Object> queueDocumentAttrs,
                                        TurSNSiteMergeProviders turSNSiteMergeProviders, String locale) {
        String relationValue = (String) queueDocumentAttrs.get(turSNSiteMergeProviders.getRelationTo());
        List<SolrDocument> resultsFrom = solrDocumentsFrom(turSNSiteMergeProviders, relationValue, locale);
        String idValue = (String) queueDocumentAttrs.get(TurSNConstants.ID_ATTRIBUTE);
        List<SolrDocument> resultsFromAndTo = solrDocumentsFromAndTo(turSNSiteMergeProviders, TurSNConstants.ID_ATTRIBUTE, idValue, locale);
        if (hasSolrDocuments(resultsFromAndTo)) {
            TurSEResult turSEResultFromAndTo = TurSolrUtils
                    .createTurSEResultFromDocument(resultsFromAndTo.iterator().next());
            return doMergeContent(turSEResultFromAndTo.getFields(),
                    queueDocumentAttrs, turSNSiteMergeProviders);
        } else if (hasSolrDocuments(resultsFrom)) {
            TurSEResult turSEResultFrom = TurSolrUtils.createTurSEResultFromDocument(resultsFrom.iterator().next());
            Map<String, Object> mergedDocumentAttributes = doMergeContent(turSEResultFrom.getFields(), queueDocumentAttrs,
                    turSNSiteMergeProviders);
            desindexSolrDocuments(turSNSiteMergeProviders, resultsFrom);
            return mergedDocumentAttributes;
        }
        return queueDocumentAttrs;
    }

    private Map<String, Object> doMergeContent(Map<String, Object> attributesFrom, Map<String, Object> attributesTo,
                                               TurSNSiteMergeProviders turSNSiteMergeProviders) {
        Map<String, Object> mergedDocumentAttribs = attributesTo;
        addProviderToSEDocument(mergedDocumentAttribs, turSNSiteMergeProviders.getProviderFrom());
        addOverwrittenAttributesToSolrDocument(attributesFrom, mergedDocumentAttribs,
                turSNSiteMergeProviders.getOverwrittenFields());
        return mergedDocumentAttribs;
    }

    private boolean hasSolrDocuments(List<SolrDocument> resultsTo) {
        return resultsTo != null && !resultsTo.isEmpty();
    }

    private SolrDocumentList solrDocumentsTo(TurSNSiteMergeProviders turSNSiteMergeProviders,
                                             String relationValue,
                                             String locale) {
        Map<String, Object> queryMapTo = new HashMap<>();
        queryMapTo.put(turSNSiteMergeProviders.getRelationTo(), relationValue);
        queryMapTo.put(TurSNConstants.PROVIDER_ATTRIBUTE, turSNSiteMergeProviders.getProviderTo());
        return solrResultAnd(turSNSiteMergeProviders, queryMapTo, locale);
    }

    private SolrDocumentList solrDocumentsFrom(TurSNSiteMergeProviders turSNSiteMergeProviders,
                                               String relationValue,
                                               String locale) {
        Map<String, Object> queryMapFrom = new HashMap<>();
        queryMapFrom.put(turSNSiteMergeProviders.getRelationFrom(), relationValue);
        queryMapFrom.put(TurSNConstants.PROVIDER_ATTRIBUTE, turSNSiteMergeProviders.getProviderFrom());
        return solrResultAnd(turSNSiteMergeProviders, queryMapFrom, locale);
    }

    private SolrDocumentList solrDocumentsFromAndTo(TurSNSiteMergeProviders turSNSiteMergeProviders,
                                                    String relationAttrib, String relationValue, String locale) {
        Map<String, Object> queryMapFrom = new HashMap<>();
        queryMapFrom.put(relationAttrib, relationValue);
        queryMapFrom.put(TurSNConstants.PROVIDER_ATTRIBUTE, String.format("(\"%s\" AND \"%s\")",
                turSNSiteMergeProviders.getProviderFrom(), turSNSiteMergeProviders.getProviderFrom()));
        return solrResultAnd(turSNSiteMergeProviders, queryMapFrom, locale);
    }

    private void desindexSolrDocuments(TurSNSiteMergeProviders turSNSiteMergeProviders, List<SolrDocument> results) {
        turSolrInstanceProcess
                .initSolrInstance(turSNSiteMergeProviders.getTurSNSite().getName(), turSNSiteMergeProviders.getLocale())
                .ifPresent(turSolrInstance -> results
                        .forEach(result -> turSolr.desindexing(turSolrInstance, result.get(TurSNConstants.ID_ATTRIBUTE).toString())));
    }

    private SolrDocumentList solrResultAnd(TurSNSiteMergeProviders turSNSiteMergeProviders,
                                           Map<String, Object> attributes, String locale) {
        return turSolrInstanceProcess
                .initSolrInstance(turSNSiteMergeProviders.getTurSNSite().getName(), Optional.ofNullable(locale).orElse(turSNSiteMergeProviders.getLocale()))
                .map(turSolrInstance -> turSolr.solrResultAnd(turSolrInstance, attributes)).orElse(new SolrDocumentList());

    }

    private void addOverwrittenAttributesToSolrDocument(Map<String, Object> queueDocumentAttrs,
                                                        Map<String, Object> attributesTo,
                                                        Set<TurSNSiteMergeProvidersField> overwrittenFields) {
        queueDocumentAttrs.entrySet().forEach(attributeFrom -> {
            if (overwrittenFields != null && overwrittenFields.stream()
                    .anyMatch(o -> o.getName().equals(attributeFrom.getKey()))) {
                attributesTo.put(attributeFrom.getKey(), attributeFrom.getValue());

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
            List<String> list = turUtils.cloneListOfTermsAsString(providers);

            if (!providers.contains(providerName)) {
                list.add(providerName);
            }
            documentAttributes.put(TurSNConstants.PROVIDER_ATTRIBUTE, list);
        } else {
            logger.debug("The providers attribute of Merge Providers is empty");
        }
    }
}
