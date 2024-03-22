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

package com.viglet.turing.api.nlp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.viglet.turing.api.nlp.bean.TurNLPEntityValidateResponse;
import com.viglet.turing.api.nlp.bean.TurNLPValidateDocument;
import com.viglet.turing.api.nlp.bean.TurNLPValidateResponse;
import com.viglet.turing.filesystem.commons.TurFileAttributes;
import com.viglet.turing.filesystem.commons.TurFileUtils;
import com.viglet.turing.nlp.TurNLPProcess;
import com.viglet.turing.nlp.TurNLPResponse;
import com.viglet.turing.nlp.TurNLPUtils;
import com.viglet.turing.nlp.output.blazon.RedactionScript;
import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.utils.TurPesistenceUtils;
import com.viglet.turing.utils.TurUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/nlp")
@Tag(name = "Natural Language Processing", description = "Natural Language Processing API")
public class TurNLPInstanceAPI {
    private final TurNLPInstanceRepository turNLPInstanceRepository;
    private final TurNLPEntityRepository turNLPEntityRepository;
    private final TurNLPProcess turNLPProcess;
    private final TurNLPUtils turNLPUtils;

    @Inject
    public TurNLPInstanceAPI(TurNLPInstanceRepository turNLPInstanceRepository,
                             TurNLPEntityRepository turNLPEntityRepository,
                             TurNLPProcess turNLPProcess,
                             TurNLPUtils turNLPUtils) {
        this.turNLPInstanceRepository = turNLPInstanceRepository;
        this.turNLPEntityRepository = turNLPEntityRepository;
        this.turNLPProcess = turNLPProcess;
        this.turNLPUtils = turNLPUtils;
    }

    @Operation(summary = "Natural Language Processing List")
    @GetMapping
    public List<TurNLPInstance> turNLPInstanceList() {
        return this.turNLPInstanceRepository.findAll(TurPesistenceUtils.orderByTitleIgnoreCase());
    }

    @Operation(summary = "Show a Natural Language Processing")
    @GetMapping("/{id}")
    public TurNLPInstance turNLPInstanceGet(@PathVariable String id) {
        return this.turNLPInstanceRepository.findById(id).orElse(new TurNLPInstance());
    }

    @Operation(summary = "Natural Language Processing structure")
    @GetMapping("/structure")
    public TurNLPInstance turNLPInstanceStructure() {
        TurNLPInstance turNLPInstance = new TurNLPInstance();
        turNLPInstance.setTurNLPVendor(new TurNLPVendor());
        turNLPInstance.setLanguage("en_US");
        return turNLPInstance;

    }

    @Operation(summary = "Update a Natural Language Processing")
    @PutMapping("/{id}")
    public TurNLPInstance turNLPInstanceUpdate(@PathVariable String id, @RequestBody TurNLPInstance turNLPInstance) {
        return turNLPInstanceRepository.findById(id).map(turNLPInstanceEdit -> {
            turNLPInstanceEdit.setTitle(turNLPInstance.getTitle());
            turNLPInstanceEdit.setDescription(turNLPInstance.getDescription());
            turNLPInstanceEdit.setTurNLPVendor(turNLPInstance.getTurNLPVendor());
            turNLPInstanceEdit.setEndpointURL(turNLPInstance.getEndpointURL());
            turNLPInstanceEdit.setKey(turNLPInstance.getKey());
            turNLPInstanceEdit.setEnabled(turNLPInstance.getEnabled());
            turNLPInstanceEdit.setLanguage(turNLPInstance.getLanguage());
            this.turNLPInstanceRepository.save(turNLPInstanceEdit);
            return turNLPInstanceEdit;
        }).orElse(new TurNLPInstance());

    }

    @Transactional
    @Operation(summary = "Delete a Natural Language Processing")
    @DeleteMapping("/{id}")
    public boolean turNLPInstanceDelete(@PathVariable String id) {
        this.turNLPInstanceRepository.deleteById(id);
        return true;
    }

    @Operation(summary = "Create a Natural Language Processing")
    @PostMapping
    public TurNLPInstance turNLPInstanceAdd(@RequestBody TurNLPInstance turNLPInstance) {
        return turNLPInstance;

    }

    @PostMapping(value = "/{id}/validate/file/blazon", produces = MediaType.APPLICATION_XML_VALUE)
    public RedactionScript validateFile(@RequestParam("file") MultipartFile multipartFile, @PathVariable String id) {
        final String text = TurFileUtils.documentToText(multipartFile);
        TurNLPTextValidate textValidate = new TurNLPTextValidate();
        textValidate.setText(text);
        return this.turNLPInstanceRepository.findById(id).map(turNLPInstance ->
                        turNLPUtils.createRedactionScript(turNLPProcess.processTextByNLP(turNLPInstance, textValidate.getText())))
                .orElse(new RedactionScript());
    }


