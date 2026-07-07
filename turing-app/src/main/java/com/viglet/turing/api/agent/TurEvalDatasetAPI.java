/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.agent;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.genai.eval.TurEvalDatasetGenerationService;
import com.viglet.turing.genai.eval.TurEvalDatasetImportService;
import com.viglet.turing.genai.eval.TurEvalDatasetImportService.Format;
import com.viglet.turing.genai.eval.TurEvalDatasetSourceImportService;
import com.viglet.turing.genai.eval.TurEvalDatasetVersionService;
import com.viglet.turing.persistence.dto.agent.TurEvalDatasetDiffDto;
import com.viglet.turing.persistence.dto.agent.TurEvalDatasetDto;
import com.viglet.turing.persistence.dto.agent.TurEvalDatasetRowDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * T596 / §XXXIII.11 — reusable eval-dataset management: list / read / delete,
 * import from an uploaded file (JSON / JSONL / CSV / OpenAI-Evals) with an
 * optional column mapping, and export as JSONL. Role-gated like the Agent-CI
 * surface.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/eval/dataset")
@Tag(name = "Eval Dataset", description = "Reusable, agent-decoupled eval datasets")
public class TurEvalDatasetAPI {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TurEvalDatasetImportService datasetService;
    private final TurEvalDatasetVersionService versionService;
    private final TurEvalDatasetSourceImportService sourceImportService;
    private final TurEvalDatasetGenerationService generationService;

    public TurEvalDatasetAPI(TurEvalDatasetImportService datasetService,
            TurEvalDatasetVersionService versionService,
            TurEvalDatasetSourceImportService sourceImportService,
            TurEvalDatasetGenerationService generationService) {
        this.datasetService = datasetService;
        this.versionService = versionService;
        this.sourceImportService = sourceImportService;
        this.generationService = generationService;
    }

    @Operation(summary = "List datasets (summaries)")
    @GetMapping
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public List<TurEvalDatasetDto> list() {
        return datasetService.list();
    }

    @Operation(summary = "One dataset with its rows")
    @GetMapping("/{datasetId}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public TurEvalDatasetDto get(@PathVariable String datasetId) {
        return datasetService.get(datasetId);
    }

    @Operation(summary = "Delete a dataset (rows cascade)")
    @DeleteMapping("/{datasetId}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public void delete(@PathVariable String datasetId) {
        datasetService.delete(datasetId);
    }

    @Operation(summary = "Import a dataset from an uploaded file (JSON/JSONL/CSV/OpenAI-Evals)")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public TurEvalDatasetDto importDataset(@RequestParam("file") MultipartFile file,
            @RequestParam("format") String format,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "columnMapping", required = false) String columnMappingJson) {
        Format parsedFormat;
        try {
            parsedFormat = Format.valueOf(format.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown format: " + format);
        }
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read upload");
        }
        Map<String, String> mapping = parseMapping(columnMappingJson);
        String datasetName = name != null ? name : file.getOriginalFilename();
        return datasetService.get(
                datasetService.importDataset(datasetName, parsedFormat, content, mapping).getId());
    }

    @Operation(summary = "Build a dataset from an agent's recent production transcripts (T61)")
    @PostMapping("/import/transcripts")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public TurEvalDatasetDto importFromTranscripts(@RequestParam("agentId") String agentId,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return datasetService.get(
                sourceImportService.importFromTranscripts(agentId, name, limit).getId());
    }

    @Operation(summary = "Build a dataset from an agent's mined failing conversations (T447)")
    @PostMapping("/import/failures")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public TurEvalDatasetDto importFromFailures(@RequestParam("agentId") String agentId,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return datasetService.get(
                sourceImportService.importFromFailures(agentId, name, limit).getId());
    }

    @Operation(summary = "T597 — LLM-generate candidate rows for an agent (draft, NOT persisted)")
    @PostMapping("/generate")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public List<TurEvalDatasetRowDto> generate(@RequestParam("agentId") String agentId,
            @RequestParam(value = "count", defaultValue = "10") int count) {
        return generationService.generateFromAgent(agentId, count);
    }

    @Operation(summary = "T597 — LLM-paraphrase a dataset's rows into variants (draft, NOT persisted)")
    @PostMapping("/{datasetId}/augment")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public List<TurEvalDatasetRowDto> augment(@PathVariable String datasetId,
            @RequestParam(value = "variants", defaultValue = "2") int variants) {
        return generationService.augmentDataset(datasetId, variants);
    }

    @Operation(summary = "T597 — persist reviewed draft rows (append to datasetId, else create)")
    @PostMapping("/save-reviewed")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public TurEvalDatasetDto saveReviewed(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "datasetId", required = false) String datasetId,
            @org.springframework.web.bind.annotation.RequestBody List<TurEvalDatasetRowDto> rows) {
        return generationService.saveReviewed(name, datasetId, rows);
    }

    @Operation(summary = "Export a dataset as JSONL")
    @GetMapping(value = "/{datasetId}/export", produces = MediaType.TEXT_PLAIN_VALUE)
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public String export(@PathVariable String datasetId) {
        return datasetService.exportJsonl(datasetId);
    }

    @Operation(summary = "Freeze an immutable snapshot at the current version, then bump the version")
    @PostMapping("/{datasetId}/snapshot")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public Map<String, Object> snapshot(@PathVariable String datasetId) {
        var snapshot = versionService.snapshot(datasetId);
        return Map.of("version", snapshot.getVersion(), "snapshotId", snapshot.getId());
    }

    @Operation(summary = "Snapshotted versions of a dataset (newest first)")
    @GetMapping("/{datasetId}/versions")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public List<Integer> versions(@PathVariable String datasetId) {
        return versionService.versions(datasetId);
    }

    @Operation(summary = "Row-level drift between two dataset versions")
    @GetMapping("/{datasetId}/diff")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public TurEvalDatasetDiffDto diff(@PathVariable String datasetId,
            @RequestParam("from") int from, @RequestParam("to") int to) {
        return versionService.diff(datasetId, from, to);
    }

    private static Map<String, String> parseMapping(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid columnMapping JSON: " + e.getMessage());
        }
    }
}
