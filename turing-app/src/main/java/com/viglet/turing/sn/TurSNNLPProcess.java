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

import com.viglet.turing.api.sn.job.TurSNJobItem;
import com.viglet.turing.nlp.TurNLPProcess;
import com.viglet.turing.nlp.TurNLPResponse;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 */
@Component
public class TurSNNLPProcess {
    private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurNLPProcess turNLPProcess;

    public TurSNNLPProcess(TurSNSiteLocaleRepository turSNSiteLocaleRepository,
                           TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
                           TurNLPProcess turNLPProcess) {
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.turNLPProcess = turNLPProcess;
    }

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

            TurNLPResponse turNLPResponse = turNLPProcess.processAttribsByNLP(turSNSiteLocale.getTurNLPInstance(),
                    seAttributes);

            // Add prefix to attribute name
            Map<String, Object> nlpAttributesToSearchEngine = turNLPResponse != null
                    ? createNLPAttributesToSEFromNLPEntityMap(turNLPResponse)
                    : new HashMap<>();

            // Copy NLP attributes to consolidateResults
            copyNLPAttributesToConsolidateResults(consolidateResults, nlpAttributesToSearchEngine);
        }
    }

    private Map<String, Object> createNLPAttributesToSEFromNLPEntityMap(TurNLPResponse turNLPResponse) {
        Map<String, Object> nlpAttributesToSearchEngine = new HashMap<>();

        for (Map.Entry<String, List<String>> nlpResult : turNLPResponse.getEntityMapWithProcessedValues().entrySet()) {
            nlpAttributesToSearchEngine.put("turing_entity_" + nlpResult.getKey(), nlpResult.getValue());
        }

        return nlpAttributesToSearchEngine;
    }

    private void copyNLPAttributesToConsolidateResults(Map<String, Object> consolidateResults,
                                                       Map<String, Object> nlpResultsPreffix) {
        consolidateResults.putAll(nlpResultsPreffix);
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
