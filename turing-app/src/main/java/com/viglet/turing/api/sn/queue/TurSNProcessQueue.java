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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.api.sn.job.TurSNJob;
import com.viglet.turing.api.sn.job.TurSNJobAction;
import com.viglet.turing.api.sn.job.TurSNJobItem;
import com.viglet.turing.nlp.TurNLP;
import com.viglet.turing.nlp.TurNLPProcess;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightTerm;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightDocumentRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightTermRepository;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import com.viglet.turing.thesaurus.TurThesaurusProcessor;
import com.viglet.turing.utils.TurUtils;

@Component
public class TurSNProcessQueue {
    private static final Logger logger = LogManager.getLogger(TurSNProcessQueue.class);
    @Autowired
    private TurSolr turSolr;
    @Autowired
    private TurSNSiteRepository turSNSiteRepository;
    @Autowired
    private TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    @Autowired
    private TurNLPProcess turNLPProcess;
    @Autowired
    private TurThesaurusProcessor turThesaurusProcessor;
    @Autowired
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    @Autowired
    private TurSolrInstanceProcess turSolrInstanceProcess;
    @Autowired
    private TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;
    @Autowired
    private TurSNSiteSpotlightTermRepository turSNSiteSpotlightTermRepository;
    @Autowired
    private TurSNSiteSpotlightDocumentRepository turSNSiteSpotlightDocumentRepository;
    @Autowired
    private TurSNMergeProvidersProcess turSNMergeProvidersProcess;
    @Autowired
    private TurUtils turUtils;
    public static final String INDEXING_QUEUE = "indexing.queue";

    @JmsListener(destination = INDEXING_QUEUE)
    @Transactional
    public void receiveIndexingQueue(TurSNJob turSNJob) {
        logger.debug("receiveQueue turSNJob: {}", turSNJob);
        if (turSNJob != null) {
            this.turSNSiteRepository.findById(turSNJob.getSiteId()).ifPresent(turSNSite -> {
                if (turSNJob.getTurSNJobItems() != null) {
                    for (TurSNJobItem turSNJobItem : turSNJob.getTurSNJobItems()) {
                        boolean status = false;
                        logger.debug("receiveQueue TurSNJobItem: {}", turSNJobItem);
                        if (turSNJobItem.getTurSNJobAction().equals(TurSNJobAction.CREATE)) {
                            status = isSpotlightJob(turSNJobItem) ? createSpotlight(turSNJobItem, turSNSite)
                                    : indexing(turSNJobItem, turSNSite);

                        } else if (turSNJobItem.getTurSNJobAction().equals(TurSNJobAction.DELETE)) {
                            String id = (String) turSNJobItem.getAttributes().get("id");
                            status = (id != null && turSNSiteSpotlightRepository.findById(id).isPresent())
                                    ? deleteSpotlight(turSNJobItem)
                                    : desindexing(turSNJobItem, turSNSite);

                        }
                        if (status) {
                            processQueueInfo(turSNSite, turSNJobItem);
                        } else {
                            logger.warn("Object ID '{}' of '{}' SN Site ({}). was not processed",
                                    turSNJobItem.getAttributes().get("id"),
                                    turSNSite.getName(),
                                    turSNJobItem.getLocale());
                        }
                    }
                }
            });
        } else {
            logger.debug("turSNJob empty or siteId empty");
        }
    }

    private boolean isSpotlightJob(TurSNJobItem turSNJobItem) {
        return turSNJobItem.getAttributes().containsKey("type")
                && turSNJobItem.getAttributes().get("type").equals("TUR_SPOTLIGHT");
    }

