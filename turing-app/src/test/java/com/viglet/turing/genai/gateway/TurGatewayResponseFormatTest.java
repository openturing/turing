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

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.TurUnsupportedParameterException;
import com.viglet.turing.genai.gateway.TurOpenAiWire.ResponseFormat;

/**
 * T1090 — the gateway's own handling of {@code response_format}: what counts as
 * requested, its cache slot, and the refusal on routes that answer prose. The
 * mapping to the provider is {@code TurPromptOverridesTest}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurGatewayResponseFormatTest {

    private static final ResponseFormat JSON_OBJECT = new ResponseFormat("json_object", null);

    @Test
    void textOrAbsentIsNotRequested() {
        assertThat(TurGatewayResponseFormat.requested(null)).isFalse();
        assertThat(TurGatewayResponseFormat.requested(new ResponseFormat("text", null))).isFalse();
        assertThat(TurGatewayResponseFormat.requested(JSON_OBJECT)).isTrue();
    }

    @Test
    void jsonModeHasItsOwnCacheSlot() {
        assertThat(TurGatewayResponseFormat.cacheModelKey("gpt", null)).isEqualTo("gpt");
        assertThat(TurGatewayResponseFormat.cacheModelKey("gpt", JSON_OBJECT)).isNotEqualTo("gpt");
    }

    @Test
    void aRoutedModelRefusesJsonModeNamingTheField() {
        assertThatThrownBy(() -> TurGatewayResponseFormat.rejectIfRequested(JSON_OBJECT, "turing-sn:site"))
                .isInstanceOfSatisfying(TurUnsupportedParameterException.class,
                        e -> assertThat(e.getParam()).isEqualTo("response_format"));
    }
}
