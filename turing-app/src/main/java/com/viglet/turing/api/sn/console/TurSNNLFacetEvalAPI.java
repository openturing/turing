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

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.sn.dsl.eval.TurNLFacetEvalPack;
import com.viglet.turing.sn.dsl.eval.TurNLFacetEvalReport;
import com.viglet.turing.sn.dsl.eval.TurNLFacetEvalService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * NL→facet query-parsing eval pack endpoint (T385 / §XX.5).
 *
 * <p>{@code POST /api/sn/nl-facet-eval} runs a {@link TurNLFacetEvalPack}
 * (declared schema + prose queries with golden filter/range/sort outputs)
 * through the configured default LLM and returns a {@link TurNLFacetEvalReport}.
 * This is how a faceted catalog hardens and regression-tests the parser that
 * replaces its hand-written {@code nlFilters} — the runtime analog of the agent
 * CI gate (Block&nbsp;K) for the NL→facet path.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@RestController
@RequestMapping("/api/sn/nl-facet-eval")
@Tag(name = "Semantic Navigation NL→Facet Eval",
        description = "Run an NL→facet query-parsing eval pack against the configured LLM")
@RequiredArgsConstructor
public class TurSNNLFacetEvalAPI {

    private final TurNLFacetEvalService evalService;

    @Operation(summary = "True when a usable default LLM is configured (the eval can run)")
    @GetMapping("/available")
    @Secured({"ROLE_ADMIN", "SN_ACCESS"})
    public Map<String, Boolean> available() {
        return Map.of("available", evalService.isAvailable());
    }

    @Operation(summary = "Run an NL→facet eval pack and return the pass/fail report")
    @PostMapping
    @Secured({"ROLE_ADMIN", "SN_ACCESS"})
    public ResponseEntity<TurNLFacetEvalReport> run(@RequestBody TurNLFacetEvalPack pack) {
        return ResponseEntity.ok(evalService.run(pack));
    }
}
