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

package com.viglet.turing.api.sn.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.api.sn.bean.TurSNFieldExtCheck;
import com.viglet.turing.api.sn.bean.TurSolrCoreExists;
import com.viglet.turing.api.sn.bean.TurSolrFieldCore;
import com.viglet.turing.api.sn.bean.TurSolrFieldStatus;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.solr.TurSolrUtils;
import com.viglet.turing.solr.bean.TurSolrFieldBean;
import com.viglet.turing.spring.utils.TurPersistenceUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/sn/{siteId}/field/check")
@Tag(name = "Semantic Navigation Field Check Ext", description = "Semantic Navigation Field Check Ext API")
public class TurSNSiteFieldCheckAPI {
        private final TurSNSiteRepository turSNSiteRepository;
        private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
        private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;

        public TurSNSiteFieldCheckAPI(TurSNSiteRepository turSNSiteRepository,
                        TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
                        TurSNSiteLocaleRepository turSNSiteLocaleRepository) {
                this.turSNSiteRepository = turSNSiteRepository;
                this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
                this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        }

        @Operation(summary = "Semantic Navigation Site Field Ext Check List")
        @GetMapping
        public TurSNFieldExtCheck turSNSiteFieldExtCheckList(@PathVariable String siteId) {
                return turSNSiteRepository.findById(siteId).map(turSNSite -> TurSNFieldExtCheck.builder()
                                .cores(coresExist(turSNSite))
                                .fields(fieldsCheck(turSNSite))
                                .build())
                                .orElse(TurSNFieldExtCheck.builder().build());
        }

        private List<TurSolrFieldStatus> fieldsCheck(TurSNSite turSNSite) {
                List<TurSolrFieldStatus> fieldsExist = new ArrayList<>();
                TurSEInstance turSEInstance = turSNSite.getTurSEInstance();
                List<TurSNSiteLocale> siteLocales = turSNSiteLocaleRepository.findByTurSNSite(turSNSite);

                turSNSiteFieldExtRepository
                                .findByTurSNSite(TurPersistenceUtils.orderByNameIgnoreCase(), turSNSite)
                                .forEach(turSNSiteFieldExt -> {
                                        TurSolrFieldStatus fieldStatus = validateField(turSNSiteFieldExt, turSEInstance,
                                                        siteLocales);
                                        fieldsExist.add(fieldStatus);
                                });

                return fieldsExist;
        }

        private TurSolrFieldStatus validateField(TurSNSiteFieldExt turSNSiteFieldExt,
                        TurSEInstance turSEInstance, List<TurSNSiteLocale> siteLocales) {
                List<TurSolrFieldCore> cores = new ArrayList<>();
                boolean allCoresValid = true;

                for (TurSNSiteLocale locale : siteLocales) {
                        TurSolrFieldCore coreStatus = validateFieldInCore(turSNSiteFieldExt, locale, turSEInstance);
                        cores.add(coreStatus);
                        if (!coreStatus.isCorrect()) {
                                allCoresValid = false;
                        }
                }

                return TurSolrFieldStatus.builder()
                                .id(turSNSiteFieldExt.getId())
                                .externalId(turSNSiteFieldExt.getExternalId())
                                .name(turSNSiteFieldExt.getName())
                                .facetIsCorrect(isFacetConfigValid(turSNSiteFieldExt))
                                .cores(cores)
                                .correct(allCoresValid)
                                .build();
        }

        private TurSolrFieldCore validateFieldInCore(TurSNSiteFieldExt turSNSiteFieldExt,
                        TurSNSiteLocale locale, TurSEInstance turSEInstance) {
                String coreName = locale.getCore();

                if (!fieldExists(turSNSiteFieldExt, locale, turSEInstance)) {
                        return buildMissingFieldCore(coreName);
                }

                TurSolrFieldBean solrField = TurSolrUtils.getField(turSEInstance, coreName,
                                turSNSiteFieldExt.getName());
                return buildFieldCore(coreName, turSNSiteFieldExt, solrField);
        }

        private TurSolrFieldCore buildFieldCore(String coreName, TurSNSiteFieldExt turSNSiteFieldExt,
                        TurSolrFieldBean solrField) {
                boolean multiValuedIsCorrect = validateMultiValued(turSNSiteFieldExt, solrField);
                boolean typeIsCorrect = validateType(turSNSiteFieldExt, solrField);
                boolean isCorrect = multiValuedIsCorrect && typeIsCorrect;

                return TurSolrFieldCore.builder()
                                .name(coreName)
                                .exists(true)
                                .type(solrField.getType())
                                .typeIsCorrect(typeIsCorrect)
                                .multiValued(solrField.isMultiValued())
                                .multiValuedIsCorrect(multiValuedIsCorrect)
                                .correct(isCorrect)
                                .build();
        }

        private TurSolrFieldCore buildMissingFieldCore(String coreName) {
                return TurSolrFieldCore.builder()
                                .name(coreName)
                                .exists(false)
                                .typeIsCorrect(false)
                                .multiValuedIsCorrect(false)
                                .correct(false)
                                .build();
        }

        private boolean validateMultiValued(TurSNSiteFieldExt turSNSiteFieldExt, TurSolrFieldBean solrField) {
                boolean isMultiValued = (turSNSiteFieldExt.getMultiValued() == 1);
                return (isMultiValued && solrField.isMultiValued()) || (!isMultiValued && !solrField.isMultiValued());
        }

        private boolean validateType(TurSNSiteFieldExt turSNSiteFieldExt, TurSolrFieldBean solrField) {
                String expectedSolrType = TurSolrUtils.getSolrFieldType(turSNSiteFieldExt.getType());
                return Objects.equals(solrField.getType(), expectedSolrType);
        }

        private boolean isFacetConfigValid(TurSNSiteFieldExt turSNSiteFieldExt) {
                return turSNSiteFieldExt.getFacet() != 1 || !turSNSiteFieldExt.getType().equals(TurSEFieldType.TEXT);
        }

        private static boolean fieldExists(TurSNSiteFieldExt turSNSiteFieldExt, TurSNSiteLocale turSNSiteLocale,
                        TurSEInstance turSEInstance) {
                return TurSolrUtils.existsField(turSEInstance, turSNSiteLocale.getCore(),
                                turSNSiteFieldExt.getName());
        }

        private List<TurSolrCoreExists> coresExist(TurSNSite turSNSite) {
                List<TurSolrCoreExists> coresExist = new ArrayList<>();
                turSNSiteLocaleRepository.findByTurSNSite(turSNSite)
                                .forEach(turSNSiteLocale -> coresExist.add(TurSolrCoreExists.builder()
                                                .name(turSNSiteLocale.getCore())
                                                .exists(TurSolrUtils.coreExists(turSNSite.getTurSEInstance(),
                                                                turSNSiteLocale.getCore()))
                                                .build()));
                return coresExist;
        }
}
