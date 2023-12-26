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

import com.google.inject.Inject;
import com.viglet.turing.api.sn.job.TurSNJob;
import com.viglet.turing.api.sn.job.TurSNJobAction;
import com.viglet.turing.api.sn.job.TurSNJobItem;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.sn.TurSNConstants;
import com.viglet.turing.sn.TurSNNLPProcess;
import com.viglet.turing.sn.TurSNThesaurusProcess;
import com.viglet.turing.sn.spotlight.TurSNSpotlightProcess;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.Map.Entry;

@Component
public class TurSNProcessQueue {
    private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
    private TurSolr turSolr;
    private TurSNSiteRepository turSNSiteRepository;
    private TurSolrInstanceProcess turSolrInstanceProcess;
    private TurSNMergeProvidersProcess turSNMergeProvidersProcess;
    private TurSNSpotlightProcess turSNSpotlightProcess;
    private TurSNNLPProcess turSNNLPProcess;

    private TurSNThesaurusProcess turSNThesaurusProcess;

    @Inject
    public TurSNProcessQueue(TurSolr turSolr, TurSNSiteRepository turSNSiteRepository,
                             TurSolrInstanceProcess turSolrInstanceProcess,
                             TurSNMergeProvidersProcess turSNMergeProvidersProcess,
                             TurSNSpotlightProcess turSNSpotlightProcess,
                             TurSNNLPProcess turSNNLPProcess,
                             TurSNThesaurusProcess turSNThesaurusProcess) {
        this.turSolr = turSolr;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSolrInstanceProcess = turSolrInstanceProcess;
        this.turSNMergeProvidersProcess = turSNMergeProvidersProcess;
        this.turSNSpotlightProcess = turSNSpotlightProcess;
        this.turSNNLPProcess = turSNNLPProcess;
        this.turSNThesaurusProcess = turSNThesaurusProcess;
    }

    public TurSNProcessQueue() {
        // Empty
    }

    @JmsListener(destination = TurSNConstants.INDEXING_QUEUE)
    @Transactional
    public void receiveIndexingQueue(TurSNJob turSNJob) {
        logger.debug("receiveQueue turSNJob: {}", turSNJob);
        if (turSNJob != null) {
            this.turSNSiteRepository.findById(turSNJob.getSiteId()).ifPresent(turSNSite ->
                    turSNJob.getTurSNJobItems().forEach(turSNJobItem -> {
                        if (processJob(turSNSite, turSNJobItem)) {
                            processQueueInfo(turSNSite, turSNJobItem);
                        } else {
                            noProcessedWarning(turSNSite, turSNJobItem);
                        }
                    })
            );
        } else {
            logger.debug("turSNJob empty or siteId empty");
        }
    }

    private void noProcessedWarning(TurSNSite turSNSite, TurSNJobItem turSNJobItem) {
        logger.warn("Object ID '{}' of '{}' SN Site ({}) was not processed",
                turSNJobItem.getAttributes().get("id"),
                turSNSite.getName(),
                turSNJobItem.getLocale());
    }

    private boolean processJob(TurSNSite turSNSite, TurSNJobItem turSNJobItem) {
        boolean status = false;
        logger.debug("processJob TurSNJobItem: {}", turSNJobItem);
        if (turSNJobItem.getTurSNJobAction().equals(TurSNJobAction.CREATE)) {
            status = createJob(turSNSite, turSNJobItem);
        } else if (turSNJobItem.getTurSNJobAction().equals(TurSNJobAction.DELETE)) {
            status = deleteJob(turSNSite, turSNJobItem);
        }
        return status;
    }

    private boolean deleteJob(TurSNSite turSNSite, TurSNJobItem turSNJobItem) {
        return (turSNSpotlightProcess.isSpotlightJob(turSNJobItem))
                ? turSNSpotlightProcess.deleteUnmanagedSpotlight(turSNJobItem, turSNSite)
                : deindex(turSNJobItem, turSNSite);
    }

    private boolean createJob(TurSNSite turSNSite, TurSNJobItem turSNJobItem) {
        return turSNSpotlightProcess.isSpotlightJob(turSNJobItem) ?
                turSNSpotlightProcess.createUnmanagedSpotlight(turSNJobItem, turSNSite)
                : index(turSNJobItem, turSNSite);
    }

