/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * T745 / §XLIX — the Governed LLM Gateway's <b>router</b>: a first-class
 * {@code turing-router:*} model that load-balances a request across N deployments
 * of a model with a fallback chain, generalizing the T742 budget-downgrade into a
 * per-request routing policy.
 *
 * <p>Model syntax: {@code turing-router:<strategy>/<id1,id2,...>} where strategy
 * is {@code rr} (round-robin, default), {@code latency} (least observed latency)
 * or {@code failover} (declared order); the strategy segment is optional
 * ({@code turing-router:id1,id2} == round-robin). The service returns an <b>ordered
 * candidate list</b> — the first entry is the pick, the rest are the fallback
 * chain the gateway walks on error. Round-robin state and an EWMA latency estimate
 * per instance are held in-memory (best-effort, reset on restart).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurGatewayRouterService {

    public static final String PREFIX = "turing-router:";

    /** Load-balancing strategy across the router's deployments. */
    public enum Strategy {
        ROUND_ROBIN, LATENCY, FAILOVER
    }

    private final TurLLMInstanceRepository llmInstanceRepository;
    private final ConcurrentHashMap<String, AtomicInteger> roundRobin = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> latencyEwmaMs = new ConcurrentHashMap<>();

    public TurGatewayRouterService(TurLLMInstanceRepository llmInstanceRepository) {
        this.llmInstanceRepository = llmInstanceRepository;
    }

    public boolean isRouter(String model) {
        return model != null && model.startsWith(PREFIX);
    }

    /** Parsed router directive. */
    public record Route(Strategy strategy, List<String> targetIds) {
    }

    /** Parses {@code turing-router:[strategy/]id1,id2,...}. */
    public Route parse(String model) {
        String rest = model.substring(PREFIX.length()).trim();
        Strategy strategy = Strategy.ROUND_ROBIN;
        String targetPart = rest;
        int slash = rest.indexOf('/');
        if (slash >= 0) {
            strategy = strategyOf(rest.substring(0, slash));
            targetPart = rest.substring(slash + 1);
        }
        List<String> ids = Arrays.stream(targetPart.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return new Route(strategy, ids);
    }

    /**
     * The ordered execution candidates for this router model: element 0 is the
     * chosen deployment (per strategy), the remainder is the fallback chain.
     * Throws {@link IllegalArgumentException} when no target resolves to an
     * enabled instance.
     */
    public List<TurLLMInstance> orderedCandidates(String model) {
        Route route = parse(model);
        List<TurLLMInstance> targets = new ArrayList<>();
        for (String id : route.targetIds()) {
            llmInstanceRepository.findById(id)
                    .filter(i -> i.getEnabled() == 1)
                    .ifPresent(targets::add);
        }
        if (targets.isEmpty()) {
            throw new IllegalArgumentException(
                    "turing-router: no enabled target instance resolved from " + model);
        }
        return switch (route.strategy()) {
            case FAILOVER -> targets;
            case LATENCY -> sortByLatency(targets);
            case ROUND_ROBIN -> rotate(model, targets);
        };
    }

    /** Feeds an observed call latency back into the least-latency estimate. */
    public void recordLatency(String instanceId, long ms) {
        if (instanceId == null || ms < 0) {
            return;
        }
        latencyEwmaMs.merge(instanceId, (double) ms, (prev, sample) -> 0.8 * prev + 0.2 * sample);
    }

    // ---- Internals --------------------------------------------------------

    private Strategy strategyOf(String token) {
        return switch (token.trim().toLowerCase(Locale.ROOT)) {
            case "latency", "least-latency", "least-busy" -> Strategy.LATENCY;
            case "failover", "fallback" -> Strategy.FAILOVER;
            default -> Strategy.ROUND_ROBIN;
        };
    }

    private List<TurLLMInstance> rotate(String model, List<TurLLMInstance> targets) {
        int start = Math.floorMod(
                roundRobin.computeIfAbsent(model, k -> new AtomicInteger()).getAndIncrement(),
                targets.size());
        List<TurLLMInstance> ordered = new ArrayList<>(targets.size());
        for (int i = 0; i < targets.size(); i++) {
            ordered.add(targets.get((start + i) % targets.size()));
        }
        return ordered;
    }

    private List<TurLLMInstance> sortByLatency(List<TurLLMInstance> targets) {
        List<TurLLMInstance> ordered = new ArrayList<>(targets);
        // Unseen instances (no sample yet) sort first so they get explored.
        ordered.sort((a, b) -> Double.compare(
                latencyEwmaMs.getOrDefault(a.getId(), 0d),
                latencyEwmaMs.getOrDefault(b.getId(), 0d)));
        return ordered;
    }
}