    @Transactional
    private boolean ifExistsDeleteSpotlightDependencies(Optional<TurSNSiteSpotlight> turSNSiteSpotlight) {
        if (turSNSiteSpotlight.isPresent()) {
            Set<TurSNSiteSpotlightTerm> turSNSiteSpotlightTerms = turSNSiteSpotlightTermRepository
                    .findByTurSNSiteSpotlight(turSNSiteSpotlight.get());
            turSNSiteSpotlightTermRepository.deleteAllInBatch(turSNSiteSpotlightTerms);

            Set<TurSNSiteSpotlightDocument> turSNSiteSpotlightDocuments = turSNSiteSpotlightDocumentRepository
                    .findByTurSNSiteSpotlight(turSNSiteSpotlight.get());
            turSNSiteSpotlightDocumentRepository.deleteAllInBatch(turSNSiteSpotlightDocuments);
        }
        return true;

    }

    @Transactional
    private boolean deleteSpotlight(TurSNJobItem turSNJobItem) {
        turSNSiteSpotlightRepository.delete((String) turSNJobItem.getAttributes().get("id"));

        if (turSNJobItem.getAttributes().containsKey("id")) {
            turSNSiteSpotlightRepository.delete((String) turSNJobItem.getAttributes().get("id"));
        } else if (turSNJobItem.getAttributes().containsKey(TurSNMergeProvidersProcess.PROVIDER_ATTRIBUTE)) {
            logger.info("Provider Value: "
                    + (String) turSNJobItem.getAttributes().get(TurSNMergeProvidersProcess.PROVIDER_ATTRIBUTE));
            Set<TurSNSiteSpotlight> turSNSiteSpotlights = turSNSiteSpotlightRepository
                    .findByProvider((String) turSNJobItem.getAttributes().get(TurSNMergeProvidersProcess.PROVIDER_ATTRIBUTE));
            turSNSiteSpotlightRepository.deleteAllInBatch(turSNSiteSpotlights);
        }
        return true;
    }

    @Transactional
    private boolean createSpotlight(TurSNJobItem turSNJobItem, TurSNSite turSNSite) {
        String jsonContent = (String) turSNJobItem.getAttributes().get("content");
        ObjectMapper mapper = new ObjectMapper();
        try {
            TurSNSiteSpotlight turSNSiteSpotlight = new TurSNSiteSpotlight();
            String id = (String) turSNJobItem.getAttributes().get("id");
            Optional<TurSNSiteSpotlight> turSNSiteSpotlightOptional = turSNSiteSpotlightRepository.findById(id);
            if (turSNSiteSpotlightOptional.isPresent()) {
                ifExistsDeleteSpotlightDependencies(turSNSiteSpotlightOptional);
                turSNSiteSpotlight = turSNSiteSpotlightOptional.get();
            }

            String name = (String) turSNJobItem.getAttributes().get("name");
            String provider = (String) turSNJobItem.getAttributes().get(TurSNMergeProvidersProcess.PROVIDER_ATTRIBUTE);
            List<String> terms = Arrays.asList(((String) turSNJobItem.getAttributes().get("terms")).split(","));
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            Date date = simpleDateFormat.parse((String) turSNJobItem.getAttributes().get("modificationDate"));
            List<TurSpotlightContent> turSpotlightContents = mapper.readValue(jsonContent,
                    new TypeReference<List<TurSpotlightContent>>() {
                    });

            turSNSiteSpotlight.setId(id);
            turSNSiteSpotlight.setDescription(name);
            turSNSiteSpotlight.setName(name);
            turSNSiteSpotlight.setDate(date);
            turSNSiteSpotlight.setTurSNSite(turSNSite);
            turSNSiteSpotlight.setManaged(0);
            turSNSiteSpotlight.setProvider(provider);
            turSNSiteSpotlightRepository.save(turSNSiteSpotlight);

            for (String term : terms) {
                TurSNSiteSpotlightTerm turSNSiteSpotlightTerm = new TurSNSiteSpotlightTerm();
                turSNSiteSpotlightTerm.setName(term.trim());
                turSNSiteSpotlightTerm.setTurSNSiteSpotlight(turSNSiteSpotlight);
                turSNSiteSpotlightTermRepository.save(turSNSiteSpotlightTerm);
            }

            for (TurSpotlightContent turSpotlightContent : turSpotlightContents) {
                TurSNSiteSpotlightDocument turSNSiteSpotlightDocument = new TurSNSiteSpotlightDocument();
                turSNSiteSpotlightDocument.setPosition(turSpotlightContent.getPosition());
                turSNSiteSpotlightDocument.setTitle(turSpotlightContent.getTitle());
                turSNSiteSpotlightDocument.setTurSNSiteSpotlight(turSNSiteSpotlight);
                turSNSiteSpotlightDocument.setContent(turSpotlightContent.getContent());
                turSNSiteSpotlightDocument.setLink(turSpotlightContent.getLink());
                turSNSiteSpotlightDocument.setType("Page");
                turSNSiteSpotlightDocumentRepository.save(turSNSiteSpotlightDocument);
            }

        } catch (ParseException | JsonProcessingException e) {
            logger.error(e.getMessage(), e);
        }
        return true;
    }

