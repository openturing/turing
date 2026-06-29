/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.nativeapi.openai.TurOpenAiEvalsPayload.EvalFixture;

/** F.9 / §X.10.b — T168 OpenAI Evals request-body shapes. */
class TurOpenAiEvalsPayloadTest {

    @Test
    @SuppressWarnings("unchecked")
    void evalBodyHasCustomSchemaAndLabelModelGrader() {
        Map<String, Object> body = TurOpenAiEvalsPayload.evalBody("turing-eval:Agent", "gpt-4o-mini");

        assertThat(body).containsEntry("name", "turing-eval:Agent");

        Map<String, Object> dsc = (Map<String, Object>) body.get("data_source_config");
        assertThat(dsc).containsEntry("type", "custom").containsEntry("include_sample_schema", false);
        Map<String, Object> schema = (Map<String, Object>) dsc.get("item_schema");
        assertThat(((Map<String, Object>) schema.get("properties")))
                .containsKeys("rubric", "turns");
        assertThat((List<String>) schema.get("required")).containsExactly("rubric", "turns");

        List<Map<String, Object>> criteria = (List<Map<String, Object>>) body.get("testing_criteria");
        assertThat(criteria).hasSize(1);
        Map<String, Object> grader = criteria.get(0);
        assertThat(grader)
                .containsEntry("type", "label_model")
                .containsEntry("model", "gpt-4o-mini");
        assertThat((List<String>) grader.get("labels")).containsExactly("pass", "fail");
        assertThat((List<String>) grader.get("passing_labels")).containsExactly("pass");
        // The grader templates reference the two item fields.
        assertThat(grader.get("input").toString())
                .contains("{{ item.rubric }}")
                .contains("{{ item.turns }}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void runBodyWrapsFixturesAsInlineJsonl() {
        Map<String, Object> body = TurOpenAiEvalsPayload.runBody("turing-eval:Agent:run", List.of(
                new EvalFixture("c1", "Must greet the user", "Hi\nHello there"),
                new EvalFixture("c2", "Must capture email", "my email is a@b.com")));

        assertThat(body).containsEntry("name", "turing-eval:Agent:run");
        Map<String, Object> dataSource = (Map<String, Object>) body.get("data_source");
        assertThat(dataSource).containsEntry("type", "jsonl");
        Map<String, Object> source = (Map<String, Object>) dataSource.get("source");
        assertThat(source).containsEntry("type", "file_content");

        List<Map<String, Object>> content = (List<Map<String, Object>>) source.get("content");
        assertThat(content).hasSize(2);
        Map<String, Object> firstItem = (Map<String, Object>) content.get(0).get("item");
        assertThat(firstItem)
                .containsEntry("rubric", "Must greet the user")
                .containsEntry("turns", "Hi\nHello there");
    }

    @Test
    @SuppressWarnings("unchecked")
    void runBodyTreatsNullFixtureFieldsAsEmpty() {
        Map<String, Object> body = TurOpenAiEvalsPayload.runBody("r", List.of(
                new EvalFixture("c1", null, null)));
        Map<String, Object> source = (Map<String, Object>) ((Map<String, Object>) body
                .get("data_source")).get("source");
        Map<String, Object> item = (Map<String, Object>) ((List<Map<String, Object>>) source
                .get("content")).get(0).get("item");
        assertThat(item).containsEntry("rubric", "").containsEntry("turns", "");
    }
}
