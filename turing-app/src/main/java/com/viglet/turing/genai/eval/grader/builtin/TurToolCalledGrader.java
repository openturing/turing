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
 * trace: asserts a tool was invoked during the case replay. Config: {@code tool}
 * (required), {@code status} (optional {@code "ok"} / {@code "error"} filter),
 * {@code minCalls} (default 1). Exact, not inferred from slot-audit writes.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurToolCalledGrader implements TurEvalGrader {

    private final TurToolCallTraceService toolCallTraceService;

    public TurToolCalledGrader(TurToolCallTraceService toolCallTraceService) {
        this.toolCallTraceService = toolCallTraceService;
    }

    @Override
    public String graderId() {
        return TurEvalBuiltinGraders.TOOL_CALLED;
    }

    @Override
    public TurEvalGraderKind kind() {
        return TurEvalGraderKind.CODE;
    }

    @Override
    public boolean appliesTo(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        return config.configString("tool") != null && ctx.conversationId() != null;
    }

    @Override
    public TurEvalGraderResult grade(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        String tool = config.configString("tool");
        String status = config.configString("status");
        int minCalls = (int) Math.round(config.configDouble("minCalls", 1d));
        List<TurChatToolCall> trace = toolCallTraceService.getTrace(ctx.conversationId());
        long calls = trace.stream()
                // count completions only (start markers carry no outcome)
                .filter(c -> !"start".equals(c.phase()))
                .filter(c -> tool.equals(c.name()))
                .filter(c -> status == null || status.equalsIgnoreCase(c.status()))
                .count();
        boolean match = calls >= minCalls;
        return TurEvalGraderResult.scored(match ? 1d : 0d, match, match ? "pass" : "fail",
                match ? null : "tool '" + tool + "' called " + calls + " time(s), expected >= " + minCalls);
    }
}
