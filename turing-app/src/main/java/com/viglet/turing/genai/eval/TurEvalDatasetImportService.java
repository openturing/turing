/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.persistence.dto.agent.TurEvalDatasetDto;
import com.viglet.turing.persistence.dto.agent.TurEvalDatasetRowDto;
import com.viglet.turing.persistence.model.agent.TurAgentEvalExpectedOutcome;
import com.viglet.turing.persistence.model.agent.TurEvalDataset;
import com.viglet.turing.persistence.model.agent.TurEvalDatasetRow;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRowRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * T596 / §XXXIII.11 — imports eval {@link TurEvalDataset}s from uploaded files
 * (JSON array / JSONL / CSV / OpenAI-Evals JSONL) with an optional column
 * mapping, and exports a dataset as JSONL. Concrete "provide a dataset to be
 * tested" surface. Importing from anonymized transcripts (T61) / Chat-Analytics
 * failures (T447 miner) composes onto {@link #importCanonicalRows} via
 * {@link TurEvalDatasetSourceImportService}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurEvalDatasetImportService {

    /** Supported import wire formats. */
    public enum Format { JSON, JSONL, CSV, OPENAI_EVALS }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TurEvalDatasetRepository datasetRepository;
    private final TurEvalDatasetRowRepository datasetRowRepository;

    public TurEvalDatasetImportService(TurEvalDatasetRepository datasetRepository,
            TurEvalDatasetRowRepository datasetRowRepository) {
        this.datasetRepository = datasetRepository;
        this.datasetRowRepository = datasetRowRepository;
    }

    /**
     * Parses {@code content} in {@code format} into rows (remapping source keys
     * via {@code columnMapping} for JSON/CSV) and persists a new dataset.
     */
    @Transactional
    public TurEvalDataset importDataset(String name, Format format, String content,
            Map<String, String> columnMapping) {
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty import content");
        }
        Map<String, String> mapping = columnMapping == null ? Map.of() : columnMapping;
        List<TurEvalDatasetRow> rows = switch (format) {
            case JSON -> fromJsonArray(content, mapping);
            case JSONL -> fromJsonl(content, mapping);
            case CSV -> fromCsv(content, mapping);
            case OPENAI_EVALS -> fromOpenAiEvals(content);
        };
        return persistDataset(name, rows);
    }

    /**
     * T596 — persists a dataset from already-built canonical row nodes (see
     * {@link #buildRow}). Lets the transcript / failure-mining source importers
     * ({@link TurEvalDatasetSourceImportService}) reuse the exact same row
     * mapping + persistence as the file importers instead of duplicating it.
     */
    @Transactional
    public TurEvalDataset importCanonicalRows(String name, List<? extends JsonNode> canonicalRows) {
        List<TurEvalDatasetRow> rows = new ArrayList<>();
        for (JsonNode node : canonicalRows) {
            rows.add(buildRow(node));
        }
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No rows to import");
        }
        return persistDataset(name, rows);
    }

    /**
     * T597 — appends already-built canonical row nodes to an <em>existing</em>
     * dataset (they land after the current rows, ordered) and bumps its
     * {@code updatedAt}. Lets the reviewed output of LLM-assisted generation /
     * augmentation ({@link TurEvalDatasetGenerationService}) grow a dataset the
     * same way {@link #importCanonicalRows} creates one, so a generated row is
     * shape-identical to an uploaded or mined one.
     */
    @Transactional
    public TurEvalDataset appendCanonicalRows(String datasetId, List<? extends JsonNode> canonicalRows) {
        TurEvalDataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Dataset not found: " + datasetId));
        List<TurEvalDatasetRow> rows = new ArrayList<>();
        for (JsonNode node : canonicalRows) {
            rows.add(buildRow(node));
        }
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No rows to append");
        }
        int order = datasetRowRepository.findByTurEvalDataset_IdOrderBySortOrderAsc(datasetId).size();
        for (TurEvalDatasetRow row : rows) {
            row.setSortOrder(order++);
            row.setTurEvalDataset(dataset);
            datasetRowRepository.save(row);
        }
        dataset.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return datasetRepository.save(dataset);
    }

    private TurEvalDataset persistDataset(String name, List<TurEvalDatasetRow> rows) {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        TurEvalDataset dataset = new TurEvalDataset();
        dataset.setName(name == null || name.isBlank() ? "imported-dataset" : name);
        dataset.setVersion(1);
        dataset.setCreatedAt(now);
        dataset.setUpdatedAt(now);
        int order = 0;
        for (TurEvalDatasetRow row : rows) {
            row.setSortOrder(order++);
            row.setTurEvalDataset(dataset);
            dataset.getRows().add(row);
        }
        return datasetRepository.save(dataset);
    }

    /** All datasets as summaries (no rows), name-ordered. */
    public List<TurEvalDatasetDto> list() {
        return datasetRepository.findByOrderByNameAsc().stream()
                .map(d -> TurEvalDatasetDto.summary(d,
                        datasetRowRepository.findByTurEvalDataset_IdOrderBySortOrderAsc(d.getId()).size()))
                .toList();
    }

    /** One dataset with its rows. */
    public TurEvalDatasetDto get(String datasetId) {
        TurEvalDataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Dataset not found: " + datasetId));
        List<TurEvalDatasetRowDto> rows =
                datasetRowRepository.findByTurEvalDataset_IdOrderBySortOrderAsc(datasetId).stream()
                        .map(TurEvalDatasetRowDto::fromEntity)
                        .toList();
        return TurEvalDatasetDto.detail(dataset, rows);
    }

    /** Deletes a dataset (rows cascade). */
    @Transactional
    public void delete(String datasetId) {
        datasetRepository.deleteById(datasetId);
    }

    /** Exports a dataset as JSONL (one canonical row object per line). */
    public String exportJsonl(String datasetId) {
        List<TurEvalDatasetRow> rows =
                datasetRowRepository.findByTurEvalDataset_IdOrderBySortOrderAsc(datasetId);
        StringBuilder sb = new StringBuilder();
        for (TurEvalDatasetRow row : rows) {
            sb.append(OBJECT_MAPPER.writeValueAsString(toCanonical(row))).append('\n');
        }
        return sb.toString();
    }

    // ─────────────────────────── format parsers ───────────────────────────

    private List<TurEvalDatasetRow> fromJsonArray(String content, Map<String, String> mapping) {
        JsonNode root = readTree(content);
        List<TurEvalDatasetRow> rows = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode node : root) {
                rows.add(buildRow(remap(node, mapping)));
            }
        } else if (root.isObject()) {
            rows.add(buildRow(remap(root, mapping)));
        }
        return rows;
    }

    private List<TurEvalDatasetRow> fromJsonl(String content, Map<String, String> mapping) {
        List<TurEvalDatasetRow> rows = new ArrayList<>();
        for (String line : content.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            rows.add(buildRow(remap(readTree(line), mapping)));
        }
        return rows;
    }

    private List<TurEvalDatasetRow> fromOpenAiEvals(String content) {
        List<TurEvalDatasetRow> rows = new ArrayList<>();
        for (String line : content.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode node = readTree(line);
            ObjectNode canonical = OBJECT_MAPPER.createObjectNode();
            // OpenAI-Evals: input = [{role,content}...]; ideal = the golden answer.
            ArrayNode turns = OBJECT_MAPPER.createArrayNode();
            JsonNode input = node.path("input");
            if (input.isArray()) {
                for (JsonNode msg : input) {
                    if ("user".equals(text(msg, "role"))) {
                        turns.add(text(msg, "content"));
                    }
                }
            }
            canonical.set("turns", turns);
            JsonNode ideal = node.path("ideal");
            canonical.put("referenceAnswer", ideal.isArray() && !ideal.isEmpty()
                    ? ideal.get(0).asString() : ideal.asString());
            rows.add(buildRow(canonical));
        }
        return rows;
    }

    private List<TurEvalDatasetRow> fromCsv(String content, Map<String, String> mapping) {
        List<String[]> table = parseCsv(content);
        List<TurEvalDatasetRow> rows = new ArrayList<>();
        if (table.size() < 2) {
            return rows;
        }
        String[] header = table.get(0);
        for (int r = 1; r < table.size(); r++) {
            String[] cells = table.get(r);
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            for (int c = 0; c < header.length && c < cells.length; c++) {
                node.put(header[c].trim(), cells[c]);
            }
            rows.add(buildRow(remap(node, mapping)));
        }
        return rows;
    }

    // ─────────────────────────── canonical row mapping ───────────────────────────

    /** Rewrites {@code node}'s keys through {@code mapping} (source -> canonical). */
    private ObjectNode remap(JsonNode node, Map<String, String> mapping) {
        ObjectNode out = OBJECT_MAPPER.createObjectNode();
        if (!node.isObject()) {
            return out;
        }
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            String canonicalKey = mapping.getOrDefault(field.getKey(), field.getKey());
            out.set(canonicalKey, field.getValue());
        }
        return out;
    }

    private TurEvalDatasetRow buildRow(JsonNode node) {
        TurEvalDatasetRow row = new TurEvalDatasetRow();
        row.setName(text(node, "name"));
        row.setSeedTurnsJson(turnsJson(node.path("turns")));
        JsonNode slots = node.path("expectedSlots");
        if (slots.isObject()) {
            row.setExpectedSlotsJson(slots.toString());
        }
        row.setExpectedOutcome(parseOutcome(text(node, "expectedOutcome")));
        row.setExpectedNodeId(text(node, "expectedNodeId"));
        row.setRubric(text(node, "rubric"));
        String reference = text(node, "referenceAnswer");
        row.setReferenceAnswer(reference != null ? reference : text(node, "reference"));
        row.setTags(tagsText(node.path("tags")));
        JsonNode metadata = node.path("metadata");
        if (metadata.isObject()) {
            row.setMetadataJson(metadata.toString());
        }
        return row;
    }

    private ObjectNode toCanonical(TurEvalDatasetRow row) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("name", row.getName());
        node.set("turns", parseTurnsArray(row.getSeedTurnsJson()));
        if (row.getExpectedSlotsJson() != null) {
            node.set("expectedSlots", readTree(row.getExpectedSlotsJson()));
        }
        node.put("expectedOutcome", row.getExpectedOutcome() == null
                ? TurAgentEvalExpectedOutcome.ANY.name() : row.getExpectedOutcome().name());
        node.put("expectedNodeId", row.getExpectedNodeId());
        node.put("rubric", row.getRubric());
        node.put("referenceAnswer", row.getReferenceAnswer());
        node.put("tags", row.getTags());
        return node;
    }

    // ─────────────────────────── helpers ───────────────────────────

    private JsonNode readTree(String content) {
        try {
            return OBJECT_MAPPER.readTree(content);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON: " + e.getMessage());
        }
    }

    /** A textual field, or {@code null} when missing / null. */
    private static String text(JsonNode node, String key) {
        JsonNode value = node.path(key);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }

    /** Serializes a turns node: an array is kept; a scalar becomes a single-turn array. */
    private String turnsJson(JsonNode turns) {
        if (turns.isArray()) {
            return turns.toString();
        }
        if (turns.isMissingNode() || turns.isNull()) {
            return "[]";
        }
        ArrayNode array = OBJECT_MAPPER.createArrayNode();
        array.add(turns.asString());
        return array.toString();
    }

    private ArrayNode parseTurnsArray(String seedTurnsJson) {
        if (seedTurnsJson == null || seedTurnsJson.isBlank()) {
            return OBJECT_MAPPER.createArrayNode();
        }
        JsonNode node = readTree(seedTurnsJson);
        return node.isArray() ? (ArrayNode) node : OBJECT_MAPPER.createArrayNode();
    }

    private static String tagsText(JsonNode tags) {
        if (tags.isArray()) {
            List<String> items = new ArrayList<>();
            for (JsonNode t : tags) {
                items.add(t.asString());
            }
            return String.join(",", items);
        }
        return tags.isMissingNode() || tags.isNull() ? null : tags.asString();
    }

    private static TurAgentEvalExpectedOutcome parseOutcome(String value) {
        if (value == null || value.isBlank()) {
            return TurAgentEvalExpectedOutcome.ANY;
        }
        try {
            return TurAgentEvalExpectedOutcome.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TurAgentEvalExpectedOutcome.ANY;
        }
    }

    /** Minimal RFC-4180-ish CSV parser (quoted fields, escaped quotes, CRLF). */
    static List<String[]> parseCsv(String content) {
        List<String[]> rows = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        int n = content.length();
        while (i < n) {
            char ch = content.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < n && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                current.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < n && content.charAt(i + 1) == '\n') {
                    i++;
                }
                current.add(field.toString());
                field.setLength(0);
                rows.add(current.toArray(new String[0]));
                current = new ArrayList<>();
            } else {
                field.append(ch);
            }
            i++;
        }
        if (field.length() > 0 || !current.isEmpty()) {
            current.add(field.toString());
            rows.add(current.toArray(new String[0]));
        }
        return rows;
    }
}
