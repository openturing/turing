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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.agent.TurAgentEvalCase;
import com.viglet.turing.persistence.model.agent.TurAgentEvalSet;
import com.viglet.turing.persistence.model.agent.TurEvalGraderConfig;

/**
 * T586 / §XXXIII.1 — discovers every {@link TurEvalGrader} bean and resolves
 * the grader stack for a case. With no explicit grader config (the only state
 * that exists until T587) it returns the {@link TurEvalBuiltinGraders#DEFAULT_STACK
 * legacy default stack}, so the eval runner is byte-identical to the pre-SPI
 * path. T587 will consult a persisted {@code TurEvalGraderConfig} list here.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurEvalGraderRegistry {

    private final Map<String, TurEvalGrader> byId;
    private final com.viglet.turing.persistence.repository.agent.TurEvalGraderStackRepository stackRepository;

    public TurEvalGraderRegistry(List<TurEvalGrader> graders,
            com.viglet.turing.persistence.repository.agent.TurEvalGraderStackRepository stackRepository) {
        Map<String, TurEvalGrader> map = new LinkedHashMap<>();
        for (TurEvalGrader grader : graders) {
            map.put(grader.graderId(), grader);
        }
        this.byId = Map.copyOf(map);
        this.stackRepository = stackRepository;
    }

    /** Looks up a grader by its stable id. */
    public Optional<TurEvalGrader> find(String graderId) {
        return Optional.ofNullable(byId.get(graderId));
    }

    /** All registered graders (registration order). */
    public Collection<TurEvalGrader> all() {
        return byId.values();
    }

    /**
     * Resolves the ordered grader stack to run for {@code evalCase} of {@code
     * set}. When the set carries no enabled {@link TurEvalGraderConfig} rows
     * (T587) this is the legacy {@link TurEvalBuiltinGraders#DEFAULT_STACK
     * default stack}, so scoring is byte-identical to the pre-config path.
     *
     * <p>When it does carry config: set-level rows (null/blank {@code caseId})
     * are taken in {@code sortOrder}, then per-case override rows (matching
     * {@code evalCase}) replace / append by {@code graderId}. Unknown grader ids
     * are skipped defensively (e.g. a config-only grader not yet on the
     * classpath). An empty resolution falls back to the default stack.
     */
    public List<TurEvalResolvedGrader> resolveStack(TurAgentEvalSet set, TurAgentEvalCase evalCase) {
        if (set == null) {
            return defaultStack();
        }
        // T600 — a bound reusable stack overrides the set's own configs.
        String stackId = set.getGraderStackId();
        if (stackId != null && !stackId.isBlank() && stackRepository != null) {
            List<TurEvalResolvedGrader> fromStack = stackRepository.findById(stackId)
                    .map(s -> resolveFromConfigs(s.getConfigs(), evalCase))
                    .orElse(List.of());
            if (!fromStack.isEmpty()) {
                return fromStack;
            }
        }
        List<TurEvalResolvedGrader> fromSet =
                resolveFromConfigs(set.getGraderConfigs(), evalCase);
        return fromSet.isEmpty() ? defaultStack() : fromSet;
    }

    /**
     * Maps an ordered, enabled config collection to resolved graders: set-level
     * rows (null/blank {@code caseId}) in {@code sortOrder}, then per-case
     * override rows (matching {@code evalCase}) replace/append by {@code graderId}.
     * Unknown grader ids are skipped. Returns empty when no config resolves.
     */
    private List<TurEvalResolvedGrader> resolveFromConfigs(
            java.util.Collection<TurEvalGraderConfig> configs, TurAgentEvalCase evalCase) {
        if (configs == null) {
            return List.of();
        }
        List<TurEvalGraderConfig> enabled = configs.stream()
                .filter(c -> c.getEnabled() == 1)
                .toList();
        if (enabled.isEmpty()) {
            return List.of();
        }
        Map<String, TurEvalGraderConfig> effective = new LinkedHashMap<>();
        enabled.stream()
                .filter(c -> c.getCaseId() == null || c.getCaseId().isBlank())
                .sorted(Comparator.comparingInt(TurEvalGraderConfig::getSortOrder))
                .forEach(c -> effective.put(c.getGraderId(), c));
        String caseId = evalCase == null ? null : evalCase.getId();
        if (caseId != null) {
            enabled.stream()
                    .filter(c -> caseId.equals(c.getCaseId()))
                    .sorted(Comparator.comparingInt(TurEvalGraderConfig::getSortOrder))
                    .forEach(c -> effective.put(c.getGraderId(), c));
        }
        List<TurEvalResolvedGrader> stack = new ArrayList<>();
        for (TurEvalGraderConfig cfg : effective.values()) {
            find(cfg.getGraderId()).ifPresent(g ->
                    stack.add(new TurEvalResolvedGrader(g, TurEvalGraderConfigView.fromEntity(cfg))));
        }
        return stack;
    }

    /** The legacy default stack (any built-in not wired is skipped defensively). */
    private List<TurEvalResolvedGrader> defaultStack() {
        List<TurEvalResolvedGrader> stack = new ArrayList<>();
        for (String graderId : TurEvalBuiltinGraders.DEFAULT_STACK) {
            find(graderId).ifPresent(g ->
                    stack.add(new TurEvalResolvedGrader(g, TurEvalGraderConfigView.defaults(graderId, g.kind()))));
        }
        return stack;
    }
}
