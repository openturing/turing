/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.openai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F.9 / §X.10.b — T168. Pure, SDK-free builder for the two OpenAI Evals API
 * request bodies Turing sends: the <b>eval</b> definition (a custom item schema
 * + a server-side {@code label_model} grader that judges each rubric) and the
 * <b>run</b> (the agent's rubric fixtures as inline JSONL).
 *
 * <p>Kept free of the OpenAI SDK types (which are deeply nested typed builders)
 * so the payload shape is unit-testable as plain maps; {@code TurOpenAiEvalsService}
 * hands the maps to the SDK via {@code putAllAdditionalBodyProperties}. Mirrors
 * the pure-builder pattern of {@code TurOpenAiBatchJsonl} (T156).
 *
 * <p>The grader judges a fixture purely from the provided rubric + scripted
 * conversation turns — exactly the inputs {@code TurEvalJudgePrompt} feeds the
 * local judge — so no server-side completion generation is required: Turing
 * pushes the fixtures and OpenAI runs the grader over them.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurOpenAiEvalsPayload {

    /** Item-schema field carrying the natural-language assertion. */
    static final String ITEM_RUBRIC = "rubric";
    /** Item-schema field carrying the scripted conversation turns (joined). */
    static final String ITEM_TURNS = "turns";

    static final String PASS_LABEL = "pass";
    static final String FAIL_LABEL = "fail";

    private TurOpenAiEvalsPayload() {
    }

    /** One rubric fixture: the assertion + the conversation it is judged against. */
    public record EvalFixture(String caseId, String rubric, String turns) {
    }

    /**
     * Body for {@code POST /v1/evals} — a custom data-source config (the two
     * string item fields) plus one {@code label_model} testing criterion that
     * labels each item {@code pass}/{@code fail} against its rubric.
     */
    public static Map<String, Object> evalBody(String name, String graderModel) {
        Map<String, Object> itemProperties = new LinkedHashMap<>();
        itemProperties.put(ITEM_RUBRIC, Map.of("type", "string"));
        itemProperties.put(ITEM_TURNS, Map.of("type", "string"));
        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "object");
        itemSchema.put("properties", itemProperties);
        itemSchema.put("required", List.of(ITEM_RUBRIC, ITEM_TURNS));

        Map<String, Object> dataSourceConfig = new LinkedHashMap<>();
        dataSourceConfig.put("type", "custom");
        dataSourceConfig.put("item_schema", itemSchema);
        dataSourceConfig.put("include_sample_schema", false);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("data_source_config", dataSourceConfig);
        body.put("testing_criteria", List.of(labelModelGrader(graderModel)));
        return body;
    }

    private static Map<String, Object> labelModelGrader(String graderModel) {
        Map<String, Object> developer = new LinkedHashMap<>();
        developer.put("role", "developer");
        developer.put("content", "You are a strict evaluator for an enterprise-search assistant. "
                + "Decide whether the conversation satisfies the rubric. Reply with exactly one "
                + "label: \"" + PASS_LABEL + "\" or \"" + FAIL_LABEL + "\".");
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("role", "user");
        user.put("content", "Rubric:\n{{ item." + ITEM_RUBRIC + " }}\n\n"
                + "Conversation turns:\n{{ item." + ITEM_TURNS + " }}");

        Map<String, Object> grader = new LinkedHashMap<>();
        grader.put("type", "label_model");
        grader.put("name", "rubric-judge");
        grader.put("model", graderModel);
        grader.put("input", List.of(developer, user));
        grader.put("labels", List.of(PASS_LABEL, FAIL_LABEL));
        grader.put("passing_labels", List.of(PASS_LABEL));
        return grader;
    }

    /**
     * Body for {@code POST /v1/evals/{eval_id}/runs} — the fixtures as inline
     * JSONL ({@code file_content}); each item carries the rubric + turns the
     * grader reads via its {@code {{ item.* }}} templates.
     */
    public static Map<String, Object> runBody(String name, List<EvalFixture> fixtures) {
        List<Map<String, Object>> content = new ArrayList<>();
        for (EvalFixture fixture : fixtures) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put(ITEM_RUBRIC, fixture.rubric() == null ? "" : fixture.rubric());
            item.put(ITEM_TURNS, fixture.turns() == null ? "" : fixture.turns());
            content.add(Map.of("item", item));
        }

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", "file_content");
        source.put("content", content);

        Map<String, Object> dataSource = new LinkedHashMap<>();
        dataSource.put("type", "jsonl");
        dataSource.put("source", source);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("data_source", dataSource);
        return body;
    }
}
