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
package com.viglet.turing.api.ml.data;

import com.google.inject.Inject;
import com.viglet.turing.filesystem.commons.TurFileUtils;
import com.viglet.turing.nlp.TurNLPProcess;
import com.viglet.turing.persistence.model.storage.TurData;
import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;
import com.viglet.turing.persistence.repository.storage.TurDataGroupSentenceRepository;
import com.viglet.turing.persistence.repository.storage.TurDataRepository;
import com.viglet.turing.plugins.nlp.opennlp.TurOpenNLPConnector;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/api/ml/data")
@Tag(name = "Machine Learning Data", description = "Machine Learning Data API")
public class TurMLDataAPI {
    private final TurDataRepository turDataRepository;
    private final TurNLPProcess turNLPProcess;
    private final TurDataGroupSentenceRepository turDataGroupSentenceRepository;
    private final TurOpenNLPConnector turOpenNLPConnector;

    @Inject
    public TurMLDataAPI(TurDataRepository turDataRepository,
                        TurNLPProcess turNLPProcess,
                        TurDataGroupSentenceRepository turDataGroupSentenceRepository,
                        TurOpenNLPConnector turOpenNLPConnector) {
        this.turDataRepository = turDataRepository;
        this.turNLPProcess = turNLPProcess;
        this.turDataGroupSentenceRepository = turDataGroupSentenceRepository;
        this.turOpenNLPConnector = turOpenNLPConnector;
    }

    @Operation(summary = "Machine Learning Data List")
    @GetMapping
    public List<TurData> turDataList() throws JSONException {
        return this.turDataRepository.findAll();
    }

    @Operation(summary = "Show a Machine Learning Data")
    @GetMapping("/{id}")
    public TurData turDataGet(@PathVariable int id) throws JSONException {
        return this.turDataRepository.findById(id);
    }

    @Operation(summary = "Update a Machine Learning Data")
    @PutMapping("/{id}")
    public TurData turDataUpdate(@PathVariable int id, @RequestBody TurData turData) {
        TurData turDataEdit = this.turDataRepository.findById(id);
        turDataEdit.setName(turData.getName());
        turDataEdit.setType(turData.getType());
        this.turDataRepository.save(turDataEdit);
        return turDataEdit;
    }

    @Transactional
    @Operation(summary = "Delete a Machine Learning Data")
    @DeleteMapping("/{id}")
    public boolean turDataDelete(@PathVariable int id) {
        this.turDataRepository.deleteById(id);
        return true;
    }

    @Operation(summary = "Create a Machine Learning Data")
    @PostMapping
    public TurData turDataAdd(@RequestBody TurData turData) {
        this.turDataRepository.save(turData);
        return turData;

    }

    @PostMapping("/import")
    @Transactional
    public String turDataImport(@RequestParam("file") MultipartFile multipartFile) {
        String[] sentences = turOpenNLPConnector.sentenceDetect(turNLPProcess.getDefaultNLPInstance(),
                Objects.requireNonNull(TurFileUtils.documentToText(multipartFile)).getContent());
        TurData turData = new TurData();
        turData.setName(multipartFile.getOriginalFilename());
        turData.setType(FilenameUtils.getExtension(multipartFile.getOriginalFilename()));
        this.turDataRepository.save(turData);
        Arrays.stream(sentences).forEach(sentence -> {
            TurDataGroupSentence turDataGroupSentence = new TurDataGroupSentence();
            turDataGroupSentence.setTurData(turData);
            turDataGroupSentence.setSentence(sentence);
            this.turDataGroupSentenceRepository.save(turDataGroupSentence);
        });
        JSONObject jsonTraining = new JSONObject();
        jsonTraining.put("sentences", sentences);
        return jsonTraining.toString();
    }
}
