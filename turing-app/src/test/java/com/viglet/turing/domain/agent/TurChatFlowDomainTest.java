/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.agent.TurChatFlowGuardrailMethod;
import com.viglet.turing.persistence.model.agent.TurChatFlowTriggerMode;

/** Behaviour tests for {@link TurChatFlowDomain}. */
class TurChatFlowDomainTest {

    @Test
    void isEnabledIsTrueOnlyWhenEnabledFlagIsOne() {
        assertThat(buildFlow(1, "trigger").isEnabled()).isTrue();
        assertThat(buildFlow(0, "trigger").isEnabled()).isFalse();
    }

    @Test
    void canAutoTriggerRespectsBlankDescription() {
        assertThat(buildFlow(1, "Activate when user asks for refund").canAutoTrigger()).isTrue();
        assertThat(buildFlow(1, null).canAutoTrigger()).isFalse();
        assertThat(buildFlow(1, "").canAutoTrigger()).isFalse();
        assertThat(buildFlow(1, "   ").canAutoTrigger()).isFalse();
    }

    private static TurChatFlowDomain buildFlow(int enabled, String triggerDescription) {
        return new TurChatFlowDomain("f-1", "name", null, "{}", enabled,
                TurChatFlowGuardrailMethod.HEURISTIC, triggerDescription,
                TurChatFlowTriggerMode.ONCE, "agent-1");
    }
}
