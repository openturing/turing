/*
 * Copyright (C) 2016-2026 the original author or authors.
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

package com.viglet.turing.api.sn.console;

import java.util.Objects;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.api.sn.bean.TurSNFieldRepairPayload;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.solr.TurSolrFieldAction;
import com.viglet.turing.solr.TurSolrUtils;
import com.viglet.turing.solr.bean.TurSolrFieldBean;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * REST endpoint that reconciles a single {@code TurSNSiteFieldExt} entry with
 * the corresponding Solr schema field. Each {@link com.viglet.turing.api.sn.bean.TurSNFieldRepairType}
 * targets one specific discrepancy reported by {@code /field/check}, keeping
 * unrelated attributes untouched. {@code REPAIR_ALL} chains every applicable
 * repair for the given core in one round-trip.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Slf4j
@RestController
@RequestMapping("/api/sn/{siteId}/field/repair")
@Tag(name = "Semantic Navigation Field Repair", description = "Semantic Navigation Repair Field API")
@Transactional
public class TurSNSiteFieldRepairAPI {
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSEInstanceRepository turSEInstanceRepository;

    public TurSNSiteFieldRepairAPI(TurSNSiteRepository turSNSiteRepository,
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
            TurSEInstanceRepository turSEInstanceRepository) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.turSEInstanceRepository = turSEInstanceRepository;
    }

    @Operation(summary = "Semantic Navigation Site Repair Field")
    @PostMapping
    public String turSNSiteFieldRepair(@PathVariable String siteId,
            @RequestBody TurSNFieldRepairPayload payload) {
        turSNSiteRepository.findById(siteId)
                .ifPresent(turSNSite -> turSNSiteFieldExtRepository.findById(payload.getId())
                        .ifPresent(fieldExt -> resolveInstance(turSNSite)
                                .ifPresent(turSEInstance -> applyRepair(payload, fieldExt, turSEInstance))));
        return "ok";
    }

    private Optional<TurSEInstance> resolveInstance(TurSNSite turSNSite) {
        return Optional.ofNullable(turSNSite.getTurSEInstance())
                .map(TurSEInstance::getId)
                .flatMap(turSEInstanceRepository::findById);
    }

    private void applyRepair(TurSNFieldRepairPayload payload, TurSNSiteFieldExt fieldExt,
            TurSEInstance turSEInstance) {
        switch (payload.getRepairType()) {
            case SE_CREATE_FIELD -> createSolrField(turSEInstance, payload.getCore(), fieldExt);
            case SE_CHANGE_TYPE -> changeSolrType(turSEInstance, payload.getCore(), fieldExt);
            case SE_ENABLE_MULTI_VALUE -> syncSolrMultiValue(turSEInstance, payload.getCore(), fieldExt);
            case SN_CHANGE_TYPE -> changeTuringTypeToString(fieldExt);
            case REPAIR_ALL -> repairAll(turSEInstance, payload.getCore(), fieldExt);
        }
    }

    /**
     * Solr-side: create the field from scratch using Turing's configured type
     * and multiValued setting. Used when the field is missing from the schema.
     */
    private void createSolrField(TurSEInstance turSEInstance, String coreName,
            TurSNSiteFieldExt fieldExt) {
        TurSolrUtils.addOrUpdateField(TurSolrFieldAction.ADD, turSEInstance, coreName,
                fieldExt.getName(), fieldExt.getType(), true,
                fieldExt.getMultiValued() == 1);
    }

    /**
     * Solr-side: replace the existing field, updating only the type to match
     * Turing's configuration. The current multiValued setting on Solr is
     * preserved so this operation never silently flips that flag.
     */
    private void changeSolrType(TurSEInstance turSEInstance, String coreName,
            TurSNSiteFieldExt fieldExt) {
        boolean preservedMultiValued = readSolrMultiValued(turSEInstance, coreName, fieldExt.getName())
                .orElse(fieldExt.getMultiValued() == 1);
        TurSolrUtils.addOrUpdateField(TurSolrFieldAction.REPLACE, turSEInstance, coreName,
                fieldExt.getName(), fieldExt.getType(), true, preservedMultiValued);
    }

    /**
     * Solr-side: replace the existing field, updating only multiValued to
     * match Turing's configuration. The current Solr type is preserved so
     * the type isn't changed by a multiValued repair.
     */
    private void syncSolrMultiValue(TurSEInstance turSEInstance, String coreName,
            TurSNSiteFieldExt fieldExt) {
        TurSEFieldType preservedType = readSolrType(turSEInstance, coreName, fieldExt.getName())
                .orElse(fieldExt.getType());
        TurSolrUtils.addOrUpdateField(TurSolrFieldAction.REPLACE, turSEInstance, coreName,
                fieldExt.getName(), preservedType, true,
                fieldExt.getMultiValued() == 1);
    }

    /**
     * Turing-side: when a facet field is misconfigured (e.g. facet=1 on a
     * TEXT field), normalize the Turing type to STRING so the facet works.
     * The Solr schema is not touched here — pair with {@code SE_CHANGE_TYPE}
     * (or {@code REPAIR_ALL}) to re-align the Solr field afterwards.
     */
    private void changeTuringTypeToString(TurSNSiteFieldExt fieldExt) {
        fieldExt.setType(TurSEFieldType.STRING);
        turSNSiteFieldExtRepository.save(fieldExt);
    }

    /**
     * Apply every fix needed to bring the (field, core) tuple to a fully
     * correct state, in the right order: normalize Turing's type first
     * (so subsequent Solr writes see the corrected type), then create or
     * replace the Solr field with Turing's full state.
     */
    private void repairAll(TurSEInstance turSEInstance, String coreName,
            TurSNSiteFieldExt fieldExt) {
        if (!isFacetConfigValid(fieldExt)) {
            changeTuringTypeToString(fieldExt);
        }

        if (!TurSolrUtils.existsField(turSEInstance, coreName, fieldExt.getName())) {
            createSolrField(turSEInstance, coreName, fieldExt);
            return;
        }

        TurSolrFieldBean current = TurSolrUtils.getField(turSEInstance, coreName, fieldExt.getName());
        boolean typeMatches = Objects.equals(current.getType(),
                TurSolrUtils.getSolrFieldType(fieldExt.getType()));
        boolean multiValuedMatches = current.isMultiValued() == (fieldExt.getMultiValued() == 1);

        if (typeMatches && multiValuedMatches) {
            return;
        }

        TurSolrUtils.addOrUpdateField(TurSolrFieldAction.REPLACE, turSEInstance, coreName,
                fieldExt.getName(), fieldExt.getType(), true,
                fieldExt.getMultiValued() == 1);
    }

    private boolean isFacetConfigValid(TurSNSiteFieldExt fieldExt) {
        return fieldExt.getFacet() != 1 || !TurSEFieldType.TEXT.equals(fieldExt.getType());
    }

    private Optional<Boolean> readSolrMultiValued(TurSEInstance turSEInstance, String coreName,
            String fieldName) {
        if (!TurSolrUtils.existsField(turSEInstance, coreName, fieldName)) {
            return Optional.empty();
        }
        return Optional.of(TurSolrUtils.getField(turSEInstance, coreName, fieldName).isMultiValued());
    }

    private Optional<TurSEFieldType> readSolrType(TurSEInstance turSEInstance, String coreName,
            String fieldName) {
        if (!TurSolrUtils.existsField(turSEInstance, coreName, fieldName)) {
            return Optional.empty();
        }
        String solrType = TurSolrUtils.getField(turSEInstance, coreName, fieldName).getType();
        if (solrType == null || solrType.isBlank()) {
            return Optional.empty();
        }
        for (TurSEFieldType type : TurSEFieldType.values()) {
            if (Objects.equals(TurSolrUtils.getSolrFieldType(type), solrType)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
