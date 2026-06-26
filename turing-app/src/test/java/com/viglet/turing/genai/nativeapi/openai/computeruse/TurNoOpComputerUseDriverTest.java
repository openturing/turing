/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.openai.computeruse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TurNoOpComputerUseDriverTest {

    private final TurComputerUseDriver driver = new TurNoOpComputerUseDriver();

    @Test
    void isNeverAvailable() {
        assertThat(driver.isAvailable()).isFalse();
    }

    @Test
    void startThrowsBecauseNoBackendIsConfigured() {
        assertThatThrownBy(() -> driver.start(TurComputerUseEnvironment.BROWSER, 1024, 768, null))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("driver");
    }
}
