/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.gateway.TurGatewayRouterService.Route;
import com.viglet.turing.genai.gateway.TurGatewayRouterService.Strategy;
import com.viglet.turing.genai.provider.llm.TurLlmModelCatalog;
import com.viglet.turing.genai.provider.llm.TurLlmModelKind;
import com.viglet.turing.genai.provider.llm.TurLlmModelMetadata;
import com.viglet.turing.genai.provider.llm.TurLlmModelMetadata.TurLlmModelBenchmarks;
import com.viglet.turing.genai.provider.llm.TurLlmModelMetadata.TurLlmModelPerformance;
import com.viglet.turing.genai.provider.llm.TurLlmModelMetadata.TurLlmModelPricing;
import com.viglet.turing.genai.provider.llm.TurLlmModelOption;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;

/**
 * T745 / §XLIX — unit coverage for gateway router parsing, round-robin rotation,
 * failover ordering and least-latency selection.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurGatewayRouterServiceTest {

    @Mock
    private TurLLMInstanceRepository llmInstanceRepository;
    @Mock
    private TurLlmModelCatalog modelCatalog;

    private TurGatewayRouterService service() {
        return new TurGatewayRouterService(llmInstanceRepository, modelCatalog);
    }

    private TurLLMInstance instance(String id) {
        return instance(id, null);
    }

    private TurLLMInstance instance(String id, String modelName) {
        TurLLMInstance i = new TurLLMInstance();
        i.setId(id);
        i.setEnabled(1);
        i.setModelName(modelName);
        TurLLMVendor vendor = new TurLLMVendor();
        vendor.setId("OPENAI");
        i.setTurLLMVendor(vendor);
        return i;
    }

    private void stubInstances(String... ids) {
        for (String id : ids) {
            lenient().when(llmInstanceRepository.findById(id)).thenReturn(Optional.of(instance(id)));
        }
    }

    /** Registers an instance whose model carries the given catalog metadata. */
    private void stubModel(String id, String modelName, TurLlmModelMetadata meta) {
        TurLLMInstance i = instance(id, modelName);
        lenient().when(llmInstanceRepository.findById(id)).thenReturn(Optional.of(i));
        catalogModels.add(new TurLlmModelOption(modelName, modelName, TurLlmModelKind.CHAT, meta));
    }

    private final java.util.List<TurLlmModelOption> catalogModels = new java.util.ArrayList<>();

    private void wireCatalog() {
        lenient().when(modelCatalog.allModels())
                .thenReturn(java.util.Map.of("openai", List.copyOf(catalogModels)));
    }

    private static TurLlmModelMetadata pricing(double inputPer1M) {
        return new TurLlmModelMetadata(null, null, null, null, null,
                new TurLlmModelPricing(inputPer1M, inputPer1M, "USD", true, "litellm", null),
                null, null, null, null, null, null, null, null);
    }

    private static TurLlmModelMetadata intelligence(double index) {
        return new TurLlmModelMetadata(null, null, null, null, null, null,
                new TurLlmModelBenchmarks(index, null), null, null, null, null, null, null, null);
    }

    private static TurLlmModelMetadata ttft(double seconds) {
        return new TurLlmModelMetadata(null, null, null, null, null, null, null,
                new TurLlmModelPerformance(null, seconds), null, null, null, null, null, null);
    }

    @Test
    void detectsRouterModel() {
        assertThat(service().isRouter("turing-router:a,b")).isTrue();
        assertThat(service().isRouter("gpt-4o")).isFalse();
        assertThat(service().isRouter(null)).isFalse();
    }

    @Test
    void parsesStrategyAndTargets() {
        Route r = service().parse("turing-router:latency/a, b ,c");
        assertThat(r.strategy()).isEqualTo(Strategy.LATENCY);
        assertThat(r.targetIds()).containsExactly("a", "b", "c");

        Route def = service().parse("turing-router:a,b");
        assertThat(def.strategy()).isEqualTo(Strategy.ROUND_ROBIN);
        assertThat(def.targetIds()).containsExactly("a", "b");

        assertThat(service().parse("turing-router:failover/a,b").strategy()).isEqualTo(Strategy.FAILOVER);
    }

    @Test
    void failoverKeepsDeclaredOrder() {
        stubInstances("a", "b", "c");
        assertThat(service().orderedCandidates("turing-router:failover/a,b,c"))
                .extracting(TurLLMInstance::getId)
                .containsExactly("a", "b", "c");
    }

    @Test
    void roundRobinRotatesAcrossCalls() {
        stubInstances("a", "b", "c");
        TurGatewayRouterService svc = service();
        String model = "turing-router:a,b,c";
        assertThat(svc.orderedCandidates(model).get(0).getId()).isEqualTo("a");
        assertThat(svc.orderedCandidates(model).get(0).getId()).isEqualTo("b");
        assertThat(svc.orderedCandidates(model).get(0).getId()).isEqualTo("c");
        assertThat(svc.orderedCandidates(model).get(0).getId()).isEqualTo("a");
    }

    @Test
    void latencyPrefersFastestObserved() {
        stubInstances("a", "b");
        TurGatewayRouterService svc = service();
        // b is fast, a is slow → b should come first.
        svc.recordLatency("a", 900);
        svc.recordLatency("b", 100);
        assertThat(svc.orderedCandidates("turing-router:latency/a,b").get(0).getId()).isEqualTo("b");
    }

    @Test
    void throwsWhenNoTargetResolves() {
        when(llmInstanceRepository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().orderedCandidates("turing-router:missing"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesCatalogStrategies() {
        assertThat(service().parse("turing-router:cost/a,b").strategy()).isEqualTo(Strategy.COST);
        assertThat(service().parse("turing-router:quality/a,b").strategy()).isEqualTo(Strategy.QUALITY);
        assertThat(service().parse("turing-router:ttft/a,b").strategy()).isEqualTo(Strategy.TTFT);
        assertThat(service().parse("turing-router:cheapest/a,b").strategy()).isEqualTo(Strategy.COST);
    }

    @Test
    void costPrefersCheapestCatalogPrice() {
        stubModel("a", "pricey", pricing(10.0));
        stubModel("b", "cheap", pricing(2.0));
        wireCatalog();
        assertThat(service().orderedCandidates("turing-router:cost/a,b"))
                .extracting(TurLLMInstance::getId).containsExactly("b", "a");
    }

    @Test
    void qualityPrefersHighestIntelligence() {
        stubModel("a", "weak", intelligence(40.0));
        stubModel("b", "strong", intelligence(85.0));
        wireCatalog();
        assertThat(service().orderedCandidates("turing-router:quality/a,b"))
                .extracting(TurLLMInstance::getId).containsExactly("b", "a");
    }

    @Test
    void ttftPrefersLowestTimeToFirstToken() {
        stubModel("a", "slow", ttft(1.2));
        stubModel("b", "fast", ttft(0.3));
        wireCatalog();
        assertThat(service().orderedCandidates("turing-router:ttft/a,b"))
                .extracting(TurLLMInstance::getId).containsExactly("b", "a");
    }

    @Test
    void catalogStrategySinksModelsWithoutTheMetricLast() {
        stubModel("a", "unpriced", intelligence(50.0)); // no pricing
        stubModel("b", "cheap", pricing(2.0));
        wireCatalog();
        // 'a' has no price → sorts last behind the priced 'b'.
        assertThat(service().orderedCandidates("turing-router:cost/a,b"))
                .extracting(TurLLMInstance::getId).containsExactly("b", "a");
    }
}
