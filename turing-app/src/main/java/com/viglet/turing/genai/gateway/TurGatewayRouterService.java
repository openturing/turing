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
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.provider.llm.TurLlmModelCatalog;
import com.viglet.turing.genai.provider.llm.TurLlmModelMetadata;
import com.viglet.turing.genai.provider.llm.TurLlmModelOption;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
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

    /**
     * Ordering strategy across the router's deployments. {@code ROUND_ROBIN},
     * {@code LATENCY} (observed EWMA) and {@code FAILOVER} are the T745 originals;
     * {@code COST}, {@code QUALITY} and {@code TTFT} (T784) order by the catalog's
     * indicative pricing / intelligence index / time-to-first-token so a client can
     * ask for "cheapest", "highest intelligence" or "lowest TTFT" across its
     * declared deployments.
     */
    public enum Strategy {
        ROUND_ROBIN, LATENCY, FAILOVER, COST, QUALITY, TTFT
    }

    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurLlmModelCatalog modelCatalog;
    private final ConcurrentHashMap<String, AtomicInteger> roundRobin = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> latencyEwmaMs = new ConcurrentHashMap<>();

    public TurGatewayRouterService(TurLLMInstanceRepository llmInstanceRepository,
            TurLlmModelCatalog modelCatalog) {
        this.llmInstanceRepository = llmInstanceRepository;
        this.modelCatalog = modelCatalog;
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
            case COST -> sortByCatalog(targets, this::inputPrice, false);
            case QUALITY -> sortByCatalog(targets, this::intelligence, true);
            case TTFT -> sortByCatalog(targets, this::ttft, false);
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
            case "cost", "cheapest", "price" -> Strategy.COST;
            case "quality", "intelligence", "best" -> Strategy.QUALITY;
            case "ttft", "fastest", "first-token" -> Strategy.TTFT;
            default -> Strategy.ROUND_ROBIN;
        };
    }

    // ---- Catalog-driven strategies (T784) --------------------------------

    /**
     * Orders the candidates by a catalog metric. Instances whose model has no
     * catalog value for the metric always sort <em>last</em> (so a missing figure
     * never wins a "cheapest"/"fastest" pick), and ties keep their declared order.
     *
     * @param metric     extracts the metric for an instance, or {@code null} when unknown
     * @param descending {@code true} to prefer the highest value (QUALITY), else lowest
     */
    private List<TurLLMInstance> sortByCatalog(List<TurLLMInstance> targets,
            java.util.function.Function<TurLLMInstance, Double> metric, boolean descending) {
        List<TurLLMInstance> ordered = new ArrayList<>(targets);
        ordered.sort((a, b) -> {
            Double va = metric.apply(a);
            Double vb = metric.apply(b);
            if (va == null && vb == null) {
                return 0;
            }
            if (va == null) {
                return 1;
            }
            if (vb == null) {
                return -1;
            }
            return descending ? Double.compare(vb, va) : Double.compare(va, vb);
        });
        return ordered;
    }

    private Double inputPrice(TurLLMInstance instance) {
        TurLlmModelMetadata meta = metadataFor(instance);
        return meta == null || meta.pricing() == null ? null : meta.pricing().inputPer1M();
    }

    private Double intelligence(TurLLMInstance instance) {
        TurLlmModelMetadata meta = metadataFor(instance);
        return meta == null || meta.benchmarks() == null ? null : meta.benchmarks().intelligenceIndex();
    }

    private Double ttft(TurLLMInstance instance) {
        TurLlmModelMetadata meta = metadataFor(instance);
        return meta == null || meta.performance() == null ? null : meta.performance().latencyTtftSec();
    }

    /** Catalog metadata for an instance's configured model, or {@code null} when unknown. */
    private TurLlmModelMetadata metadataFor(TurLLMInstance instance) {
        if (instance == null || modelCatalog == null || !StringUtils.hasText(instance.getModelName())) {
            return null;
        }
        String slug = vendorSlug(instance.getTurLLMVendor());
        if (slug == null) {
            return null;
        }
        return modelCatalog.allModels().getOrDefault(slug, List.of()).stream()
                .filter(o -> instance.getModelName().equals(o.id()))
                .map(TurLlmModelOption::metadata)
                .findFirst()
                .orElse(null);
    }

    private static String vendorSlug(TurLLMVendor vendor) {
        if (vendor == null) {
            return null;
        }
        String slug = StringUtils.hasText(vendor.getPlugin()) ? vendor.getPlugin() : vendor.getId();
        return StringUtils.hasText(slug) ? slug.toLowerCase(Locale.ROOT) : null;
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