    @PostMapping("/{id}/validate/document")
    public TurNLPValidateResponse validateDocument(@PathVariable String id,
                                                   @RequestParam("file") MultipartFile multipartFile,
                                                   @RequestParam("config") String turNLPValidateDocumentRequest) {

        File file = TurUtils.getFileFromMultipart(multipartFile);
        TurFileAttributes turFileAttributes = TurFileUtils.readFile(file);
        return this.turNLPInstanceRepository.findById(id)
                .map(turNLPInstance -> {
                    try {
                        TurNLPValidateDocument turNLPValidateDocument = new ObjectMapper().readValue(turNLPValidateDocumentRequest,
                                TurNLPValidateDocument.class);
                        if (turFileAttributes != null && turNLPValidateDocument != null) {
                            TurNLPResponse turNLPResponse = turNLPProcess.processTextByNLP(turNLPInstance,
                                    turFileAttributes.getContent(), turNLPValidateDocument.getEntities());
                            List<String> terms = getNLPTerms(turNLPResponse);
                            turNLPUtils.redactPdf(file, terms);
                            return createNLPValidateResponse(turNLPInstance, turNLPResponse, turFileAttributes.getContent());
                        }
                    } catch (JsonProcessingException e) {
                        log.error(e.getMessage(), e);
                    }
                    return null;
                }).orElse(null);
    }


    @PostMapping("/{id}/validate/text/web")
    public TurNLPValidateResponse validateWeb(@PathVariable String id, @RequestBody TurNLPTextValidate textValidate) {
        return this.turNLPInstanceRepository.findById(id).map(turNLPInstance -> {
            TurNLPResponse turNLPResponse = turNLPProcess.processTextByNLP(turNLPInstance, textValidate.getText());
            return createNLPValidateResponse(turNLPInstance, turNLPResponse, textValidate.getText());
        }).orElse(new TurNLPValidateResponse());
    }

    @PostMapping("/{id}/validate/text/blazon")
    public RedactionScript validateBlazon(@PathVariable String id, @RequestBody TurNLPTextValidate textValidate) {
        return this.turNLPInstanceRepository.findById(id).map(turNLPInstance -> {
            TurNLPResponse turNLPResponse = turNLPProcess.processTextByNLP(turNLPInstance, textValidate.getText());
            return turNLPUtils.createRedactionScript(turNLPResponse);
        }).orElse(new RedactionScript());
    }

    private TurNLPValidateResponse createNLPValidateResponse(TurNLPInstance turNLPInstance,
                                                             TurNLPResponse turNLPResponse, String text) {
        TurNLPValidateResponse turNLPValidateResponse = new TurNLPValidateResponse();
        turNLPValidateResponse.setVendor(turNLPInstance.getTurNLPVendor().getTitle());
        turNLPValidateResponse.setLocale(turNLPInstance.getLanguage());
        turNLPValidateResponse.setText(text);
        Optional.ofNullable(turNLPResponse)
                .map(TurNLPResponse::getEntityMapWithProcessedValues)
                .ifPresent(entityMap -> entityMap.entrySet().stream()
                        .filter(entityType -> entityType.getValue() != null)
                        .forEach(entityType -> {
                            TurNLPEntity turNLPEntity = turNLPEntityRepository.findByInternalName(entityType.getKey());
                            TurNLPEntityValidateResponse turNLPEntityValidateResponse = new TurNLPEntityValidateResponse();
                            turNLPEntityValidateResponse.setType(turNLPEntity);
                            turNLPEntityValidateResponse.setTerms(entityType.getValue());
                            turNLPValidateResponse.getEntities().add(turNLPEntityValidateResponse);
                        }));
        return turNLPValidateResponse;
    }

    private List<String> getNLPTerms(TurNLPResponse turNLPResponse) {
        return Optional.ofNullable(turNLPResponse)
                .map(TurNLPResponse::getEntityMapWithProcessedValues)
                .map(entityMap -> turNLPResponse.getEntityMapWithProcessedValues().entrySet().stream()
                        .filter(entityType -> entityType.getValue() != null)
                        .flatMap(entityType -> entityType.getValue().stream())
                        .collect(Collectors.toList())).orElse(Collections.emptyList());
    }

    @Getter
    @Setter
    public static class TurNLPTextValidate {
        String text;
    }
}