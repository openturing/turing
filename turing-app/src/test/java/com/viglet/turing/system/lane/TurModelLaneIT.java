/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.system.lane;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * T517 / §XXVIII.13 — boots the full Spring context to assert the model-lane
 * config vars are seeded blank (legacy: every stage rides the default LLM) and
 * that binding a lane round-trips through Global Settings and is honoured by the
 * resolver.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurModelLaneIT extends AbstractTuringSpringIT {

    @Autowired
    private TurGlobalSettingsService globalSettingsService;

    @Autowired
    private TurModelLaneService modelLaneService;

    @Test
    void lanesAreBlankByDefaultSoStagesUseTheDefaultLlm() {
        assertThat(globalSettingsService.getModelLaneInstanceId(TurModelLane.FAST)).isEmpty();
        assertThat(globalSettingsService.getModelLaneInstanceId(TurModelLane.REASONING)).isEmpty();
        assertThat(globalSettingsService.getModelLaneInstanceId(TurModelLane.CHEAP)).isEmpty();
        // No lane bound and no default LLM in a fresh DB → resolves to the
        // (blank) default id, i.e. the legacy single-model path.
        assertThat(modelLaneService.resolveInstanceId(TurModelStage.RERANK))
                .isEqualTo(globalSettingsService.getDefaultLlmId());
    }

    @Test
    void bindingALaneRoundTripsAndFallsBackWhenInstanceMissing() {
        globalSettingsService.updateModelLaneInstanceId(TurModelLane.REASONING, "no-such-instance");
        assertThat(globalSettingsService.getModelLaneInstanceId(TurModelLane.REASONING))
                .isEqualTo("no-such-instance");
        // The bound instance does not exist → resolver fails safe to the default.
        assertThat(modelLaneService.resolveInstanceId(TurModelStage.JUDGE))
                .isEqualTo(globalSettingsService.getDefaultLlmId());
        // cleanup so we don't leak into other tests sharing the per-class context
        globalSettingsService.updateModelLaneInstanceId(TurModelLane.REASONING, "");
    }
}
