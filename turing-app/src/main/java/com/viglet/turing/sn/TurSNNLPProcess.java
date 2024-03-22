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

import com.google.inject.Inject;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.nlp.TurNLPProcess;
import com.viglet.turing.nlp.TurNLPResponse;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 */
@Component
@Slf4j
public class TurSNNLPProcess {
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurNLPProcess turNLPProcess;

    @Inject
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
            consolidateResults.putAll(Optional.ofNullable(turNLPProcess.processAttribsByNLP(turSNSiteLocale.getTurNLPInstance(),
                            defineSEAttributesToBeProcessedByNLP(turSNJobItem,
                                    convertFieldsExtListToMap(turSNSiteFieldExtRepository
                                            .findByTurSNSiteAndNlpAndEnabled(turSNSite, 1, 1)))))
                    .map(this::createNLPAttributesToSEFromNLPEntityMap)
                    .orElse(new HashMap<>()));
        }
    }

    private Map<String, Object> createNLPAttributesToSEFromNLPEntityMap(TurNLPResponse turNLPResponse) {

        return turNLPResponse.getEntityMapWithProcessedValues()
                .entrySet().stream()
                .collect(Collectors.toMap(nlpResult -> "turing_entity_" + nlpResult.getKey(),
                        Map.Entry::getValue,
                        (a, b) -> b));
    }

    private boolean useNLPToProcessAttributes(TurSNSiteLocale turSNSiteLocale) {
        if (turSNSiteLocale != null && turSNSiteLocale.getTurNLPInstance() != null) {
            log.debug("It is using NLP to process attributes");
            return true;
        } else {
            log.debug("It is not using NLP to process attributes");
            return false;
        }
    }

    private HashMap<String, Object> defineSEAttributesToBeProcessedByNLP(TurSNJobItem turSNJobItem,
                                                                         Map<String, TurSNSiteFieldExt> turSNSiteFieldsExtMap) {
        HashMap<String, Object> nlpAttributes = new HashMap<>();
        Optional.ofNullable(turSNJobItem.getAttributes()).ifPresent(attributes ->
                turSNJobItem.getAttributes().forEach((key, value) -> {
                    if (turSNSiteFieldsExtMap.containsKey(key.toLowerCase())) nlpAttributes.put(key, value);
                }));
        return nlpAttributes;
    }

    private Map<String, TurSNSiteFieldExt> convertFieldsExtListToMap(List<TurSNSiteFieldExt> turSNSiteFieldsExt) {
        return turSNSiteFieldsExt.stream().collect(Collectors
                .toMap(turSNSiteFieldExt ->
                                turSNSiteFieldExt.getName().toLowerCase(),
                        turSNSiteFieldExt -> turSNSiteFieldExt,
                        (a, b) -> b));
    }

}
