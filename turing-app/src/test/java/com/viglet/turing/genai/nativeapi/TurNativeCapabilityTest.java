/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurNativeCapabilityTest {

    @Test
    void fromKeyIsCaseInsensitiveAndTrimmed() {
        assertThat(TurNativeCapability.fromKey("openai-web-search"))
                .contains(TurNativeCapability.OPENAI_WEB_SEARCH);
        assertThat(TurNativeCapability.fromKey("  OPENAI-WEB-SEARCH  "))
                .contains(TurNativeCapability.OPENAI_WEB_SEARCH);
    }

    @Test
    void fromKeyReturnsEmptyForUnknownOrNull() {
        assertThat(TurNativeCapability.fromKey("does-not-exist")).isEmpty();
        assertThat(TurNativeCapability.fromKey(null)).isEmpty();
    }

    @Test
    void everyCapabilityHasAUniqueNonBlankKey() {
        long distinctKeys = java.util.Arrays.stream(TurNativeCapability.values())
                .map(TurNativeCapability::getKey)
                .distinct()
                .count();
        assertThat(distinctKeys).isEqualTo(TurNativeCapability.values().length);
        assertThat(TurNativeCapability.values())
                .allSatisfy(c -> assertThat(c.getKey()).isNotBlank());
    }

    @Test
    void isForPluginMatchesVendorCaseInsensitively() {
        assertThat(TurNativeCapability.OPENAI_WEB_SEARCH.isForPlugin("openai")).isTrue();
        assertThat(TurNativeCapability.OPENAI_WEB_SEARCH.isForPlugin("OpenAI")).isTrue();
        assertThat(TurNativeCapability.OPENAI_WEB_SEARCH.isForPlugin("anthropic")).isFalse();
        assertThat(TurNativeCapability.OPENAI_WEB_SEARCH.isForPlugin(null)).isFalse();
    }
}
