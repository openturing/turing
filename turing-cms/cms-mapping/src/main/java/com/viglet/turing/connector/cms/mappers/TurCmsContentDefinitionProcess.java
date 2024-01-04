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
package com.viglet.turing.connector.cms.mappers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.client.sn.job.TurSNAttributeSpec;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
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
public class TurCmsContentDefinitionProcess {
    private IHandlerConfiguration config;
    private Path workingDirectory;
    private Path json;

    public TurCmsContentDefinitionProcess(IHandlerConfiguration config, Path workingDirectory) {
        this.config = config;
        this.workingDirectory = workingDirectory;
        this.json = getContentMappingPath(config, workingDirectory);

    }

    private Optional<TurCmsTargetAttr> findByNameFromTargetAttrs(final List<TurCmsTargetAttr> turCmsTargetAttrs,
                                                                 final String name) {
        return turCmsTargetAttrs.stream().filter(o -> o.getName().equals(name)).findFirst();
    }

    private Optional<TurCmsModel> findByNameFromModel(final List<TurCmsModel> turCmsModels,
                                                      final String name) {
        return turCmsModels.stream().filter(o -> o.getType().equals(name)).findFirst();
    }
    public List<TurSNAttributeSpec> getTargetAttrDefinitions() {
        return getMappingDefinitions().getTargetAttrDefinitions();
    }
    public TurCmsModel findByNameFromModelWithDefinition(String modelName) {
        return Optional.ofNullable(json).map(path -> {
            TurCmsContentMapping turCmsContentMapping = getMappingDefinitions();
            return findByNameFromModel(turCmsContentMapping.getModels(), modelName).map(model -> {
                List<TurCmsTargetAttr> turCmsTargetAttrs = new ArrayList<>();
                turCmsContentMapping.getTargetAttrDefinitions().forEach(targetAttrDefinition ->
                        findByNameFromTargetAttrs(model.getTargetAttrs(), targetAttrDefinition.getName())
                                .ifPresentOrElse(targetAttr ->
                                                turCmsTargetAttrs.add(
                                                        setTargetAttrFromDefinition(targetAttrDefinition, targetAttr)),
                                        () -> {
                                            if (targetAttrDefinition.isMandatory()) {
                                                turCmsTargetAttrs.add(
                                                        setTargetAttrFromDefinition(targetAttrDefinition,
                                                                new TurCmsTargetAttr()));
                                            }
                                        }));
                model.setTargetAttrs(turCmsTargetAttrs);
                return model;

            }).orElse(new TurCmsModel());
        }).orElse(new TurCmsModel());
    }

    private TurCmsTargetAttr setTargetAttrFromDefinition(TurSNAttributeSpec turSNAttributeSpec,
                                                         TurCmsTargetAttr targetAttr) {
        if (StringUtils.isBlank(targetAttr.getName())) {
            targetAttr.setName(turSNAttributeSpec.getName());
        }
        if (StringUtils.isNotBlank(turSNAttributeSpec.getClassName())) {
            if (CollectionUtils.isEmpty(targetAttr.getSourceAttrs())) {
                List<TurCmsSourceAttr> turCmsSourceAttrs = Collections.singletonList(TurCmsSourceAttr.builder()
                        .className(turSNAttributeSpec.getClassName())
                        .uniqueValues(false)
                        .convertHtmlToText(false)
                        .build());
                targetAttr.setSourceAttrs(turCmsSourceAttrs);
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

    public TurCmsContentMapping getMappingDefinitions() {
        return Optional.ofNullable(json).map(path -> {
            try {
                return new ObjectMapper().readValue(path.toFile(), TurCmsContentMapping.class);
            } catch (IOException e) {
                log.error("Can not read mapping file, because is not valid: " + path.toFile().getAbsolutePath(), e);
                return new TurCmsContentMapping();
            }
        }).orElse(new TurCmsContentMapping());
    }

    private Path getContentMappingPath(IHandlerConfiguration config, Path workingDirectory) {
        String contentMappingFile = config.getMappingFile();
        if (workingDirectory != null) {
            Path path = Paths.get(workingDirectory.toAbsolutePath().toString(),
                    contentMappingFile);
            if (path.toFile().isFile() && path.toFile().canRead()) {
                return path;
            } else {
                log.error("Can not read mapping file, because not exist: " + path.toFile().getAbsolutePath());
            }
        } else {
            log.error("Can not read mapping file, because WorkDirectory is empty: " + contentMappingFile);
        }
        log.error("Mapping definitions are not loaded properly from mappingsXML: " + contentMappingFile);
        return null;
    }
}
