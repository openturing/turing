/*
 * Copyright (C) 2016-2022 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.api.se;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.persistence.dto.se.TurSEInstanceDto;
import com.viglet.turing.persistence.mapper.se.TurSEInstanceMapper;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import com.viglet.turing.solr.TurSolrUtils;
import com.viglet.turing.spring.utils.TurPersistenceUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/se")
@Tag(name = "Search Engine", description = "Search Engine API")
public class TurSEInstanceAPI {
        private final TurSEInstanceRepository turSEInstanceRepository;
        private final TurSEInstanceMapper turSEInstanceMapper;
        private final TurSolrInstanceProcess turSolrInstanceProcess;
        private final TurSolr turSolr;

        public TurSEInstanceAPI(TurSEInstanceRepository turSEInstanceRepository,
                        TurSEInstanceMapper turSEInstanceMapper,
                        TurSolrInstanceProcess turSolrInstanceProcess, TurSolr turSolr) {
                this.turSEInstanceRepository = turSEInstanceRepository;
                this.turSEInstanceMapper = turSEInstanceMapper;
                this.turSolrInstanceProcess = turSolrInstanceProcess;
                this.turSolr = turSolr;
        }

        @Operation(summary = "Search Engine List")
        @GetMapping
        public List<TurSEInstanceDto> turSEInstanceList() {
                return turSEInstanceMapper
                                .toDtoList(this.turSEInstanceRepository
                                                .findAll(TurPersistenceUtils.orderByTitleIgnoreCase()));
        }

        @Operation(summary = "Search Engine structure")
        @GetMapping("/structure")
        public TurSEInstanceDto turSearchEngineStructure() {
                TurSEInstance turSEInstance = new TurSEInstance();
                turSEInstance.setTurSEVendor(new TurSEVendor());
                return turSEInstanceMapper.toDto(turSEInstance);

        }

        @Operation(summary = "Show a Search Engine")
        @GetMapping("/{id}")
        public TurSEInstanceDto turSEInstanceGet(@PathVariable String id) {
                return turSEInstanceMapper
                                .toDto(this.turSEInstanceRepository.findById(id).orElse(new TurSEInstance()));
        }

        @Operation(summary = "Update a Search Engine")
        @PutMapping("/{id}")
        public TurSEInstanceDto turSEInstanceUpdate(@PathVariable String id,
                        @RequestBody TurSEInstanceDto turSEInstanceDto) {
                TurSEInstance turSEInstance = turSEInstanceMapper.toEntity(turSEInstanceDto);
                return turSEInstanceRepository.findById(id).map(turSEInstanceEdit -> {
                        turSEInstanceEdit.setTitle(turSEInstance.getTitle());
                        turSEInstanceEdit.setDescription(turSEInstance.getDescription());
                        turSEInstanceEdit.setTurSEVendor(turSEInstance.getTurSEVendor());
                        turSEInstanceEdit.setEndpointUrl(turSEInstance.getEndpointUrl());
                        turSEInstanceEdit.setEnabled(turSEInstance.getEnabled());
                        this.turSEInstanceRepository.save(turSEInstanceEdit);
                        return turSEInstanceMapper.toDto(turSEInstanceEdit);
                }).orElse(new TurSEInstanceDto());

        }

        @Transactional
        @Operation(summary = "Delete a Search Engine")
        @DeleteMapping("/{id}")
        public boolean turSEInstanceDelete(@PathVariable String id) {
                this.turSEInstanceRepository.delete(id);
                return true;
        }

        @Operation(summary = "Create a Search Engine")
        @PostMapping
        public TurSEInstanceDto turSEInstanceAdd(@RequestBody TurSEInstanceDto turSEInstanceDto) {
                TurSEInstance turSEInstance = turSEInstanceMapper.toEntity(turSEInstanceDto);
                this.turSEInstanceRepository.save(turSEInstance);
                return turSEInstanceMapper.toDto(turSEInstance);

        }

        @Operation(summary = "List Solr cores/collections for a Search Engine")
        @GetMapping("/{id}/cores")
        public ResponseEntity<List<String>> turSEInstanceCores(@PathVariable String id) {
                return turSEInstanceRepository.findById(id)
                                .map(turSEInstance -> ResponseEntity.ok(TurSolrUtils.listCores(turSEInstance)))
                                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        }

        @GetMapping("/{id}/{core}/select")
        public ResponseEntity<TurSEResults> turSEInstanceSelect(@PathVariable String id, @PathVariable String core,
                        @ModelAttribute TurSNSearchParams turSNSearchParams) {
                return turSEInstanceRepository.findById(id).map(turSEInstance -> {

                        int page = (turSNSearchParams.getP() != null && turSNSearchParams.getP() > 0)
                                        ? turSNSearchParams.getP()
                                        : 1;
                        int rows = (turSNSearchParams.getRows() != null && turSNSearchParams.getRows() > 0)
                                        ? turSNSearchParams.getRows()
                                        : 10;
                        turSNSearchParams.setP(page);
                        turSNSearchParams.setRows(rows);
                        TurSEParameters turSEParameters = new TurSEParameters(turSNSearchParams);

                        return turSolrInstanceProcess.initSolrInstance(turSEInstance, core).map(turSolrInstance -> {
                                TurSEResults results = turSolr.retrieveSolr(turSolrInstance,
                                                turSEParameters, "text");
                                return ResponseEntity.ok(results);
                        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        }

}
