/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn.dsl.eval;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.persistence.dto.agent.TurEvalDatasetDto;
import com.viglet.turing.persistence.model.agent.TurAgentEvalExpectedOutcome;
import com.viglet.turing.persistence.model.agent.TurEvalDataset;
import com.viglet.turing.persistence.model.agent.TurEvalDatasetRow;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRowRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * T601 / §XXXIII.16 — migrates the Block&nbsp;R NL→facet eval pack onto the
 * shared {@link TurEvalDataset} model, so chat-flow and SN evals share one
 * dataset store (and, via {@code TurNLFacetGrader}, one grader registry).
 *
 * <p>An imported {@link TurNLFacetEvalPack} becomes a reusable, versioned
 * dataset: the pack-level {@code index}/{@code locale}/{@code schema} are stored
 * once on the dataset's {@code metadataJson} (kind {@value #KIND}); each case
 * becomes a {@link TurEvalDatasetRow} whose {@code seedTurnsJson} holds the prose
 * query and whose {@code metadataJson} holds the query + golden expectation. The
 * dataset round-trips losslessly back into a pack via {@link #toPack}, which the
 * intact Block&nbsp;R {@link TurNLFacetEvalService} then runs (an additive bridge
 * — the {@code POST /api/sn/nl-facet-eval} pack path is untouched).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurNLFacetDatasetService {

    /** Discriminator stored in {@code metadataJson.kind} for NL→facet datasets. */
    public static final String KIND = "nl-facet";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TurEvalDatasetRepository datasetRepository;
    private final TurEvalDatasetRowRepository datasetRowRepository;
    private final TurNLFacetEvalService evalService;

    public TurNLFacetDatasetService(TurEvalDatasetRepository datasetRepository,
            TurEvalDatasetRowRepository datasetRowRepository,
            TurNLFacetEvalService evalService) {
        this.datasetRepository = datasetRepository;
        this.datasetRowRepository = datasetRowRepository;
        this.evalService = evalService;
    }

    /**
     * Persists {@code pack} as a new NL→facet {@link TurEvalDataset} (one row per
     * case). Returns the detail view (rows included).
     */
    @Transactional
    public TurEvalDatasetDto importPack(TurNLFacetEvalPack pack) {
        if (pack == null || pack.cases().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "NL→facet eval pack has no cases");
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        TurEvalDataset dataset = new TurEvalDataset();
        dataset.setName(pack.name() == null || pack.name().isBlank()
                ? "nl-facet-dataset" : pack.name());
        dataset.setDescription("NL→facet eval pack (index=" + pack.index() + ")");
        dataset.setVersion(1);
        dataset.setCreatedAt(now);
        dataset.setUpdatedAt(now);
        dataset.setMetadataJson(packMetadata(pack));

        int order = 0;
        for (TurNLFacetEvalCase evalCase : pack.cases()) {
            TurEvalDatasetRow row = new TurEvalDatasetRow();
            row.setName(evalCase.name());
            row.setSeedTurnsJson(singleTurn(evalCase.query()));
            row.setExpectedOutcome(TurAgentEvalExpectedOutcome.ANY);
            row.setTags(KIND);
            row.setMetadataJson(caseMetadata(evalCase));
            row.setSortOrder(order++);
            row.setTurEvalDataset(dataset);
            dataset.getRows().add(row);
        }
        dataset = datasetRepository.save(dataset);
        List<com.viglet.turing.persistence.dto.agent.TurEvalDatasetRowDto> rows = dataset.getRows()
                .stream()
                .sorted(java.util.Comparator.comparingInt(TurEvalDatasetRow::getSortOrder))
                .map(com.viglet.turing.persistence.dto.agent.TurEvalDatasetRowDto::fromEntity)
                .toList();
        return TurEvalDatasetDto.detail(dataset, rows);
    }

    /** All NL→facet datasets (kind {@value #KIND}) as summaries, name-ordered. */
    public List<TurEvalDatasetDto> list() {
        return datasetRepository.findByOrderByNameAsc().stream()
                .filter(TurNLFacetDatasetService::isNlFacet)
                .map(d -> TurEvalDatasetDto.summary(d,
                        datasetRowRepository.findByTurEvalDataset_IdOrderBySortOrderAsc(d.getId()).size()))
                .toList();
    }

    /** Detail view of one NL→facet dataset (with rows). 404 when missing / not NL→facet. */
    public TurEvalDatasetDto get(String datasetId) {
        TurEvalDataset dataset = requireNlFacet(datasetId);
        List<com.viglet.turing.persistence.dto.agent.TurEvalDatasetRowDto> rows =
                datasetRowRepository.findByTurEvalDataset_IdOrderBySortOrderAsc(datasetId).stream()
                        .map(com.viglet.turing.persistence.dto.agent.TurEvalDatasetRowDto::fromEntity)
                        .toList();
        return TurEvalDatasetDto.detail(dataset, rows);
    }

    /**
     * Reconstructs the {@link TurNLFacetEvalPack} a dataset was imported from:
     * pack-level {@code index}/{@code locale}/{@code schema} from the dataset
     * metadata, one case per row (query + expectation from the row metadata).
     */
    public TurNLFacetEvalPack toPack(String datasetId) {
        TurEvalDataset dataset = requireNlFacet(datasetId);
        ObjectNode meta = readObject(dataset.getMetadataJson());
        String index = text(meta, "index");
        String locale = text(meta, "locale");
        List<TurNLFacetField> schema = OBJECT_MAPPER.convertValue(
                meta.has("schema") ? meta.get("schema") : OBJECT_MAPPER.createArrayNode(),
                new TypeReference<>() {
                });
        List<TurNLFacetEvalCase> cases = new ArrayList<>();
        for (TurEvalDatasetRow row : datasetRowRepository
                .findByTurEvalDataset_IdOrderBySortOrderAsc(datasetId)) {
            cases.add(rowToCase(row));
        }
        return new TurNLFacetEvalPack(dataset.getName(), index, locale, schema, cases);
    }

    /** Runs a saved NL→facet dataset through the intact Block&nbsp;R scorer. */
    public TurNLFacetEvalReport run(String datasetId) {
        return evalService.run(toPack(datasetId));
    }

    /** True when a usable default LLM is configured (the eval can run). */
    public boolean isAvailable() {
        return evalService.isAvailable();
    }

    /** Deletes an NL→facet dataset (rows cascade). */
    @Transactional
    public void delete(String datasetId) {
        requireNlFacet(datasetId);
        datasetRepository.deleteById(datasetId);
    }

    // ─────────────────────────── metadata mapping ───────────────────────────

    private static String packMetadata(TurNLFacetEvalPack pack) {
        ObjectNode meta = OBJECT_MAPPER.createObjectNode();
        meta.put("kind", KIND);
        meta.put("index", pack.index());
        meta.put("locale", pack.locale());
        meta.set("schema", OBJECT_MAPPER.valueToTree(pack.fields()));
        return meta.toString();
    }

    private static String caseMetadata(TurNLFacetEvalCase evalCase) {
        ObjectNode meta = OBJECT_MAPPER.createObjectNode();
        meta.put("kind", KIND);
        meta.put("query", evalCase.query());
        meta.set("expect", OBJECT_MAPPER.valueToTree(evalCase.expect()));
        return meta.toString();
    }

    private static TurNLFacetEvalCase rowToCase(TurEvalDatasetRow row) {
        ObjectNode meta = readObject(row.getMetadataJson());
        String query = text(meta, "query");
        if (query == null) {
            // Fallback: the query lives in the single seed turn.
            List<String> turns = parseTurns(row.getSeedTurnsJson());
            query = turns.isEmpty() ? "" : turns.get(0);
        }
        TurNLFacetExpectation expect = meta.has("expect")
                ? OBJECT_MAPPER.convertValue(meta.get("expect"), TurNLFacetExpectation.class)
                : new TurNLFacetExpectation(List.of(), List.of(), null);
        return new TurNLFacetEvalCase(row.getName(), query, expect);
    }

    // ─────────────────────────── helpers ───────────────────────────

    private TurEvalDataset requireNlFacet(String datasetId) {
        TurEvalDataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Dataset not found: " + datasetId));
        if (!isNlFacet(dataset)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Dataset is not an NL→facet dataset: " + datasetId);
        }
        return dataset;
    }

    private static boolean isNlFacet(TurEvalDataset dataset) {
        return KIND.equals(text(readObject(dataset.getMetadataJson()), "kind"));
    }

    private static String singleTurn(String query) {
        ArrayNode turns = OBJECT_MAPPER.createArrayNode();
        turns.add(query == null ? "" : query);
        return turns.toString();
    }

    private static List<String> parseTurns(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> turns = OBJECT_MAPPER.readValue(json, new TypeReference<>() {
            });
            return turns == null ? List.of() : turns;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static ObjectNode readObject(String json) {
        if (json == null || json.isBlank()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            var node = OBJECT_MAPPER.readTree(json);
            return node.isObject() ? (ObjectNode) node : OBJECT_MAPPER.createObjectNode();
        } catch (RuntimeException e) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private static String text(ObjectNode node, String key) {
        var value = node.path(key);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }
}
