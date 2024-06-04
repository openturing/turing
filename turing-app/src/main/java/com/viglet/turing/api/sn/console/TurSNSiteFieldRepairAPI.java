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

import com.google.inject.Inject;
import com.viglet.turing.api.sn.bean.TurSNFieldRepairPayload;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.solr.TurSolrFieldAction;
import com.viglet.turing.solr.TurSolrUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/sn/{siteId}/field/repair")
@Tag(name = "Semantic Navigation Field Repair", description = "Semantic Navigation Repair Field API")
public class TurSNSiteFieldRepairAPI {
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSEInstanceRepository turSEInstanceRepository;

    @Inject
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
                                                   @RequestBody TurSNFieldRepairPayload turSNFieldRepairPayload) {
        turSNSiteRepository.findById(siteId).ifPresent(turSNSite -> {
            turSNSiteFieldExtRepository.findById(turSNFieldRepairPayload.getId()).ifPresent(turSNSiteFieldExt ->
                    turSEInstanceRepository
                            .findById(turSNSite.getTurSEInstance().getId()).ifPresent(turSEInstance -> {
                                switch (turSNFieldRepairPayload.getRepairType()) {
                                    case SE_CREATE_FIELD -> TurSolrUtils.addOrUpdateField(
                                            TurSolrFieldAction.ADD,
                                            turSEInstance,
                                            turSNFieldRepairPayload.getCore(),
                                            turSNSiteFieldExt.getName(),
                                            turSNSiteFieldExt.getType(),
                                            true,
                                            turSNSiteFieldExt.getMultiValued() == 1);
                                    case SE_CHANGE_TYPE, SE_ENABLE_MULTI_VALUE -> TurSolrUtils.addOrUpdateField(
                                            TurSolrFieldAction.REPLACE,
                                            turSEInstance,
                                            turSNFieldRepairPayload.getCore(),
                                            turSNSiteFieldExt.getName(),
                                            turSNSiteFieldExt.getType(),
                                            true,
                                            turSNSiteFieldExt.getMultiValued() == 1);
                                    case SN_CHANGE_TYPE -> turSNSiteFieldExt.setType(TurSEFieldType.STRING);
                                }
                            }));

        });
        return "ok";
    }
}