    private void processQueueInfo(TurSNSite turSNSite, TurSNJobItem turSNJobItem) {
        if (turSNSite != null && turSNJobItem != null && turSNJobItem.getAttributes() != null) {
            String action = null;
            if (turSNJobItem.getTurSNJobAction().equals(TurSNJobAction.CREATE)) {
                action = "Created";
            } else if (turSNJobItem.getTurSNJobAction().equals(TurSNJobAction.DELETE)) {
                action = "Deleted";
            }
            if (turSNJobItem.getAttributes().containsKey(TurSNConstants.ID_ATTRIBUTE)) {
                logger.info("{} the Object ID '{}' of '{}' SN Site ({}).", action,
                        turSNJobItem.getAttributes().get(TurSNConstants.ID_ATTRIBUTE),
                        turSNSite.getName(), turSNJobItem.getLocale());
            } else if (turSNJobItem.getAttributes().containsKey(TurSNConstants.TYPE_ATTRIBUTE)) {
                logger.info("{} the Object Type '{}' of '{}' SN Site ({}).", action,
                        turSNJobItem.getAttributes().get(TurSNConstants.TYPE_ATTRIBUTE),
                        turSNSite.getName(), turSNJobItem.getLocale());
            }
        }
    }

    public boolean deindex(TurSNJobItem turSNJobItem, TurSNSite turSNSite) {
        logger.debug("Deindex");
        return turSolrInstanceProcess.initSolrInstance(turSNSite.getName(), turSNJobItem.getLocale()).map(turSolrInstance -> {
            if (turSNJobItem.getAttributes().containsKey(TurSNConstants.ID_ATTRIBUTE)) {
                turSolr.deIndexing(turSolrInstance, (String) turSNJobItem.getAttributes().get(TurSNConstants.ID_ATTRIBUTE));
            } else if (turSNJobItem.getAttributes().containsKey(TurSNConstants.TYPE_ATTRIBUTE)) {
                turSolr.deIndexingByType(turSolrInstance, (String) turSNJobItem.getAttributes().get(TurSNConstants.TYPE_ATTRIBUTE));
            }
            return true;
        }).orElse(false);
    }

    public boolean index(TurSNJobItem turSNJobItem, TurSNSite turSNSite) {
        logger.debug("Index");
        Map<String, Object> consolidateResults = new HashMap<>();

        processSEAttributes(turSNJobItem, consolidateResults);

        turSNNLPProcess.processNLP(turSNJobItem, turSNSite, consolidateResults);

        turSNThesaurusProcess.processThesaurus(turSNJobItem, turSNSite, consolidateResults);

        Map<String, Object> attributes = this.removeDuplicateTerms(
                turSNMergeProvidersProcess.mergeDocuments(turSNSite, consolidateResults, turSNJobItem.getLocale()));

        // SE
        return turSolrInstanceProcess.initSolrInstance(turSNSite.getName(), turSNJobItem.getLocale()).map(turSolrInstance -> {
            turSolr.indexing(turSolrInstance, turSNSite, attributes);
            return true;
        }).orElse(false);

    }

    private void processSEAttributes(TurSNJobItem turSNJobItem, Map<String, Object> consolidateResults) {
        for (Entry<String, Object> attribute : turSNJobItem.getAttributes().entrySet()) {
            if (logger.isDebugEnabled())
                logger.debug("SE Consolidate Value: {}", attribute.getValue());
            if (attribute.getValue() != null) {
                if (logger.isDebugEnabled())
                    logger.debug("SE Consolidate Class: {}", attribute.getValue().getClass().getName());
                consolidateResults.put(attribute.getKey(), attribute.getValue());
            }
        }
    }

    public Map<String, Object> removeDuplicateTerms(Map<String, Object> attributes) {
        Map<String, Object> attributesWithUniqueTerms = new HashMap<>();
        if (attributes != null) {
            for (Entry<String, Object> attribute : attributes.entrySet()) {
                if (attribute.getValue() != null) {
                    logger.debug("removeDuplicateTerms: attribute Value: {}", attribute.getValue());
                    logger.debug("removeDuplicateTerms: attribute Class: {}",
                            attribute.getValue().getClass().getName());
                    if (attribute.getValue() instanceof ArrayList) {
                        removeDuplicateTermsFromMultiValue(attributesWithUniqueTerms, attribute);
                    } else {
                        attributesWithUniqueTerms.put(attribute.getKey(), attribute.getValue());
                    }
                }
            }
            logger.debug("removeDuplicateTerms: attributesWithUniqueTerms: {}", attributesWithUniqueTerms);
        }
        return attributesWithUniqueTerms;
    }

    private void removeDuplicateTermsFromMultiValue(Map<String, Object> attributesWithUniqueTerms,
                                                    Entry<String, Object> attribute) {
        List<?> nlpAttributeArray = (ArrayList<?>) attribute.getValue();
        if (!nlpAttributeArray.isEmpty()) {
            List<String> list = TurCommonsUtils.cloneListOfTermsAsString(nlpAttributeArray);
            Set<String> termsUnique = new HashSet<>(list);
            List<Object> arrayValue = new ArrayList<>(termsUnique);
            attributesWithUniqueTerms.put(attribute.getKey(), arrayValue);
            termsUnique.forEach(
                    term -> logger.debug("removeDuplicateTerms: attributesWithUniqueTerms Array Value: {}", term));
        }
    }
}
