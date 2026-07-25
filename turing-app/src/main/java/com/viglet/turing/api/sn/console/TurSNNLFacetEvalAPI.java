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

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.catalog.planning.TurCopilotPlanningComparison;
import com.viglet.turing.genai.catalog.planning.TurCopilotPlanningComparisonRequest;
import com.viglet.turing.genai.catalog.planning.TurCopilotPlanningComparisonService;
import com.viglet.turing.persistence.dto.agent.TurEvalDatasetDto;
import com.viglet.turing.sn.dsl.eval.TurNLFacetDatasetService;
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
    private final TurNLFacetDatasetService datasetService;
    private final TurCopilotPlanningComparisonService planningComparisonService;

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

    // ─────────────── T601 — NL→facet packs as reusable eval datasets ───────────────

    @Operation(summary = "Import an NL→facet eval pack as a reusable eval dataset")
    @PostMapping("/dataset")
    @Secured({"ROLE_ADMIN", "SN_ACCESS"})
    public ResponseEntity<TurEvalDatasetDto> importDataset(@RequestBody TurNLFacetEvalPack pack) {
        return ResponseEntity.ok(datasetService.importPack(pack));
    }

    @Operation(summary = "List the saved NL→facet eval datasets")
    @GetMapping("/dataset")
    @Secured({"ROLE_ADMIN", "SN_ACCESS"})
    public List<TurEvalDatasetDto> listDatasets() {
        return datasetService.list();
    }

    @Operation(summary = "Reconstruct the eval pack a saved NL→facet dataset holds")
    @GetMapping("/dataset/{id}")
    @Secured({"ROLE_ADMIN", "SN_ACCESS"})
    public ResponseEntity<TurNLFacetEvalPack> getDataset(@PathVariable String id) {
        return ResponseEntity.ok(datasetService.toPack(id));
    }

    @Operation(summary = "Run a saved NL→facet dataset and return the pass/fail report")
    @PostMapping("/dataset/{id}/run")
    @Secured({"ROLE_ADMIN", "SN_ACCESS"})
    public ResponseEntity<TurNLFacetEvalReport> runDataset(@PathVariable String id) {
        return ResponseEntity.ok(datasetService.run(id));
    }

    @Operation(summary = "Delete a saved NL→facet dataset")
    @DeleteMapping("/dataset/{id}")
    @Secured({"ROLE_ADMIN", "SN_ACCESS"})
    public ResponseEntity<Void> deleteDataset(@PathVariable String id) {
        datasetService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ─────────── T821 — comparative copilot planning-strategy eval ───────────

    @Operation(summary = "Run an NL→facet pack through every copilot planning strategy and "
            + "compare quality, latency and LLM cost side by side")
    @PostMapping("/planning-comparison")
    @Secured({"ROLE_ADMIN", "SN_ACCESS"})
    public ResponseEntity<TurCopilotPlanningComparison> comparePlanning(
            @RequestBody TurCopilotPlanningComparisonRequest request) {
        return ResponseEntity.ok(planningComparisonService.compare(request));
    }

    @Operation(summary = "Run a saved NL→facet dataset through every copilot planning strategy")
    @PostMapping("/dataset/{id}/planning-comparison")
    @Secured({"ROLE_ADMIN", "SN_ACCESS"})
    public ResponseEntity<TurCopilotPlanningComparison> comparePlanningForDataset(
            @PathVariable String id) {
        return ResponseEntity.ok(planningComparisonService.compare(
                TurCopilotPlanningComparisonRequest.of(datasetService.toPack(id))));
    }
}
