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
package com.viglet.turing.sn;

import com.viglet.turing.api.sn.job.TurSNJobItem;
import com.viglet.turing.nlp.TurNLP;
import com.viglet.turing.nlp.TurNLPProcess;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 */
@Component
public class TurSNNLPProcess {
    private static final Logger logger = LogManager.getLogger(TurSNNLPProcess.class);
    @Autowired
    private TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    @Autowired
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    @Autowired
    private TurNLPProcess turNLPProcess;

    public void processNLP(TurSNJobItem turSNJobItem, TurSNSite turSNSite, Map<String, Object> consolidateResults) {
        TurSNSiteLocale turSNSiteLocale = turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(turSNSite,
                turSNJobItem.getLocale());

        if (useNLPToProcessAttributes(turSNSiteLocale)) {
            List<TurSNSiteFieldExt> turSNSiteFieldsExt = turSNSiteFieldExtRepository
                    .findByTurSNSiteAndNlpAndEnabled(turSNSite, 1, 1);

            // Convert List to HashMap
            Map<String, TurSNSiteFieldExt> turSNSiteFieldsExtMap = convertFieldsExtListToMap(turSNSiteFieldsExt);

            // Select only fields that is checked as NLP. These attributes will be processed
            // by NLP
            HashMap<String, Object> seAttributes = defineSEAttributesToBeProcessedByNLP(turSNJobItem,
                    turSNSiteFieldsExtMap);

            Optional<TurNLP> turNLP = turNLPProcess.processAttribsByNLP(turSNSiteLocale.getTurNLPInstance(),
                    seAttributes);

            // Add prefix to attribute name
            Map<String, Object> nlpAttributesToSearchEngine = turNLP.isPresent()
                    ? createNLPAttributesToSEFromNLPEntityMap(turNLP.get())
                    : new HashMap<>();

            // Copy NLP attributes to consolidateResults
            copyNLPAttributesToConsolidateResults(consolidateResults, nlpAttributesToSearchEngine);
        }
    }

    private Map<String, Object> createNLPAttributesToSEFromNLPEntityMap(TurNLP turNLP) {
        Map<String, Object> nlpAttributesToSearchEngine = new HashMap<>();

        for (Map.Entry<String, List<String>> nlpResult : turNLP.getEntityMapWithProcessedValues().entrySet()) {
            nlpAttributesToSearchEngine.put("turing_entity_" + nlpResult.getKey(), nlpResult.getValue());
        }

        return nlpAttributesToSearchEngine;
    }

    private void copyNLPAttributesToConsolidateResults(Map<String, Object> consolidateResults,
                                                       Map<String, Object> nlpResultsPreffix) {
        for (Map.Entry<String, Object> nlpResultPrefix : nlpResultsPreffix.entrySet()) {
            consolidateResults.put(nlpResultPrefix.getKey(), nlpResultPrefix.getValue());
        }
    }

    private boolean useNLPToProcessAttributes(TurSNSiteLocale turSNSiteLocale) {
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

    private HashMap<String, Object> defineSEAttributesToBeProcessedByNLP(TurSNJobItem turSNJobItem,
                                                                         Map<String, TurSNSiteFieldExt> turSNSiteFieldsExtMap) {
        HashMap<String, Object> nlpAttributes = new HashMap<>();
        for (Map.Entry<String, Object> attribute : turSNJobItem.getAttributes().entrySet()) {
            if (turSNSiteFieldsExtMap.containsKey(attribute.getKey().toLowerCase())) {
                nlpAttributes.put(attribute.getKey(), attribute.getValue());
            }
        }
        return nlpAttributes;
    }

    private Map<String, TurSNSiteFieldExt> convertFieldsExtListToMap(List<TurSNSiteFieldExt> turSNSiteFieldsExt) {
        Map<String, TurSNSiteFieldExt> turSNSiteFieldsExtMap = new HashMap<>();
        for (TurSNSiteFieldExt turSNSiteFieldExt : turSNSiteFieldsExt) {
            turSNSiteFieldsExtMap.put(turSNSiteFieldExt.getName().toLowerCase(), turSNSiteFieldExt);
        }
        return turSNSiteFieldsExtMap;
    }

}
