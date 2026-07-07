/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval.grader.builtin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.viglet.turing.genai.eval.grader.TurEvalBuiltinGraders;
import com.viglet.turing.genai.eval.grader.TurEvalGrader;
import com.viglet.turing.genai.eval.grader.TurEvalGraderConfigView;
import com.viglet.turing.genai.eval.grader.TurEvalGraderKind;
import com.viglet.turing.genai.eval.grader.TurEvalGraderResult;
import com.viglet.turing.genai.eval.grader.TurEvalGradingContext;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * T586 / §XXXIII.1 — built-in CODE grader wrapping the historical <b>slots</b>
 * dimension: a deterministic normalized-equality diff of the captured slots vs
 * the case's {@code expectedSlotsJson}. Score = matched / expected; passes only
 * when every expected slot matched.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurSlotMatchGrader implements TurEvalGrader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String graderId() {
        return TurEvalBuiltinGraders.SLOT_MATCH;
    }

    @Override
    public TurEvalGraderKind kind() {
        return TurEvalGraderKind.CODE;
    }

    @Override
    public boolean appliesTo(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        return !parseSlots(ctx.evalCase().getExpectedSlotsJson()).isEmpty();
    }

    @Override
    public TurEvalGraderResult grade(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        Map<String, String> expected = parseSlots(ctx.evalCase().getExpectedSlotsJson());
        Map<String, String> captured = ctx.capturedSlots();
        List<TurEvalGraderResult.Detail> details = new ArrayList<>();
        int matched = 0;
        for (Map.Entry<String, String> e : expected.entrySet()) {
            String actual = captured == null ? null : captured.get(e.getKey());
            boolean match = normalize(actual).equals(normalize(e.getValue()));
            if (match) {
                matched++;
            }
            details.add(new TurEvalGraderResult.Detail(e.getKey(), e.getValue(), actual, match));
        }
        double score = (double) matched / expected.size();
        return TurEvalGraderResult.withDetails(score, matched == expected.size(), details);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim();
    }

    private static Map<String, String> parseSlots(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> slots = OBJECT_MAPPER.readValue(json, new TypeReference<>() {
            });
            return slots == null ? Map.of() : slots;
        } catch (RuntimeException e) {
            log.warn("[AgentEval] bad expectedSlotsJson: {}", e.getMessage());
            return Map.of();
        }
    }
}
