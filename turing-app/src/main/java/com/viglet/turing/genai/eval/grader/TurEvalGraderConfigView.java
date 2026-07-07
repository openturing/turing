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

import java.util.List;
import java.util.Map;

import com.viglet.turing.persistence.model.agent.TurEvalGraderConfig;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * T588 / §XXXIII.3 — an immutable, JPA-decoupled view of one {@link
 * TurEvalGraderConfig} handed to a {@link TurEvalGrader} at grade time. Exposes
 * the per-instance {@code configJson} through typed accessors so config-driven
 * CODE graders (regex / exact / numeric-range / ...) don't each re-parse JSON.
 *
 * @param graderId  the grader id this config drives
 * @param name      human label (defaults to the grader id)
 * @param kind      grader kind name (informational)
 * @param configJson per-instance JSON config (nullable)
 * @param weight    aggregate weight (T600)
 * @param threshold pass threshold (T600)
 * @param blocking  1 = failing result blocks the stack (T600)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
public record TurEvalGraderConfigView(
        String graderId,
        String name,
        String kind,
        String configJson,
        double weight,
        double threshold,
        int blocking) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** View of a persisted config row. */
    public static TurEvalGraderConfigView fromEntity(TurEvalGraderConfig c) {
        return new TurEvalGraderConfigView(c.getGraderId(), c.getName(), c.getKind(),
                c.getConfigJson(), c.getWeight(), c.getThreshold(), c.getBlocking());
    }

    /** Default view for a grader that runs without a persisted config (legacy stack). */
    public static TurEvalGraderConfigView defaults(String graderId, TurEvalGraderKind kind) {
        return new TurEvalGraderConfigView(graderId, graderId, kind == null ? null : kind.name(),
                null, 1.0d, 0.0d, 1);
    }

    /** The parsed {@code configJson} as a map ({@code {}} when null / invalid). */
    public Map<String, Object> config() {
        if (configJson == null || configJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(configJson, new TypeReference<>() {
            });
            return parsed == null ? Map.of() : parsed;
        } catch (RuntimeException e) {
            log.warn("[AgentEval] bad grader configJson for '{}': {}", graderId, e.getMessage());
            return Map.of();
        }
    }

    /** Whether the config declares {@code key} at all. */
    public boolean has(String key) {
        return config().containsKey(key);
    }

    /** String value at {@code key}, or {@code null}. */
    public String configString(String key) {
        Object v = config().get(key);
        return v == null ? null : String.valueOf(v);
    }

    /** Numeric value at {@code key}, or {@code dflt} when absent / non-numeric. */
    public double configDouble(String key, double dflt) {
        Object v = config().get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v != null) {
            try {
                return Double.parseDouble(String.valueOf(v).trim());
            } catch (NumberFormatException ignored) {
                return dflt;
            }
        }
        return dflt;
    }

    /** String list at {@code key} (a JSON array, or a single string), else empty. */
    public List<String> configStringList(String key) {
        Object v = config().get(key);
        if (v instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (v instanceof String s && !s.isBlank()) {
            return List.of(s);
        }
        return List.of();
    }

    /** Boolean value at {@code key}, or {@code dflt} when absent. */
    public boolean configBoolean(String key, boolean dflt) {
        Object v = config().get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v != null) {
            return Boolean.parseBoolean(String.valueOf(v).trim());
        }
        return dflt;
    }
}
