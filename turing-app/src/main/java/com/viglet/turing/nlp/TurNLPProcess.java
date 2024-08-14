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

package com.viglet.turing.nlp;

import com.google.inject.Inject;
import com.viglet.turing.api.nlp.bean.TurNLPValidateEntity;
import com.viglet.turing.commons.cache.TurCustomClassCache;
import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.nlp.TurNLPVendorEntityRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import com.viglet.turing.plugins.nlp.TurNLPPlugin;
import jakarta.servlet.ServletContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.WebApplicationContextUtils;

import java.util.*;

@Slf4j
@ComponentScan
@Component
public class TurNLPProcess {
    private final TurNLPInstanceRepository turNLPInstanceRepository;
    private final TurNLPVendorEntityRepository turNLPVendorEntityRepository;
    private final TurConfigVarRepository turConfigVarRepository;
    private final ServletContext context;

    @Inject
    public TurNLPProcess(TurNLPInstanceRepository turNLPInstanceRepository,
                         TurNLPVendorEntityRepository turNLPVendorEntityRepository,
                         TurConfigVarRepository turConfigVarRepository,
                         ServletContext context) {
        this.turNLPInstanceRepository = turNLPInstanceRepository;
        this.turNLPVendorEntityRepository = turNLPVendorEntityRepository;
        this.turConfigVarRepository = turConfigVarRepository;
        this.context = context;
    }

    public TurNLPInstance getDefaultNLPInstance() {
        return init().map(TurNLPRequest::getTurNLPInstance).orElse(null);
    }

    private Optional<TurNLPRequest> init() {
        Optional<TurConfigVar> turConfigVar = turConfigVarRepository.findById("DEFAULT_NLP");

        if (turConfigVar.isPresent()) {
            Optional<TurNLPInstance> turNLPInstance = turNLPInstanceRepository.findById(turConfigVar.get().getValue());
            if (turNLPInstance.isPresent()) {
                return init(turNLPInstance.get());
            }
        }
        return Optional.empty();
    }

    private Optional<TurNLPRequest> init(TurNLPInstance turNLPInstance) {
        return init(turNLPInstance, new HashMap<>(), new ArrayList<>());
    }

    private Optional<TurNLPRequest> init(TurNLPInstance turNLPInstance, Map<String, Object> data,
                                         List<TurNLPValidateEntity> turNLPValidateEntities) {
        TurNLPRequest turNLPRequest = new TurNLPRequest();
        turNLPRequest.setTurNLPInstance(turNLPInstance);
        turNLPRequest.setData(data);

        List<TurNLPEntityRequest> turNLPEntitiesRequest = new ArrayList<>();
        turNLPValidateEntities.forEach(entity ->
                turNLPEntitiesRequest.add(new TurNLPEntityRequest(entity.getName(), entity.getTypes(), entity.getSubTypes(),
                        turNLPVendorEntityRepository.findByTurNLPVendorAndTurNLPEntity_internalNameAndLanguage(
                                turNLPInstance.getTurNLPVendor(), entity.getName(), "pt_BR"))));
        turNLPRequest.setEntities(turNLPEntitiesRequest);
        return Optional.of(turNLPRequest);
    }

    private TurNLPResponse getNLPResponse(Optional<TurNLPRequest> turNLPRequestOptional) {
        return turNLPRequestOptional.map(this::createEntityMapFromAttributeMapToBeProcessed)
                .orElse(new TurNLPResponse());
    }

    public TurNLPResponse processTextByNLP(TurNLPInstance turNLPInstance, String text) {
        return processTextByNLP(turNLPInstance, text, getEntitiesFromNLPVendor(turNLPInstance));
    }

    private List<TurNLPValidateEntity> getEntitiesFromNLPVendor(TurNLPInstance turNLPInstance) {
        List<TurNLPValidateEntity> entities = new ArrayList<>();
        turNLPVendorEntityRepository.findByTurNLPVendor(turNLPInstance.getTurNLPVendor())
                .forEach(entity -> entities.add(new TurNLPValidateEntity(entity.getName())));
        return entities;
    }

    public TurNLPResponse processTextByNLP(TurNLPInstance turNLPInstance, String text,
                                           List<TurNLPValidateEntity> turNLPValidateEntities) {
        Optional<TurNLPRequest> turNLPRequest = this.init(turNLPInstance, createDataWithTextAttrib(text),
                turNLPValidateEntities);
        return getNLPResponse(turNLPRequest);
    }

    private Map<String, Object> createDataWithTextAttrib(String text) {
        Map<String, Object> attribs = new HashMap<>();
        attribs.put("text", text);
        return attribs;
    }

    public TurNLPResponse processAttribsByNLP(TurNLPInstance turNLPInstance, Map<String, Object> attributes) {
        return processAttribsByNLP(turNLPInstance, attributes, getEntitiesFromNLPVendor(turNLPInstance));

    }

    public TurNLPResponse processAttribsByNLP(TurNLPInstance turNLPInstance, Map<String, Object> attributes,
                                              List<TurNLPValidateEntity> entities) {
        Optional<TurNLPRequest> turNLPRequest = this.init(turNLPInstance, attributes, entities);
        return getNLPResponse(turNLPRequest);

    }

    private TurNLPResponse createEntityMapFromAttributeMapToBeProcessed(TurNLPRequest turNLPRequest) {
        log.debug("Executing retrieveNLP...");
        return TurCustomClassCache.getCustomClassMap(turNLPRequest.getTurNLPInstance().getTurNLPVendor().getPlugin())
                .map(classInstance -> {
                    TurNLPResponse turNLPResponse = new TurNLPResponse();
                    TurNLPPlugin nlpService;
                    nlpService = (TurNLPPlugin) classInstance;
                    ApplicationContext applicationContext = WebApplicationContextUtils.getWebApplicationContext(context);
                    Optional.ofNullable(applicationContext).ifPresent(appContext -> {
                        applicationContext.getAutowireCapableBeanFactory().autowireBean(nlpService);
                        turNLPResponse.setEntityMapWithProcessedValues(nlpService
                                .processAttributesToEntityMap(turNLPRequest));
                    });
                    if (log.isDebugEnabled() && turNLPResponse.getEntityMapWithProcessedValues() != null) {
                        log.debug("Result retrieveNLP: {}", turNLPResponse.getEntityMapWithProcessedValues());
                    }
                    return turNLPResponse;
                }).orElseGet(TurNLPResponse::new);


    }
}
