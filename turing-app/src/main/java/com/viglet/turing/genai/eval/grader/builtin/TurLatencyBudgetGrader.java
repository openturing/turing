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

import java.util.List;

import org.springframework.stereotype.Component;

import com.viglet.turing.genai.eval.grader.TurEvalBuiltinGraders;
import com.viglet.turing.genai.eval.grader.TurEvalGrader;
import com.viglet.turing.genai.eval.grader.TurEvalGraderConfigView;
import com.viglet.turing.genai.eval.grader.TurEvalGraderKind;
import com.viglet.turing.genai.eval.grader.TurEvalGraderResult;
import com.viglet.turing.genai.eval.grader.TurEvalGradingContext;
import com.viglet.turing.genai.tool.TurChatToolCall;
import com.viglet.turing.service.chatanalytics.TurToolCallTraceService;

/**
 * T588 / §XXXIII.3 — config-driven CODE grader that composes the T427 tool-call
 * trace's per-call {@code durationMs}: asserts an aggregated tool-call latency
 * stays within a budget. Config: {@code maxMs} (required), {@code aggregate}
 * ({@code max} default / {@code sum} / {@code avg}), {@code tool} (optional
 * filter). With no timed tool calls the budget is trivially met.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurLatencyBudgetGrader implements TurEvalGrader {

    private final TurToolCallTraceService toolCallTraceService;

    public TurLatencyBudgetGrader(TurToolCallTraceService toolCallTraceService) {
        this.toolCallTraceService = toolCallTraceService;
    }

    @Override
    public String graderId() {
        return TurEvalBuiltinGraders.LATENCY_BUDGET;
    }

    @Override
    public TurEvalGraderKind kind() {
        return TurEvalGraderKind.CODE;
    }

    @Override
    public boolean appliesTo(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        return config.has("maxMs") && ctx.conversationId() != null;
    }

    @Override
    public TurEvalGraderResult grade(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        double maxMs = config.configDouble("maxMs", Double.POSITIVE_INFINITY);
        String tool = config.configString("tool");
        String aggregate = config.configString("aggregate");
        List<Long> durations = toolCallTraceService.getTrace(ctx.conversationId()).stream()
                .filter(c -> c.durationMs() != null)
                .filter(c -> tool == null || tool.equals(c.name()))
                .map(TurChatToolCall::durationMs)
                .toList();
        if (durations.isEmpty()) {
            return TurEvalGraderResult.scored(1d, true, "pass", "no timed tool calls");
        }
        double observed = switch (aggregate == null ? "max" : aggregate.toLowerCase()) {
            case "sum" -> durations.stream().mapToLong(Long::longValue).sum();
            case "avg" -> durations.stream().mapToLong(Long::longValue).average().orElse(0d);
            default -> durations.stream().mapToLong(Long::longValue).max().orElse(0L);
        };
        boolean match = observed <= maxMs;
        return TurEvalGraderResult.scored(match ? 1d : 0d, match, match ? "pass" : "fail",
                match ? null : "latency " + observed + "ms exceeded budget " + maxMs + "ms");
    }
}
