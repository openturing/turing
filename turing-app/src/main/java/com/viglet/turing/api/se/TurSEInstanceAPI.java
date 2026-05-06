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
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
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
import com.viglet.turing.domain.sn.TurSNSiteLocaleRepositoryPort;
import com.viglet.turing.persistence.dto.se.TurSEInstanceDto;
import com.viglet.turing.persistence.mapper.se.TurSEInstanceMapper;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import com.viglet.turing.solr.bean.TurSECoreInfo;
import com.viglet.turing.solr.bean.TurSECoreSiteUsage;
import com.viglet.turing.solr.source.TurSolrInstanceSource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/se")
@Tag(name = "Search Engine", description = "Search Engine API")
public class TurSEInstanceAPI {
        private final TurSolrInstanceSource seInstanceSource;
        private final TurSEInstanceMapper turSEInstanceMapper;
        private final TurSolrInstanceProcess turSolrInstanceProcess;
        private final TurSolr turSolr;
        private final TurSNSiteLocaleRepositoryPort turSNSiteLocaleRepositoryPort;
        private final TurSearchEnginePluginFactory pluginFactory;

        public TurSEInstanceAPI(TurSolrInstanceSource seInstanceSource,
                        TurSEInstanceMapper turSEInstanceMapper,
                        TurSolrInstanceProcess turSolrInstanceProcess, TurSolr turSolr,
                        TurSNSiteLocaleRepositoryPort turSNSiteLocaleRepositoryPort,
                        TurSearchEnginePluginFactory pluginFactory) {
                this.seInstanceSource = seInstanceSource;
                this.turSEInstanceMapper = turSEInstanceMapper;
                this.turSolrInstanceProcess = turSolrInstanceProcess;
                this.turSolr = turSolr;
                this.turSNSiteLocaleRepositoryPort = turSNSiteLocaleRepositoryPort;
                this.pluginFactory = pluginFactory;
        }

        private ResponseEntity<TurSEInstanceDto> readOnlyResponse() {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        @Operation(summary = "Search Engine List")
        @GetMapping
        @Secured({"ROLE_ADMIN", "SE_VIEW"})
        public List<TurSEInstanceDto> turSEInstanceList() {
                return turSEInstanceMapper.toDtoList(
                                this.seInstanceSource.findAll().stream()
                                                .sorted(java.util.Comparator.comparing(
                                                                TurSEInstance::getTitle,
                                                                String.CASE_INSENSITIVE_ORDER))
                                                .toList());
        }

        @Operation(summary = "Search Engine structure")
        @GetMapping("/structure")
        @Secured({"ROLE_ADMIN", "SE_VIEW"})
        public TurSEInstanceDto turSearchEngineStructure() {
                TurSEInstance turSEInstance = new TurSEInstance();
                turSEInstance.setTurSEVendor(new TurSEVendor());
                return turSEInstanceMapper.toDto(turSEInstance);

        }

        @Operation(summary = "Show a Search Engine")
        @GetMapping("/{id}")
        @Secured({"ROLE_ADMIN", "SE_VIEW"})
        public TurSEInstanceDto turSEInstanceGet(@PathVariable String id) {
                return turSEInstanceMapper
                                .toDto(this.seInstanceSource.findById(id).orElse(new TurSEInstance()));
        }

        @Operation(summary = "Update a Search Engine")
        @PutMapping("/{id}")
        @Secured({"ROLE_ADMIN", "SE_EDIT"})
        public ResponseEntity<TurSEInstanceDto> turSEInstanceUpdate(@PathVariable String id,
                        @RequestBody TurSEInstanceDto turSEInstanceDto) {
                if (seInstanceSource.isReadOnly()) {
                        return readOnlyResponse();
                }
                TurSEInstance source = turSEInstanceMapper.toEntity(turSEInstanceDto);
                return ResponseEntity.ok(seInstanceSource.findById(id).map(existing -> {
                        turSEInstanceMapper.updateEntity(source, existing);
                        seInstanceSource.save(existing);
                        return turSEInstanceMapper.toDto(existing);
                }).orElse(new TurSEInstanceDto()));
        }

        @Transactional
        @Operation(summary = "Delete a Search Engine")
        @DeleteMapping("/{id}")
        @Secured({"ROLE_ADMIN", "SE_DELETE"})
        public ResponseEntity<Boolean> turSEInstanceDelete(@PathVariable String id) {
                if (seInstanceSource.isReadOnly()) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(false);
                }
                this.seInstanceSource.delete(id);
                return ResponseEntity.ok(true);
        }

