/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.catalog.planning;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.catalog.planning.TurCopilotQueryPlanner.PlanRequest;
import com.viglet.turing.genai.nativeapi.anthropic.TurAnthropicStructuredOutputsService;
import com.viglet.turing.genai.nativeapi.openai.TurOpenAiStructuredOutputsService;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.eval.TurNLFacetField;
import com.viglet.turing.system.TurDefaultChatModelResolver;
import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T819 / §LIX.2 (Block BK) — the two LLM passes that follow the copilot's initial
 * parse: <strong>judge</strong> (does this query answer the question?) and
 * <strong>refine</strong> (name the constraints it should have had).
 *
 * <p>Both passes are <em>schema-bound</em>, never free-form JSON: the request goes
 * through the provider's own structured-output surface (OpenAI {@code json_schema},
 * Anthropic forced-tool input) exactly like the AI-authoring path, per the
 * {@code gpt-4o-mini} truncated-JSON caution in {@code agents.md}. Only when the
 * default LLM is neither OpenAI nor Anthropic does it fall back to a plain chat call
 * with the converter's format instruction appended.
 *
 * <p>Both passes are <strong>fail-open</strong>: any provider error, empty reply or
 * unparseable output returns {@link Optional#empty()} and the caller keeps the plan it
 * already had. Neither pass can make the copilot fail.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurCopilotPlanJudge {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    /** Ceiling on the serialized plan we show the model, so a huge body can't blow the prompt. */
    private static final int MAX_PLAN_CHARS = 4_000;

    private final TurGlobalSettingsService globalSettingsService;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurOpenAiStructuredOutputsService openAiStructured;
    private final TurAnthropicStructuredOutputsService anthropicStructured;
    private final TurDefaultChatModelResolver chatModelResolver;

    public TurCopilotPlanJudge(TurGlobalSettingsService globalSettingsService,
            TurLLMInstanceRepository llmInstanceRepository,
            TurOpenAiStructuredOutputsService openAiStructured,
            TurAnthropicStructuredOutputsService anthropicStructured,
            TurDefaultChatModelResolver chatModelResolver) {
        this.globalSettingsService = globalSettingsService;
        this.llmInstanceRepository = llmInstanceRepository;
        this.openAiStructured = openAiStructured;
        this.anthropicStructured = anthropicStructured;
        this.chatModelResolver = chatModelResolver;
    }

    // ─────────────────────────── Pass 1: judge ───────────────────────────

    /**
     * Judge {@code plan} against {@code request}'s question. Returns
     * {@link Optional#empty()} when no verdict could be obtained (fail-open — the
     * caller then keeps the plan unchanged).
     */
    public Optional<TurCopilotPlanVerdict> judge(PlanRequest request, TurDslQueryRequest plan) {
        return call("copilot_plan_verdict", TurCopilotPlanVerdict.class,
                judgeSystemPrompt(request.schema()), planInput(request, plan));
    }

    private String judgeSystemPrompt(List<TurNLFacetField> schema) {
        return """
                You audit a structured catalog search query against the question a user \
                asked. You do NOT rewrite the query — you only report what is wrong with it.

                Check exactly these things and nothing else:
                1. MISSING FILTERS — a constraint the QUESTION states (a category, a \
                   vendor, a price bound, a capability) that the QUERY does not express. \
                   Describe each one in a few words. Ignore constraints that map to no \
                   declared field: those are correctly omitted.
                2. SORT — does the question ask for a superlative, a comparative or an \
                   explicit ordering ("cheapest", "most capable", "sorted by", "mais \
                   barato", "ordenar por")? If yes, report the DECLARED field whose \
                   description names that quality as the OVERALL metric, and the \
                   direction: "asc" for a minimum, "desc" for a maximum. A narrower \
                   per-category sub-metric is never the overall metric. If the question \
                   asks for no ordering, leave sortField empty.
                3. UNGROUNDED FIELD — a field the query references that is NOT in the \
                   SCHEMA below. Report at most one.

                Set valid=true only when there are no missing filters, the ordering is \
                already correct (or none was asked for) and every field is declared.
                The question may be in any language; the field names are always as \
                declared below.

                SCHEMA (field: type [facet] [numeric] - description):
                %s
                """.formatted(renderSchema(schema));
    }

    // ─────────────────────────── Pass 2: refine ───────────────────────────

    /**
     * Ask the model to restate the query as a flat list of grounded constraints,
     * guided by {@code verdict}'s findings. Returns {@link Optional#empty()} when no
     * usable repair came back.
     */
    public Optional<TurCopilotPlanRepair> refine(PlanRequest request, TurDslQueryRequest plan,
            TurCopilotPlanVerdict verdict) {
        return call("copilot_plan_repair", TurCopilotPlanRepair.class,
                refineSystemPrompt(request.schema()), refineInput(request, plan, verdict));
    }

    private String refineSystemPrompt(List<TurNLFacetField> schema) {
        return """
                You restate a user's catalog question as a flat list of constraints over \
                a declared field schema. You do NOT write query syntax — you only name \
                fields, values and the ordering.

                HARD RULES:
                - Use ONLY field names from the SCHEMA below. Never invent one. A \
                  constraint that maps to no declared field belongs in freeText, or is \
                  dropped entirely.
                - termFilters are exact facet matches on a declared field. Prefer the \
                  value as written in the question; matching is case-insensitive.
                - rangeFilters are numeric bounds on a declared NUMERIC field \
                  ("under N" -> lte, "over N" -> gte, "between A and B" -> both).
                - A superlative, comparative or explicit ordering is NEVER a filter. \
                  Express it as sortField + sortOrder on the declared NUMERIC field \
                  whose description names that quality as the OVERALL metric \
                  ("asc" for a minimum, "desc" for a maximum). Never sort on a \
                  narrower per-category sub-metric just because its numbers are bigger.
                - freeText holds only what remains as plain search words. Leave it \
                  empty when every constraint is already a filter.
                - The question may be in any language; answer with the declared field \
                  names regardless.

                SCHEMA (field: type [facet] [numeric] - description):
                %s
                """.formatted(renderSchema(schema));
    }

    private String refineInput(PlanRequest request, TurDslQueryRequest plan,
            TurCopilotPlanVerdict verdict) {
        StringBuilder sb = new StringBuilder(planInput(request, plan));
        sb.append("\n\nAUDIT FINDINGS ON THAT QUERY:");
        if (verdict != null && verdict.missingFilters() != null && !verdict.missingFilters().isEmpty()) {
            sb.append("\n- missing filters: ").append(String.join("; ", verdict.missingFilters()));
        }
        if (verdict != null && StringUtils.hasText(verdict.sortField())) {
            sb.append("\n- ordering should be on: ").append(verdict.sortField())
                    .append(" ").append(StringUtils.hasText(verdict.sortOrder()) ? verdict.sortOrder() : "desc");
        }
        if (verdict != null && StringUtils.hasText(verdict.ungroundedField())) {
            sb.append("\n- undeclared field referenced: ").append(verdict.ungroundedField());
        }
        if (verdict != null && StringUtils.hasText(verdict.rationale())) {
            sb.append("\n- auditor note: ").append(verdict.rationale());
        }
        sb.append("\n\nRestate the QUESTION as constraints, fixing those findings.");
        return sb.toString();
    }

    // ─────────────────────────── Shared prompt pieces ───────────────────────────

    private String planInput(PlanRequest request, TurDslQueryRequest plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("INDEX: ").append(request.siteName());
        if (StringUtils.hasText(request.locale())) {
            sb.append("\nLOCALE: ").append(request.locale());
        }
        sb.append("\nQUESTION: ").append(request.query());
        sb.append("\nCURRENT QUERY (JSON): ").append(serializePlan(plan));
        return sb.toString();
    }

    private static String serializePlan(TurDslQueryRequest plan) {
        if (plan == null) {
            return "(none)";
        }
        try {
            String json = OBJECT_MAPPER.writeValueAsString(plan);
            return json.length() > MAX_PLAN_CHARS ? json.substring(0, MAX_PLAN_CHARS) + "…" : json;
        } catch (RuntimeException e) {
            return "(unserializable)";
        }
    }

    /**
     * The declared schema as the model sees it. {@code [numeric]} is flagged
     * explicitly (unlike the T385 parse prompt) because both passes hinge on "is this
     * field sortable / rangeable", and spelling it out beats making the model infer it
     * from the type name.
     */
    private static String renderSchema(List<TurNLFacetField> schema) {
        if (schema == null || schema.isEmpty()) {
            return "(no fields declared)";
        }
        StringBuilder sb = new StringBuilder();
        for (TurNLFacetField field : schema) {
            if (field == null || field.name() == null) {
                continue;
            }
            sb.append("- ").append(field.name()).append(": ")
                    .append(field.type() != null ? field.type().name().toLowerCase(java.util.Locale.ROOT) : "text");
            if (field.facet()) {
                sb.append(" [facet]");
            }
            if (field.numeric()) {
                sb.append(" [numeric]");
            }
            if (StringUtils.hasText(field.description())) {
                sb.append(" - ").append(field.description().trim());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    // ─────────────────────────── The schema-bound call ───────────────────────────

    /**
     * One schema-bound LLM call: provider structured output first (OpenAI
     * {@code json_schema} → Anthropic forced tool), then a plain chat call with the
     * converter's format instruction as the last resort. Never throws.
     */
    private <T> Optional<T> call(String schemaName, Class<T> type, String system, String input) {
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(type);
        try {
            Optional<String> json = providerStructured(schemaName, converter, system, input);
            if (json.isEmpty()) {
                json = plainChat(converter, system, input);
            }
            if (json.isEmpty() || !StringUtils.hasText(json.get())) {
                return Optional.empty();
            }
            return Optional.ofNullable(converter.convert(extractJsonObject(json.get())));
        } catch (RuntimeException e) {
            log.warn("[CopilotPlanner] '{}' pass failed ({}) — keeping the previous plan",
                    schemaName, e.getMessage());
            return Optional.empty();
        }
    }

    /** The provider's native structured-output surface; empty when unavailable. */
    private <T> Optional<String> providerStructured(String schemaName, BeanOutputConverter<T> converter,
            String system, String input) {
        TurLLMInstance instance = resolveDefaultInstance();
        Map<String, Object> schema = parseSchema(converter.getJsonSchema());
        if (instance == null || schema == null) {
            return Optional.empty();
        }
        // strict=false: the converter-generated schema isn't authored to OpenAI's
        // strict-mode subset, but json_schema still forces a JSON-object reply.
        Optional<String> json = openAiStructured.complete(instance, system, input, schemaName,
                schema, false);
        if (json.isPresent()) {
            return json;
        }
        return anthropicStructured.complete(instance, system, input, schemaName, schema);
    }

    /** Last resort for a non-OpenAI/Anthropic default LLM: chat + format instruction. */
    private <T> Optional<String> plainChat(BeanOutputConverter<T> converter, String system,
            String input) {
        Optional<ChatModel> modelOpt = chatModelResolver.resolve();
        if (modelOpt.isEmpty()) {
            return Optional.empty();
        }
        var response = modelOpt.get().call(new Prompt(List.of(
                new SystemMessage(system + "\n\n" + converter.getFormat()),
                new UserMessage(input))));
        var result = response != null ? response.getResult() : null;
        var output = result != null ? result.getOutput() : null;
        return Optional.ofNullable(output != null ? output.getText() : null);
    }

    private TurLLMInstance resolveDefaultInstance() {
        String llmId = globalSettingsService.getDefaultLlmId();
        if (!StringUtils.hasText(llmId)) {
            return null;
        }
        return llmInstanceRepository.findById(llmId).filter(l -> l.getEnabled() == 1).orElse(null);
    }

    private static Map<String, Object> parseSchema(String jsonSchema) {
        if (!StringUtils.hasText(jsonSchema)) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = OBJECT_MAPPER.readValue(jsonSchema, Map.class);
            return map;
        } catch (RuntimeException e) {
            log.debug("[CopilotPlanner] could not parse the response JSON schema: {}", e.getMessage());
            return null;
        }
    }

    /** Strips markdown fences / prose around the JSON object (same trick as T385). */
    static String extractJsonObject(String reply) {
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        return (start >= 0 && end > start) ? reply.substring(start, end + 1) : reply.trim();
    }
}
