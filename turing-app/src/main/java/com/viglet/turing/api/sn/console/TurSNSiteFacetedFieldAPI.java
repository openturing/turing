/*
 * Copyright (C) 2016-2024 the original author or authors.
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
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * @author Alexandre Oliveira
 * @since 0.3.9
 */
@Slf4j
@RestController
@RequestMapping("/api/sn/{snSiteId}/facet")
@Tag(name = "Semantic Navigation Faceted Field", description = "Semantic Navigation Faceted Field API")
public class TurSNSiteFacetedFieldAPI {
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    @Inject
    public TurSNSiteFacetedFieldAPI(TurSNSiteRepository turSNSiteRepository,
                                    TurSNSiteFieldExtRepository turSNSiteFieldExtRepository) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
    }

    @Operation(summary = "Semantic Navigation Site Faceted Field List")
    @GetMapping
    public List<TurSNSiteFieldExt> turSNSiteFacetdFieldExtList(@PathVariable String snSiteId) {
        System.out.println(snSiteId);
        turSNSiteRepository.findById(snSiteId).ifPresent(turSNSite -> System.out.println(turSNSite.getName()));
        return turSNSiteRepository.findById(snSiteId).map(turSNSite -> {
            System.out.println("BBB");
            return turSNSiteFieldExtRepository
                .findByTurSNSiteAndFacetAndEnabled(turSNSite, 1, 1);
        }).orElse(Collections.emptyList());

    }
}