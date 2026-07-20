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

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.gateway.TurGatewayRouterService.Route;
import com.viglet.turing.genai.gateway.TurGatewayRouterService.Strategy;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
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

    private TurGatewayRouterService service() {
        return new TurGatewayRouterService(llmInstanceRepository);
    }

    private TurLLMInstance instance(String id) {
        TurLLMInstance i = new TurLLMInstance();
        i.setId(id);
        i.setEnabled(1);
        return i;
    }

    private void stubInstances(String... ids) {
        for (String id : ids) {
            lenient().when(llmInstanceRepository.findById(id)).thenReturn(Optional.of(instance(id)));
        }
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
}