        @Operation(summary = "Create a Search Engine")
        @PostMapping
        @Secured({"ROLE_ADMIN", "SE_CREATE"})
        public ResponseEntity<TurSEInstanceDto> turSEInstanceAdd(@RequestBody TurSEInstanceDto turSEInstanceDto) {
                if (seInstanceSource.isReadOnly()) {
                        return readOnlyResponse();
                }
                TurSEInstance turSEInstance = turSEInstanceMapper.toEntity(turSEInstanceDto);
                this.seInstanceSource.save(turSEInstance);
                return ResponseEntity.ok(turSEInstanceMapper.toDto(turSEInstance));
        }

        @Operation(summary = "List Solr cores/collections for a Search Engine")
        @GetMapping("/{id}/cores")
        @Secured({"ROLE_ADMIN", "SE_VIEW"})
        public ResponseEntity<List<TurSECoreInfo>> turSEInstanceCores(@PathVariable String id) {
                return seInstanceSource.findById(id).map(turSEInstance -> {
                        List<TurSECoreInfo> cores = pluginFactory.getPluginForInstance(turSEInstance).listIndexes(turSEInstance).stream()
                                        .map(core -> new TurSECoreInfo(core.name(), core.numDocs(),
                                                        turSNSiteLocaleRepositoryPort.findCoreUsage(core.name()).stream()
                                                                        .map(usage -> new TurSECoreSiteUsage(
                                                                                        usage.snSiteId(),
                                                                                        usage.snSiteName(),
                                                                                        usage.localeId(),
                                                                                        usage.language().toString()))
                                                                        .toList()))
                                        .toList();
                        return ResponseEntity.ok(cores);
                }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        }

        @Operation(summary = "Create a new core/collection in a Search Engine")
        @PostMapping("/{id}/cores")
        @Secured({"ROLE_ADMIN", "SE_CREATE"})
        public ResponseEntity<Void> turSEInstanceCreateCore(@PathVariable String id,
                        @RequestBody TurSECreateCoreRequest request) {
                if (request.name == null || request.name.isBlank()) {
                        return ResponseEntity.badRequest().build();
                }
                if (request.locale == null || request.locale.isBlank()) {
                        return ResponseEntity.badRequest().build();
                }
                return seInstanceSource.findById(id).map(turSEInstance -> {
                        Locale language = Locale.forLanguageTag(request.locale.replace("_", "-"));
                        pluginFactory.getPluginForInstance(turSEInstance)
                                        .createStandaloneIndex(turSEInstance, language, request.name.trim());
                        return ResponseEntity.status(HttpStatus.CREATED).<Void>build();
                }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        }

        public record TurSECreateCoreRequest(String name, String locale) {}

        @Operation(summary = "Delete a Solr core/collection from a Search Engine")
        @DeleteMapping("/{id}/cores/{core}")
        @Secured({"ROLE_ADMIN", "SE_DELETE"})
        public ResponseEntity<Void> turSEInstanceDeleteCore(@PathVariable String id, @PathVariable String core) {
                return seInstanceSource.findById(id).map(turSEInstance -> {
                        if (turSNSiteLocaleRepositoryPort.existsByCore(core)) {
                                return ResponseEntity.status(HttpStatus.CONFLICT).<Void>build();
                        }
                        pluginFactory.getPluginForInstance(turSEInstance).deleteIndex(turSEInstance, core);
                        return ResponseEntity.noContent().<Void>build();
                }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).<Void>build());
        }

        @Operation(summary = "Remove all documents from a Solr core/collection")
        @DeleteMapping("/{id}/cores/{core}/documents")
        @Secured({"ROLE_ADMIN", "SE_DELETE"})
        public ResponseEntity<Void> turSEInstanceClearCore(@PathVariable String id, @PathVariable String core) {
                return seInstanceSource.findById(id).map(turSEInstance -> {
                        pluginFactory.getPluginForInstance(turSEInstance).clearIndex(turSEInstance, core);
                        return ResponseEntity.noContent().<Void>build();
                }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).<Void>build());
        }

        @Operation(summary = "Get system information for a Search Engine instance")
        @GetMapping("/{id}/system-info")
        @Secured({"ROLE_ADMIN", "SE_VIEW"})
        public ResponseEntity<java.util.Map<String, String>> turSEInstanceSystemInfo(@PathVariable String id) {
                return seInstanceSource.findById(id).map(turSEInstance -> {
                        java.util.Map<String, String> info = pluginFactory
                                        .getPluginForInstance(turSEInstance)
                                        .getSystemInfo(turSEInstance);
                        return ResponseEntity.ok(info);
                }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        }

        @GetMapping("/{id}/{core}/select")
        @Secured({"ROLE_ADMIN", "SE_VIEW"})
        public ResponseEntity<TurSEResults> turSEInstanceSelect(@PathVariable String id, @PathVariable String core,
                        @ModelAttribute TurSNSearchParams turSNSearchParams) {
                return seInstanceSource.findById(id).map(turSEInstance -> {

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
