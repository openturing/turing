/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.persistence.dto.agent.TurChatFlowDto;
import com.viglet.turing.persistence.dto.agent.TurChatFlowVariantRequest;
import com.viglet.turing.persistence.dto.agent.TurChatFlowVariantResponse;
import com.viglet.turing.persistence.mapper.agent.TurChatFlowMapper;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.system.TurLlmSummaryService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * T97 / §VII.11.g — variant generator. Rewrites the user-facing copy of an
 * existing chat flow per a free-text directive ({@code "more casual tone,
 * 30% shorter"}) and returns a NON-PERSISTED candidate the author can
 * review and save through the standard create endpoint.
 *
 * <p>What gets rewritten: {@code label}, {@code aiInstruction},
 * {@code conditionExpression}, and {@code slotValue} on every node — the
 * fields a visitor or a downstream LLM call actually reads.
 *
 * <p>What is preserved verbatim: every node id, every node type, every
 * edge, every structural field ({@code outputVariable}, {@code validationRule},
 * {@code toolSource}, {@code functionName}, {@code mcpServerId},
 * {@code subFlowId}, {@code personaId}, {@code switchVariable},
 * {@code switchOptions}, {@code slotName}, {@code slotOperation},
 * {@code onJudgeReject}, {@code toolsEnabled}, {@code requiredTools}),
 * positions, and React-Flow render hints. Structural edits stay the author's
 * responsibility — the LLM only touches the copy.
 *
 * <p>Delegates the LLM round-trip to {@link TurLlmSummaryService} so the
 * default-LLM resolution, encryption, token accounting and error path stay
 * consistent with the AI Insights pipeline. Each call uses a fresh
 * (random-suffixed) cache key with {@code regenerate=true} — variants are
 * inherently creative and should not be served from cache.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurChatFlowVariantService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Field names on a node's {@code data} object that the LLM is allowed to rewrite. */
    private static final List<String> REWRITABLE_FIELDS = List.of(
            "label", "aiInstruction", "conditionExpression", "slotValue");

    private final TurLlmSummaryService llmSummaryService;
    private final TurChatFlowMapper turChatFlowMapper;

    public TurChatFlowVariantService(TurLlmSummaryService llmSummaryService,
            TurChatFlowMapper turChatFlowMapper) {
        this.llmSummaryService = llmSummaryService;
        this.turChatFlowMapper = turChatFlowMapper;
    }

    /**
     * Build a variant of {@code source} per {@code request.instructions()}.
     * Returns a {@link TurChatFlowVariantResponse} with the unpersisted
     * candidate; {@code success=false} when no default LLM is configured,
     * the source has no parseable definition, or the LLM response cannot be
     * applied.
     */
    public TurChatFlowVariantResponse generate(TurChatFlow source, TurChatFlowVariantRequest request) {
        if (source == null) {
            return fail("Source chat flow is required.");
        }
        if (request == null || !StringUtils.hasText(request.instructions())) {
            return fail("Variant instructions are required.");
        }
        if (!llmSummaryService.isAvailable()) {
            return fail("No default LLM configured in Global Settings.");
        }
        if (!StringUtils.hasText(source.getDefinitionJson())) {
            return fail("Source flow has no graph to rewrite.");
        }

        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(source.getDefinitionJson());
        } catch (JacksonException e) {
            log.warn("[ChatFlowVariant] Source flow {} has unparseable definitionJson: {}",
                    source.getId(), e.getOriginalMessage());
            return fail("Source flow definition is not valid JSON.");
        }

        List<Map<String, Object>> copyTable = extractCopyTable(root);
        if (copyTable.isEmpty()) {
            return fail("Source flow has no rewritable copy.");
        }

        String userPayload = buildUserPayload(source, request.instructions(), copyTable);
        String cacheKey = "chat-flow-variant:" + source.getId() + ":" + UUID.randomUUID();
        TurLlmSummaryService.SummaryResult result =
                llmSummaryService.generate(cacheKey, userPayload, SYSTEM_PROMPT, true);
        if (!result.success()) {
            return fail(result.error() == null ? "LLM call failed." : result.error());
        }

        ObjectNode rewrite = parseRewrite(result.content());
        if (rewrite == null) {
            return fail("LLM response was not valid JSON.");
        }

        List<String> changed = applyRewrite(root, rewrite);
        if (changed.isEmpty()) {
            return fail("LLM did not change any node copy.");
        }

        String serialized;
        try {
            serialized = OBJECT_MAPPER.writeValueAsString(root);
        } catch (JacksonException e) {
            log.warn("[ChatFlowVariant] Failed to serialize rewritten graph", e);
            return fail("Failed to serialize rewritten graph.");
        }

        String summary = extractText(rewrite, "summary");
        TurChatFlowDto candidate = buildCandidate(source, request, serialized, summary);
        return new TurChatFlowVariantResponse(true, null, candidate, summary, changed);
    }

    /* ─────────────────────── Extraction & apply ─────────────────────── */

    private List<Map<String, Object>> extractCopyTable(JsonNode root) {
        JsonNode nodes = root.path("nodes");
        if (!nodes.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode node : nodes) {
            String id = textOrNull(node, "id");
            String type = textOrNull(node, "type");
            if (id == null || type == null || "start".equals(type) || "end".equals(type)) {
                continue;
            }
            JsonNode data = node.path("data");
            if (!data.isObject()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("type", type);
            boolean anyCopy = false;
            for (String field : REWRITABLE_FIELDS) {
                String value = textOrNull(data, field);
                if (value != null && !value.isBlank()) {
                    row.put(field, value);
                    anyCopy = true;
                }
            }
            if (anyCopy) {
                rows.add(row);
            }
        }
        return rows;
    }

    private List<String> applyRewrite(JsonNode root, ObjectNode rewrite) {
        JsonNode rewrittenNodes = rewrite.path("nodes");
        if (!rewrittenNodes.isArray()) {
            return List.of();
        }

        // Index source nodes by id so we don't pay O(N²) walking the array per LLM patch.
        Map<String, ObjectNode> sourceNodeById = new LinkedHashMap<>();
        for (JsonNode node : root.path("nodes")) {
            String id = textOrNull(node, "id");
            if (id != null && node instanceof ObjectNode objectNode) {
                sourceNodeById.put(id, objectNode);
            }
        }

        List<String> changed = new ArrayList<>();
        for (JsonNode patch : rewrittenNodes) {
            String id = textOrNull(patch, "id");
            if (id == null) {
                continue;
            }
            ObjectNode sourceNode = sourceNodeById.get(id);
            if (sourceNode == null) {
                log.debug("[ChatFlowVariant] LLM rewrote unknown node id '{}' — skipping", id);
                continue;
            }
            JsonNode dataNode = sourceNode.path("data");
            if (!(dataNode instanceof ObjectNode dataObject)) {
                continue;
            }
            boolean nodeChanged = false;
            for (String field : REWRITABLE_FIELDS) {
                if (!patch.has(field)) {
                    continue;
                }
                String newValue = textOrNull(patch, field);
                if (newValue == null) {
                    continue;
                }
                String oldValue = textOrNull(dataObject, field);
                if (!newValue.equals(oldValue)) {
                    dataObject.put(field, newValue);
                    nodeChanged = true;
                }
            }
            if (nodeChanged) {
                changed.add(id);
            }
        }
        return changed;
    }

    /* ─────────────────────── LLM round-trip ─────────────────────── */

    private String buildUserPayload(TurChatFlow source, String instructions,
            List<Map<String, Object>> copyTable) {
        String tableJson;
        try {
            tableJson = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(copyTable);
        } catch (JacksonException e) {
            tableJson = "[]";
        }
        StringBuilder sb = new StringBuilder(512);
        sb.append("Author instruction: ").append(instructions.trim()).append("\n\n");
        sb.append("Source flow name: ").append(safe(source.getName())).append('\n');
        if (StringUtils.hasText(source.getDescription())) {
            sb.append("Source flow description: ").append(source.getDescription().trim()).append('\n');
        }
        sb.append("\nCurrent copy (do NOT change ids, types, or any field not listed here):\n");
        sb.append(tableJson);
        return sb.toString();
    }

    private ObjectNode parseRewrite(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String json = ChatFlowOps.stripFences(content);
        try {
            JsonNode parsed = OBJECT_MAPPER.readTree(json);
            return parsed instanceof ObjectNode object ? object : null;
        } catch (JacksonException e) {
            log.warn("[ChatFlowVariant] LLM response was not valid JSON: {}", e.getOriginalMessage());
            return null;
        }
    }

    /* ─────────────────────── Candidate assembly ─────────────────────── */

    private TurChatFlowDto buildCandidate(TurChatFlow source, TurChatFlowVariantRequest request,
            String serializedGraph, String summary) {
        // Round-trip through the mapper so we preserve every field on the
        // entity without manually copying each one — keeps the candidate
        // automatically in sync as new columns get added to TurChatFlow.
        TurChatFlowDto candidate = turChatFlowMapper.toDto(source);
        candidate.setId(null);
        candidate.setName(resolveVariantName(source, request));
        candidate.setDefinitionJson(serializedGraph);
        // New variants land disabled so they cannot accidentally take live
        // traffic before the author has reviewed the rewrite.
        candidate.setEnabled(0);
        if (StringUtils.hasText(summary)) {
            String trimmed = summary.trim();
            String existing = source.getDescription();
            String prefix = StringUtils.hasText(existing) ? existing.trim() + "\n\n" : "";
            candidate.setDescription(prefix + "Variant: " + trimmed);
        }
        // The variant inherits the source's experiment grouping by default
        // so an author can stage multiple tone variants under the same A/B
        // key. variantLabel is left empty — the author picks it explicitly
        // at save time to avoid stamping a meaningless label.
        candidate.setVariantLabel(null);
        // Recipe lineage belongs to the original install, not to a derived
        // rewrite — wipe both audit columns.
        candidate.setInstalledFromRecipe(null);
        candidate.setInstalledRecipeVersion(null);
        return candidate;
    }

    private String resolveVariantName(TurChatFlow source, TurChatFlowVariantRequest request) {
        if (StringUtils.hasText(request.targetName())) {
            return request.targetName().trim();
        }
        String base = StringUtils.hasText(source.getName()) ? source.getName().trim() : "Chat Flow";
        return base + " (variant)";
    }

    /* ─────────────────────── Small helpers ─────────────────────── */

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private static String extractText(ObjectNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static TurChatFlowVariantResponse fail(String message) {
        return new TurChatFlowVariantResponse(false, message, null, null, List.of());
    }

    /* ─────────────────────── System prompt ─────────────────────── */

    private static final String SYSTEM_PROMPT = """
            You are a copywriter rewriting the user-facing TEXT of an existing
            chat-flow per the author's instruction.

            ════════════ HARD CONSTRAINTS ════════════
            - Output ONE JSON object. No prose, no markdown, no code fences.
            - Schema:
              {
                "summary": "one short sentence (<=200 chars) describing what you changed",
                "nodes": [
                  { "id": "<existing-id>",
                    "label": "...",                  // optional, only if changed
                    "aiInstruction": "...",          // optional, only if changed
                    "conditionExpression": "...",    // optional, only if changed
                    "slotValue": "..." }             // optional, only if changed
                ]
              }
            - Keep every node id EXACTLY as you received it. Do NOT invent new
              ids, do NOT drop nodes, do NOT add nodes.
            - Only rewrite the four fields listed above. Anything else in the
              source (types, outputVariable, validationRule, toolSource,
              functionName, mcpServerId, slotName, slotOperation, switchOptions,
              edges) MUST be left untouched — omit those fields from your
              response entirely.
            - Omit a node from the "nodes" array if its copy did not need to
              change. An empty "nodes" array is acceptable when the
              instruction was a no-op.

            ════════════ WHAT EACH FIELD IS ════════════
            - `label` — short uppercase title shown on the editor card.
            - `aiInstruction` — second-person prompt the engine sends to the
              LLM ("Ask the user for their full name."). Rewrite preserving the
              intent and any {{slot}} placeholders verbatim.
            - `conditionExpression` — plain-language boolean an LLM judge
              evaluates ("company_size > 50"). Keep variable names exact;
              only rewrite the surrounding phrasing.
            - `slotValue` — literal value a `slot SET` node writes. Rewrite
              only if it is human-readable copy; never touch ids, UUIDs,
              booleans, numbers, or `{{interpolation}}` tokens.

            ════════════ STYLE GUIDANCE ════════════
            - Honor the author's instruction (tone, audience, length).
            - When asked to shorten, prefer cutting filler words over dropping
              {{slot}} placeholders or validation cues.
            - Preserve language: if the source is in Portuguese, the rewrite
              stays in Portuguese unless the instruction asks for a translation.
            - Do NOT add new questions, branching logic, or tool calls. Those
              are structural changes — the author handles them in the editor.
            """;
}