    private void processQueueInfo(TurSNSite turSNSite, TurSNJobItem turSNJobItem) {
        if (turSNSite != null && turSNJobItem != null && turSNJobItem.getAttributes() != null
                && turSNJobItem.getAttributes().containsKey("id")) {
            String action = null;
            if (turSNJobItem.getTurSNJobAction().equals(TurSNJobAction.CREATE)) {
                action = "Created";
            } else if (turSNJobItem.getTurSNJobAction().equals(TurSNJobAction.DELETE)) {
                action = "Deleted";
            }
            logger.info("{} the Object ID '{}' of '{}' SN Site ({}).", action, turSNJobItem.getAttributes().get("id"),
                    turSNSite.getName(), turSNJobItem.getLocale());

        }
    }

    public boolean desindexing(TurSNJobItem turSNJobItem, TurSNSite turSNSite) {
        logger.debug("Deindexing");
        return turSolrInstanceProcess.initSolrInstance(turSNSite, turSNJobItem.getLocale()).map(turSolrInstance -> {
            if (turSNJobItem.getAttributes().containsKey("id")) {
                turSolr.desindexing(turSolrInstance, (String) turSNJobItem.getAttributes().get("id"));
            } else if (turSNJobItem.getAttributes().containsKey("type")) {
                turSolr.desindexingByType(turSolrInstance, (String) turSNJobItem.getAttributes().get("type"));
            }
            return true;
        }).orElse(false);
    }

