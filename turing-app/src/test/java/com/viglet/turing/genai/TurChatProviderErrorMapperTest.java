/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;

class TurChatProviderErrorMapperTest {

    @Test
    void nullErrorIsGeneric() {
        assertThat(TurChatProviderErrorMapper.toUserMessage(null))
                .isEqualTo(TurChatProviderErrorMapper.MSG_GENERIC);
    }

    @Test
    void permissionErrorMapsToConfigAndNeverLeaksProviderDetail() {
        Throwable err = new RuntimeException(
                "403: Your organization must be verified to use the model `gpt-image-1`. "
                        + "Please go to: https://platform.openai.com/settings/organization/general");
        String message = TurChatProviderErrorMapper.toUserMessage(err);
        // Maps to the generic config message and never leaks the raw provider detail.
        assertThat(message)
                .isEqualTo(TurChatProviderErrorMapper.MSG_CONFIG)
                .doesNotContain("gpt-image-1", "organization", "platform.openai.com", "403");
    }

    @Test
    void authAndBillingMapToConfig() {
        assertThat(TurChatProviderErrorMapper.toUserMessage(new RuntimeException("401: invalid api key")))
                .isEqualTo(TurChatProviderErrorMapper.MSG_CONFIG);
        assertThat(TurChatProviderErrorMapper.toUserMessage(new RuntimeException("402: billing required")))
                .isEqualTo(TurChatProviderErrorMapper.MSG_CONFIG);
    }

    @Test
    void rateLimitMapsToBusy() {
        assertThat(TurChatProviderErrorMapper.toUserMessage(new RuntimeException("429: rate limit exceeded")))
                .isEqualTo(TurChatProviderErrorMapper.MSG_BUSY);
    }

    @Test
    void serverErrorMapsToUnavailable() {
        assertThat(TurChatProviderErrorMapper.toUserMessage(new RuntimeException("503: upstream down")))
                .isEqualTo(TurChatProviderErrorMapper.MSG_UNAVAILABLE);
    }

    @Test
    void unknownErrorIsGeneric() {
        assertThat(TurChatProviderErrorMapper.toUserMessage(new IllegalStateException("connection reset")))
                .isEqualTo(TurChatProviderErrorMapper.MSG_GENERIC);
    }

    @Test
    void readsStatusFromNestedCause() {
        Throwable root = new IllegalStateException("429: too many requests");
        Throwable wrapper = new RuntimeException("stream failed", root);
        assertThat(TurChatProviderErrorMapper.toUserMessage(wrapper))
                .isEqualTo(TurChatProviderErrorMapper.MSG_BUSY);
    }

    @Test
    void portugueseLocaleReturnsPortugueseMessages() {
        Locale ptBr = Locale.forLanguageTag("pt-BR");
        assertThat(TurChatProviderErrorMapper.toUserMessage(new RuntimeException("403: verify org"), ptBr))
                .isEqualTo(TurChatProviderErrorMapper.PT_CONFIG);
        assertThat(TurChatProviderErrorMapper.toUserMessage(new RuntimeException("429: slow down"), ptBr))
                .isEqualTo(TurChatProviderErrorMapper.PT_BUSY);
        assertThat(TurChatProviderErrorMapper.toUserMessage(new RuntimeException("503: down"), ptBr))
                .isEqualTo(TurChatProviderErrorMapper.PT_UNAVAILABLE);
        assertThat(TurChatProviderErrorMapper.toUserMessage(new IllegalStateException("boom"), ptBr))
                .isEqualTo(TurChatProviderErrorMapper.PT_GENERIC);
    }

    @Test
    void nonPortugueseLocaleAndNullFallBackToEnglish() {
        assertThat(TurChatProviderErrorMapper.toUserMessage(new RuntimeException("403: x"), Locale.FRENCH))
                .isEqualTo(TurChatProviderErrorMapper.MSG_CONFIG);
        assertThat(TurChatProviderErrorMapper.toUserMessage(new RuntimeException("403: x"), null))
                .isEqualTo(TurChatProviderErrorMapper.MSG_CONFIG);
    }
}
