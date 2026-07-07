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

/**
 * T517 — every stage maps to a lane, and the interactive stages ride FAST while
 * the quality-sensitive stages ride REASONING and background rides CHEAP.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurModelStageTest {

    @Test
    void interactiveStagesRideFast() {
        assertThat(TurModelStage.AUTOCOMPLETE.lane()).isEqualTo(TurModelLane.FAST);
        assertThat(TurModelStage.SPELL.lane()).isEqualTo(TurModelLane.FAST);
        assertThat(TurModelStage.QUERY_REWRITE.lane()).isEqualTo(TurModelLane.FAST);
    }

    @Test
    void qualitySensitiveStagesRideReasoning() {
        assertThat(TurModelStage.ROUTER.lane()).isEqualTo(TurModelLane.REASONING);
        assertThat(TurModelStage.RERANK.lane()).isEqualTo(TurModelLane.REASONING);
        assertThat(TurModelStage.JUDGE.lane()).isEqualTo(TurModelLane.REASONING);
    }

    @Test
    void backgroundStageRidesCheap() {
        assertThat(TurModelStage.BACKGROUND_SUMMARIZATION.lane()).isEqualTo(TurModelLane.CHEAP);
    }

    @Test
    void everyStageHasALane() {
        for (TurModelStage stage : TurModelStage.values()) {
            assertThat(stage.lane()).isNotNull();
        }
    }
}