    public boolean indexing(TurSNJobItem turSNJobItem, TurSNSite turSNSite) {
        logger.debug("Indexing");
        Map<String, Object> consolidateResults = new HashMap<>();

        processSEAttributes(turSNJobItem, consolidateResults);

        processNLP(turSNJobItem, turSNSite, consolidateResults);

        processThesaurus(turSNJobItem, turSNSite, consolidateResults);

        Map<String, Object> attributes = this.removeDuplicateTerms(
                turSNMergeProvidersProcess.mergeDocuments(turSNSite, turSNJobItem.getLocale(), consolidateResults));

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

    private void processThesaurus(TurSNJobItem turSNJobItem, TurSNSite turSNSite,
                                  Map<String, Object> consolidateResults) {
        boolean thesaurus = false;
        if (turSNSite.getThesaurus() < 1) {
            logger.debug("It is not using Thesaurus to process attributes");
            thesaurus = false;
        } else {
            logger.debug("It is using Thesaurus to process attributes");
            thesaurus = true;
        }
        if (thesaurus) {
            turThesaurusProcessor.startup();
            Map<String, Object> thesaurusResults = turThesaurusProcessor.detectTerms(turSNJobItem.getAttributes());

            logger.debug("thesaurusResults.size(): {}", thesaurusResults.size());
            for (Entry<String, Object> thesaurusResult : thesaurusResults.entrySet()) {
                logger.debug("thesaurusResult Key: {}", thesaurusResult.getKey());
                logger.debug("thesaurusResult Value: {}", thesaurusResult.getValue());
                consolidateResults.put(thesaurusResult.getKey(), thesaurusResult.getValue());
            }
        }
    }

    private void processNLP(TurSNJobItem turSNJobItem, TurSNSite turSNSite, Map<String, Object> consolidateResults) {
        TurSNSiteLocale turSNSiteLocale = turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(turSNSite,
                turSNJobItem.getLocale());

        if (useNLPToProcessAttribs(turSNSiteLocale)) {
            List<TurSNSiteFieldExt> turSNSiteFieldsExt = turSNSiteFieldExtRepository
                    .findByTurSNSiteAndNlpAndEnabled(turSNSite, 1, 1);

            // Convert List to HashMap
            Map<String, TurSNSiteFieldExt> turSNSiteFieldsExtMap = converFieldsExtListToMap(turSNSiteFieldsExt);

            // Select only fields that is checked as NLP. These attributes will be processed
            // by NLP
            HashMap<String, Object> seAttributes = defineSEAttribsToBeProcessedByNLP(turSNJobItem,
                    turSNSiteFieldsExtMap);

            Optional<TurNLP> turNLP = turNLPProcess.processAttribsByNLP(turSNSiteLocale.getTurNLPInstance(),
                    seAttributes);

            // Add prefix to attribute name
            Map<String, Object> nlpAttributesToSearchEngine = turNLP.isPresent()
                    ? createNLPAttributestoSEFromNLPEntityMap(turNLP.get())
                    : new HashMap<>();

            // Copy NLP attributes to consolidateResults
            copyNLPAttribsToConsolidateResults(consolidateResults, nlpAttributesToSearchEngine);
        }
    }

    private Map<String, Object> createNLPAttributestoSEFromNLPEntityMap(TurNLP turNLP) {
        Map<String, Object> nlpAttributesToSearchEngine = new HashMap<>();

        for (Entry<String, List<String>> nlpResult : turNLP.getEntityMapWithProcessedValues().entrySet()) {
            nlpAttributesToSearchEngine.put("turing_entity_" + nlpResult.getKey(), nlpResult.getValue());
        }

        return nlpAttributesToSearchEngine;
    }

    private HashMap<String, Object> defineSEAttribsToBeProcessedByNLP(TurSNJobItem turSNJobItem,
                                                                      Map<String, TurSNSiteFieldExt> turSNSiteFieldsExtMap) {
        HashMap<String, Object> nlpAttributes = new HashMap<>();
        for (Entry<String, Object> attribute : turSNJobItem.getAttributes().entrySet()) {
            if (turSNSiteFieldsExtMap.containsKey(attribute.getKey().toLowerCase())) {
                nlpAttributes.put(attribute.getKey(), attribute.getValue());
            }
        }
        return nlpAttributes;
    }

    private void copyNLPAttribsToConsolidateResults(Map<String, Object> consolidateResults,
                                                    Map<String, Object> nlpResultsPreffix) {
        for (Entry<String, Object> nlpResultPreffix : nlpResultsPreffix.entrySet()) {
            consolidateResults.put(nlpResultPreffix.getKey(), nlpResultPreffix.getValue());
        }
    }

    private Map<String, TurSNSiteFieldExt> converFieldsExtListToMap(List<TurSNSiteFieldExt> turSNSiteFieldsExt) {
        Map<String, TurSNSiteFieldExt> turSNSiteFieldsExtMap = new HashMap<>();
        for (TurSNSiteFieldExt turSNSiteFieldExt : turSNSiteFieldsExt) {
            turSNSiteFieldsExtMap.put(turSNSiteFieldExt.getName().toLowerCase(), turSNSiteFieldExt);
        }
        return turSNSiteFieldsExtMap;
    }

    private boolean useNLPToProcessAttribs(TurSNSiteLocale turSNSiteLocale) {
        boolean nlp;
        if (turSNSiteLocale != null && turSNSiteLocale.getTurNLPInstance() != null) {
            if (logger.isDebugEnabled())
                logger.debug("It is using NLP to process attributes");
            nlp = true;
        } else {
            if (logger.isDebugEnabled())
                logger.debug("It is not using NLP to process attributes");
            nlp = false;
        }
        return nlp;
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
