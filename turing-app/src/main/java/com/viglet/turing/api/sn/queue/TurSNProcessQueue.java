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

import com.viglet.turing.api.sn.job.TurSNJob;
import com.viglet.turing.api.sn.job.TurSNJobAction;
import com.viglet.turing.api.sn.job.TurSNJobItem;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import com.viglet.turing.sn.TurSNConstants;
import com.viglet.turing.sn.TurSNNLPProcess;
import com.viglet.turing.sn.TurSNThesaurusProcess;
import com.viglet.turing.sn.TurSNSpotlightProcess;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import com.viglet.turing.utils.TurUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.Map.Entry;

@Component
public class TurSNProcessQueue {
    private static final Logger logger = LogManager.getLogger(TurSNProcessQueue.class);
    @Autowired
    private TurSolr turSolr;
    @Autowired
    private TurSNSiteRepository turSNSiteRepository;
    @Autowired
    private TurSolrInstanceProcess turSolrInstanceProcess;
    @Autowired
    private TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;
    @Autowired
    private TurSNMergeProvidersProcess turSNMergeProvidersProcess;
    @Autowired
    private TurUtils turUtils;
    @Autowired
    private TurSNSpotlightProcess turSNSpotlightProcess;
    @Autowired
    private TurSNNLPProcess turSNNLPProcess;
    @Autowired
    private TurSNThesaurusProcess turSNThesaurusProcess;

    public TurSNProcessQueue() {
        // Empty
    }

    @JmsListener(destination = TurSNConstants.INDEXING_QUEUE)
    @Transactional
    public void receiveIndexingQueue(TurSNJob turSNJob) {
        logger.debug("receiveQueue turSNJob: {}", turSNJob);
        if (turSNJob != null) {
            this.turSNSiteRepository.findById(turSNJob.getSiteId()).ifPresent(turSNSite -> {
                turSNJob.getTurSNJobItems().forEach(turSNJobItem -> {
                    if (processJob(turSNSite, turSNJobItem)) {
                        processQueueInfo(turSNSite, turSNJobItem);
                    } else {
                        logNoProcessed(turSNSite, turSNJobItem);
                    }
                });
            });
        } else {
            logger.debug("turSNJob empty or siteId empty");
        }
    }

    private void logNoProcessed(TurSNSite turSNSite, TurSNJobItem turSNJobItem) {
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
        boolean status;
        String id = (String) turSNJobItem.getAttributes().get(TurSNConstants.ID_ATTRIBUTE);
        status = (id != null && turSNSiteSpotlightRepository.findById(id).isPresent())
                ? turSNSpotlightProcess.deleteUnmanagedSpotlight(turSNJobItem, turSNSite)
                : deindex(turSNJobItem, turSNSite);
        return status;
    }

    private boolean createJob(TurSNSite turSNSite, TurSNJobItem turSNJobItem) {
        boolean status;
        status = turSNSpotlightProcess.isSpotlightJob(turSNJobItem) ?
                turSNSpotlightProcess.createUnmanagedSpotlight(turSNJobItem, turSNSite)
                : index(turSNJobItem, turSNSite);
        return status;
    }

    private void processQueueInfo(TurSNSite turSNSite, TurSNJobItem turSNJobItem) {
        if (turSNSite != null && turSNJobItem != null && turSNJobItem.getAttributes() != null
                && turSNJobItem.getAttributes().containsKey(TurSNConstants.ID_ATTRIBUTE)) {
            String action = null;
            if (turSNJobItem.getTurSNJobAction().equals(TurSNJobAction.CREATE)) {
                action = "Created";
            } else if (turSNJobItem.getTurSNJobAction().equals(TurSNJobAction.DELETE)) {
                action = "Deleted";
            }
            logger.info("{} the Object ID '{}' of '{}' SN Site ({}).", action,
                    turSNJobItem.getAttributes().get(TurSNConstants.ID_ATTRIBUTE),
                    turSNSite.getName(), turSNJobItem.getLocale());
        }
    }

    public boolean deindex(TurSNJobItem turSNJobItem, TurSNSite turSNSite) {
        logger.debug("Deindex");
        return turSolrInstanceProcess.initSolrInstance(turSNSite, turSNJobItem.getLocale()).map(turSolrInstance -> {
            if (turSNJobItem.getAttributes().containsKey(TurSNConstants.ID_ATTRIBUTE)) {
                turSolr.desindexing(turSolrInstance, (String) turSNJobItem.getAttributes().get(TurSNConstants.ID_ATTRIBUTE));
            } else if (turSNJobItem.getAttributes().containsKey(TurSNConstants.TYPE_ATTRIBUTE)) {
                turSolr.desindexingByType(turSolrInstance, (String) turSNJobItem.getAttributes().get(TurSNConstants.TYPE_ATTRIBUTE));
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
                turSNMergeProvidersProcess.mergeDocuments(turSNSite, consolidateResults));

        // SE
        return turSolrInstanceProcess.initSolrInstance(turSNSite, turSNJobItem.getLocale()).map(turSolrInstance -> {
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
            List<String> list = turUtils.cloneListOfTermsAsString(nlpAttributeArray);
            Set<String> termsUnique = new HashSet<>(list);
            List<Object> arrayValue = new ArrayList<>();
            arrayValue.addAll(termsUnique);
            attributesWithUniqueTerms.put(attribute.getKey(), arrayValue);
            termsUnique.forEach(
                    term -> logger.debug("removeDuplicateTerms: attributesWithUniqueTerms Array Value: {}", term));
        }
    }

}
