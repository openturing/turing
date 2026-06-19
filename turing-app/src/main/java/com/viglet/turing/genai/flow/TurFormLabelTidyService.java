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
import com.viglet.turing.persistence.dto.agent.TurFormLabelTidyField;
import com.viglet.turing.persistence.dto.agent.TurFormLabelTidyResponse;
import com.viglet.turing.system.TurLlmSummaryService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * T236 / §VII.13.g — opt-in "tidy labels" polish on the T234 form conversion.
 *
 * <p>The client-side {@code planFormConversion} derives each form field's label
 * from the first line of the source question's instruction — so a converted form
 * carries raw labels like <em>"Qual o seu e-mail corporativo?"</em>. This service
 * asks the default LLM to rewrite those into short, clean form labels
 * (<em>"E-mail"</em>) in the same language, leaving the slot {@code name} and
 * widget {@code type} untouched.
 *
 * <p>Mirrors {@link TurChatFlowVariantService}: it delegates the LLM round-trip
 * to {@link TurLlmSummaryService} (default-LLM resolution, encryption, token
 * accounting, error path) with a fresh random cache key + {@code regenerate=true}
 * (a tidy pass is creative and must not be served from cache). Nothing is
 * persisted — the dialog applies the tidied labels only when the author confirms
 * the conversion, and the graph itself persists only on Save.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurFormLabelTidyService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Defensive cap — a converted form rarely exceeds a handful of fields. */
    private static final int MAX_FIELDS = 50;

    private final TurLlmSummaryService llmSummaryService;

    public TurFormLabelTidyService(TurLlmSummaryService llmSummaryService) {
        this.llmSummaryService = llmSummaryService;
    }

    /**
     * Tidy the labels of {@code fields}. Returns the same fields in the same
     * order with cleaned labels; {@code success=false} when no default LLM is
     * configured, the input is empty, or the LLM response cannot be applied.
     */
    public TurFormLabelTidyResponse tidy(List<TurFormLabelTidyField> fields) {
        if (fields == null || fields.isEmpty()) {
            return fail("No fields to tidy.");
        }
        if (fields.size() > MAX_FIELDS) {
            return fail("Too many fields to tidy (max " + MAX_FIELDS + ").");
        }
        if (!llmSummaryService.isAvailable()) {
            return fail("No default LLM configured in Global Settings.");
        }

        String userPayload = buildUserPayload(fields);
        String cacheKey = "form-label-tidy:" + UUID.randomUUID();
        TurLlmSummaryService.SummaryResult result =
                llmSummaryService.generate(cacheKey, userPayload, SYSTEM_PROMPT, true);
        if (!result.success()) {
            return fail(result.error() == null ? "LLM call failed." : result.error());
        }

        Map<String, String> tidyByName = parseTidyLabels(result.content());
        if (tidyByName == null) {
            return fail("LLM response was not valid JSON.");
        }

        // Re-pair tidied labels with the original fields by slot name. Names are
        // unique per agent, so this is unambiguous; a field the LLM dropped or
        // returned blank keeps its original label (lossless degrade).
        List<TurFormLabelTidyField> tidied = new ArrayList<>(fields.size());
        boolean anyChanged = false;
        for (TurFormLabelTidyField field : fields) {
            String original = field.label();
            String candidate = tidyByName.get(field.name());
            String resolved = (candidate != null && !candidate.isBlank())
                    ? candidate.trim()
                    : original;
            if (!java.util.Objects.equals(resolved, original)) {
                anyChanged = true;
            }
            tidied.add(new TurFormLabelTidyField(field.name(), resolved, field.type()));
        }
        if (!anyChanged) {
            return fail("The labels were already concise — nothing to tidy.");
        }
        return new TurFormLabelTidyResponse(true, null, tidied);
    }

    /* ─────────────────────── LLM round-trip ─────────────────────── */

    private String buildUserPayload(List<TurFormLabelTidyField> fields) {
        // Send a compact table of {name, label, type} so the model has the
        // widget context (a `tel` field's label should read like a phone label).
        List<Map<String, Object>> rows = new ArrayList<>(fields.size());
        for (TurFormLabelTidyField field : fields) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", field.name() == null ? "" : field.name());
            row.put("label", field.label() == null ? "" : field.label());
            if (StringUtils.hasText(field.type())) {
                row.put("type", field.type());
            }
            rows.add(row);
        }
        String tableJson;
        try {
            tableJson = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(rows);
        } catch (JacksonException e) {
            tableJson = "[]";
        }
        return "Form fields to tidy:\n" + tableJson;
    }

    /** Parse the LLM response into a {name → tidied label} map, or null if it could not be parsed. */
    // S1168: null is a parse-failure sentinel (the caller maps null → fail(...)),
    // distinct from an empty map ("parsed, no labels"). Empty would mask bad JSON.
    @SuppressWarnings("java:S1168")
    private Map<String, String> parseTidyLabels(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String json = ChatFlowOps.stripFences(content);
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode fieldsNode = root.path("fields");
            if (!fieldsNode.isArray()) {
                return null;
            }
            Map<String, String> byName = new LinkedHashMap<>();
            for (JsonNode field : fieldsNode) {
                String name = textOrNull(field, "name");
                String label = textOrNull(field, "label");
                if (name != null && label != null) {
                    byName.put(name, label);
                }
            }
            return byName;
        } catch (JacksonException e) {
            log.warn("[FormLabelTidy] LLM response was not valid JSON: {}", e.getOriginalMessage());
            return null;
        }
    }

    /* ─────────────────────── Small helpers ─────────────────────── */

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() ? value.asString() : null;
    }

    private static TurFormLabelTidyResponse fail(String message) {
        return new TurFormLabelTidyResponse(false, message, null);
    }

    /* ─────────────────────── System prompt ─────────────────────── */

    private static final String SYSTEM_PROMPT = """
            You clean up form-field LABELS. The input is a JSON array of fields
            converted from a chat-flow question sequence; each `label` is a raw
            question instruction ("Qual o seu e-mail corporativo?", "Please tell
            me your full name") and should become a short, professional form
            label ("E-mail", "Full name").

            ════════════ HARD CONSTRAINTS ════════════
            - Output ONE JSON object. No prose, no markdown, no code fences.
            - Schema:
              {
                "fields": [
                  { "name": "<echo the field's name VERBATIM>",
                    "label": "<the tidied label>" }
                ]
              }
            - Echo every `name` EXACTLY as received — it is the field's slot key,
              never translate or alter it. Return one entry per input field, in
              the same order.
            - Only produce the `label`. Do NOT add fields, drop fields, or emit
              any property other than `name` and `label`.

            ════════════ HOW TO TIDY A LABEL ════════════
            - Strip the question framing ("Qual o seu…", "Please enter…",
              "Could you tell me…") and keep the noun phrase the field asks for.
            - Title-case to the convention of the language; keep it under ~30
              characters. Drop trailing punctuation ("?", ":").
            - Preserve the LANGUAGE of the original label — a Portuguese label
              stays Portuguese, an English label stays English. Never translate.
            - Use the `type` hint when present (e.g. a `tel` field → "Telefone" /
              "Phone"; an `email` field → "E-mail").
            - If a label is already a clean short label, return it unchanged.
            - Never invent information that is not implied by the original label.
            """;
}
