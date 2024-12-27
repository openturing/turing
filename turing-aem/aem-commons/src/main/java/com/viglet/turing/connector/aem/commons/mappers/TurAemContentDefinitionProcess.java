/*
 * Copyright (C) 2016-2023 the original author or authors.
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
package com.viglet.turing.connector.aem.commons.mappers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.client.sn.job.TurSNAttributeSpec;
import com.viglet.turing.connector.aem.commons.config.IAemConfiguration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Getter
@Setter
@Slf4j
public class TurAemContentDefinitionProcess {
    private IAemConfiguration config;
    private Path workingDirectory;
    private Path jsonFile;
    private String json;


    public TurAemContentDefinitionProcess(IAemConfiguration config, Path workingDirectory) {
        this.config = config;
        this.workingDirectory = workingDirectory;
        this.jsonFile = getContentMappingPath(workingDirectory);
        this.json = null;

    }

    public TurAemContentDefinitionProcess(String json) {
        this.config = null;
        this.workingDirectory = null;
        this.jsonFile = null;
        this.json = json;

    }

    private Optional<TurAemTargetAttr> findByNameFromTargetAttrs(final List<TurAemTargetAttr> turCmsTargetAttrs,
                                                                 final String name) {
        return turCmsTargetAttrs.stream().filter(o -> o.getName().equals(name)).findFirst();
    }

    private Optional<TurAemModel> findByNameFromModel(final List<TurAemModel> turCmsModels,
                                                      final String name) {
        return turCmsModels != null ?
                turCmsModels.stream().filter(o -> o != null && o.getType().equals(name)).findFirst() :
                Optional.empty();
    }
    public List<TurSNAttributeSpec> getTargetAttrDefinitions() {
        return getMappingDefinitions().map(TurAemContentMapping::getTargetAttrDefinitions).orElse(new ArrayList<>());

    }
    public String getDeltaClassName() {
        return getMappingDefinitions().map(TurAemContentMapping::getDeltaClassName).orElse(null);
    }
    public Optional<TurAemModel> findByNameFromModelWithDefinition(String modelName) {
          return getMappingDefinitions()
                  .flatMap(turCmsContentMapping -> findByNameFromModel(turCmsContentMapping.getModels(), modelName)
                  .map(model -> {
                      List<TurAemTargetAttr> turCmsTargetAttrs =
                              new ArrayList<>(addTargetAttrFromDefinition(model, turCmsContentMapping));
                      model.getTargetAttrs().forEach(turCmsTargetAttr -> {
                          if (turCmsTargetAttrs.stream()
                                  .noneMatch(o -> o.getName().equals(turCmsTargetAttr.getName())))
                              turCmsTargetAttrs.add(turCmsTargetAttr);
                      });
                      model.setTargetAttrs(turCmsTargetAttrs);
                      return model;
                  }));
    }

    private List<TurAemTargetAttr> addTargetAttrFromDefinition(TurAemModel model,
                                                               TurAemContentMapping turCmsContentMapping) {
        List<TurAemTargetAttr> turCmsTargetAttrs = new ArrayList<>();
        turCmsContentMapping.getTargetAttrDefinitions()
                .forEach(targetAttrDefinition ->
                        findByNameFromTargetAttrs(model.getTargetAttrs(), targetAttrDefinition.getName())
                                .ifPresentOrElse(targetAttr ->
                                                turCmsTargetAttrs.add(
                                                        setTargetAttrFromDefinition(targetAttrDefinition, targetAttr)),
                                        () -> {
                                            if (targetAttrDefinition.isMandatory()) {
                                                turCmsTargetAttrs.add(
                                                        setTargetAttrFromDefinition(targetAttrDefinition,
                                                                new TurAemTargetAttr()));
                                            }
                                        }));

        return turCmsTargetAttrs;
    }

    private TurAemTargetAttr setTargetAttrFromDefinition(TurSNAttributeSpec turSNAttributeSpec,
                                                         TurAemTargetAttr targetAttr) {
        if (StringUtils.isBlank(targetAttr.getName())) {
            targetAttr.setName(turSNAttributeSpec.getName());
        }
        if (StringUtils.isNotBlank(turSNAttributeSpec.getClassName())) {
            if (CollectionUtils.isEmpty(targetAttr.getSourceAttrs())) {
                List<TurAemSourceAttr> sourceAttrs = Collections.singletonList(TurAemSourceAttr.builder()
                        .className(turSNAttributeSpec.getClassName())
                        .uniqueValues(false)
                        .convertHtmlToText(false)
                        .build());
                targetAttr.setSourceAttrs(sourceAttrs);
                targetAttr.setClassName(turSNAttributeSpec.getClassName());
            } else {
                targetAttr.getSourceAttrs().stream()
                        .filter(turCmsSourceAttr -> Objects.nonNull(turCmsSourceAttr)
                                && StringUtils.isBlank(turCmsSourceAttr.getClassName()))
                        .forEach(turCmsSourceAttr ->
                                turCmsSourceAttr.setClassName(turSNAttributeSpec.getClassName()));
            }
        }
        targetAttr.setDescription(turSNAttributeSpec.getDescription());
        targetAttr.setFacet(turSNAttributeSpec.isFacet());
        targetAttr.setFacetName(turSNAttributeSpec.getFacetName());
        targetAttr.setMandatory(turSNAttributeSpec.isMandatory());
        targetAttr.setMultiValued(turSNAttributeSpec.isMultiValued());
        targetAttr.setType(turSNAttributeSpec.getType());
        return targetAttr;

    }

    public Optional<TurAemContentMapping> getMappingDefinitions() {
        return Optional.ofNullable(json).map(TurAemContentDefinitionProcess::readJsonMapping)
                .orElse(Optional.ofNullable(jsonFile)
                        .flatMap(TurAemContentDefinitionProcess::readJsonMapping));
    }

    private static Optional<TurAemContentMapping> readJsonMapping(Path path) {
        try {
            return Optional.of(new ObjectMapper().readValue(path.toFile(), TurAemContentMapping.class));
        } catch (IOException e) {
            log.error("Can not read mapping file, because is not valid: {}", path.toFile().getAbsolutePath(), e);
            return Optional.empty();
        }
    }

    private static Optional<TurAemContentMapping> readJsonMapping(String json) {
        try {
            return Optional.of(new ObjectMapper().readValue(json, TurAemContentMapping.class));
        } catch (IOException e) {
            log.error("Can not read mapping,  because is not valid.", e);
            return Optional.empty();
        }
    }

    private Path getContentMappingPath(Path workingDirectory) {
        String contentMappingFile = config.getMappingFile();
        if (workingDirectory != null) {
            Path path = Paths.get(workingDirectory.toAbsolutePath().toString(),
                    contentMappingFile);
            if (path.toFile().isFile() && path.toFile().canRead()) {
                return path;
            } else {
                log.error("Can not read mapping file, because not exist: {}", path.toFile().getAbsolutePath());
            }
        } else {
            log.error("Can not read mapping file, because WorkDirectory is empty: {}", contentMappingFile);
        }
        log.error("Mapping definitions are not loaded properly from mappingsXML: {}", contentMappingFile);
        return null;
    }
}
