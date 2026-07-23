/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval.grader;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.viglet.turing.genai.tool.TurCustomToolCallbackService;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import lombok.extern.slf4j.Slf4j;

/**
 * T589 / §XXXIII.4 — compiles + runs a custom Groovy eval-grader script,
 * reusing the Custom Tool sandbox model: the same {@link GroovyShell} parse +
 * {@link TurCustomToolCallbackService#sanitizeGroovySource source
 * sanitization}, a vetted {@link Binding} of grading-context data (no arbitrary
 * platform helpers), and compiled-script caching by source hash.
 *
 * <p>The script is authored by an admin (same trust model as a Custom Tool). It
 * receives these bindings: {@code answer} (final assistant reply), {@code
 * answers} (all replies), {@code turns} (user turns), {@code slots} (captured
 * slot map), {@code outcome}, {@code node}, {@code conversationId}, {@code
 * caseName}. It returns one of:
 *
 * <ul>
 *   <li>a {@code Map} {@code [score: 0..1, pass: bool, rationale: '...']}
 *       (any field optional — {@code pass} defaults to {@code score >= 1});</li>
 *   <li>a {@code Boolean} pass/fail;</li>
 *   <li>a {@code Number} score (pass when {@code >= 1}).</li>
 * </ul>
 *
 * <p><b>Errors-as-fail</b>: a compile/run exception yields a failing result with
 * the message as rationale — a broken grader never breaks the eval run.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurGroovyGraderEngine {

    private final Map<Integer, Class<? extends Script>> scriptCache = new ConcurrentHashMap<>();

    public TurEvalGraderResult evaluate(String script, TurEvalGradingContext ctx) {
        if (script == null || script.isBlank()) {
            return TurEvalGraderResult.scored(0d, false, "fail", "empty grader script");
        }
        try {
            Class<? extends Script> clazz = compile(script);
            Binding binding = new Binding();
            binding.setVariable("answer", ctx.finalAnswer());
            binding.setVariable("answers", ctx.assistantReplies());
            binding.setVariable("turns", ctx.userTurns());
            binding.setVariable("slots", ctx.capturedSlots());
            binding.setVariable("outcome", ctx.actualOutcome());
            binding.setVariable("node", ctx.finalNodeId());
            binding.setVariable("conversationId", ctx.conversationId());
            binding.setVariable("caseName", ctx.evalCase() == null ? null : ctx.evalCase().getName());
            Script instance = clazz.getDeclaredConstructor().newInstance();
            instance.setBinding(binding);
            return interpret(instance.run());
        } catch (Exception e) {
            log.warn("[AgentEval] groovy grader failed: {}", e.getMessage());
            return TurEvalGraderResult.scored(0d, false, "fail", "grader error: " + e.getMessage());
        }
    }

    private Class<? extends Script> compile(String script) {
        return scriptCache.computeIfAbsent(script.hashCode(), h -> {
            GroovyShell shell = new GroovyShell();
            String source = TurCustomToolCallbackService.sanitizeGroovySource(script);
            Class<?> parsed = shell.getClassLoader()
                    .parseClass(source, "TurEvalGroovyGrader_" + h + ".groovy");
            return parsed.asSubclass(Script.class);
        });
    }

    private static TurEvalGraderResult interpret(Object result) {
        if (result instanceof Map<?, ?> map) {
            Double score = toDouble(map.get("score"));
            Boolean pass = toBoolean(map.get("pass"));
            Object rationaleObj = map.get("rationale");
            String rationale = rationaleObj == null ? null : rationaleObj.toString();
            boolean passed = pass != null ? pass : (score != null && score >= 1d);
            double sc = score != null ? clamp(score) : (passed ? 1d : 0d);
            return TurEvalGraderResult.scored(sc, passed, passed ? "pass" : "fail", rationale);
        }
        if (result instanceof Boolean b) {
            return TurEvalGraderResult.binary(b);
        }
        if (result instanceof Number n) {
            double sc = clamp(n.doubleValue());
            boolean passed = sc >= 1d;
            return TurEvalGraderResult.scored(sc, passed, passed ? "pass" : "fail", null);
        }
        boolean truthy = result != null && Boolean.parseBoolean(String.valueOf(result));
        return TurEvalGraderResult.binary(truthy);
    }

    private static double clamp(double v) {
        if (v < 0d) {
            return 0d;
        }
        return Math.min(v, 1d);
    }

    private static Double toDouble(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v != null) {
            try {
                return Double.valueOf(String.valueOf(v).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Boolean toBoolean(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v != null) {
            return Boolean.valueOf(String.valueOf(v).trim());
        }
        return null;
    }
}
