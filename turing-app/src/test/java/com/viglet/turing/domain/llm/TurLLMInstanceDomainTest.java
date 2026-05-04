/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.domain.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Behaviour tests for {@link TurLLMInstanceDomain}. */
class TurLLMInstanceDomainTest {

    @Test
    void isEnabledIsTrueOnlyWhenEnabledFlagIsOne() {
        assertThat(withEnabled(1).isEnabled()).isTrue();
        assertThat(withEnabled(0).isEnabled()).isFalse();
        assertThat(withEnabled(2).isEnabled()).isFalse();
    }

    private static TurLLMInstanceDomain withEnabled(int enabled) {
        return new TurLLMInstanceDomain("id", "title", null, null, enabled, "https://x", null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                false);
    }
}
