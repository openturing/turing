/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.transcription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * T687 / §XLII.1 — {@link TurTranscriptionProviderFactory} routing: providers
 * indexed by declared type, an unregistered type falls back to {@code OPENAI},
 * and {@code resolveActive()} reads the effective strategy from the resolver.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurTranscriptionProviderFactoryTest {

    private TurTranscriptionProvider provider(TurTranscriptionProviderType type) {
        TurTranscriptionProvider p = mock(TurTranscriptionProvider.class);
        when(p.getType()).thenReturn(type);
        return p;
    }

    @Test
    void routesByDeclaredType() {
        TurTranscriptionProvider openai = provider(TurTranscriptionProviderType.OPENAI);
        TurTranscriptionProvider none = provider(TurTranscriptionProviderType.NONE);
        TurTranscriptionConfigResolver resolver = mock(TurTranscriptionConfigResolver.class);

        var factory = new TurTranscriptionProviderFactory(List.of(openai, none), resolver);

        assertThat(factory.resolve(TurTranscriptionProviderType.OPENAI)).isSameAs(openai);
        assertThat(factory.resolve(TurTranscriptionProviderType.NONE)).isSameAs(none);
    }

    @Test
    void fallsBackToOpenAiForUnregisteredType() {
        TurTranscriptionProvider openai = provider(TurTranscriptionProviderType.OPENAI);
        TurTranscriptionConfigResolver resolver = mock(TurTranscriptionConfigResolver.class);

        var factory = new TurTranscriptionProviderFactory(List.of(openai), resolver);

        // OPENAI_COMPATIBLE has no bean in this list → degrade to OPENAI, never null.
        assertThat(factory.resolve(TurTranscriptionProviderType.OPENAI_COMPATIBLE)).isSameAs(openai);
    }

    @Test
    void resolveActiveReadsResolverStrategy() {
        TurTranscriptionProvider openai = provider(TurTranscriptionProviderType.OPENAI);
        TurTranscriptionProvider none = provider(TurTranscriptionProviderType.NONE);
        TurTranscriptionConfigResolver resolver = mock(TurTranscriptionConfigResolver.class);
        when(resolver.resolve()).thenReturn(new TurTranscriptionConfig(
                TurTranscriptionProviderType.NONE, "", "", "", 26_214_400L));

        var factory = new TurTranscriptionProviderFactory(List.of(openai, none), resolver);

        assertThat(factory.resolveActive()).isSameAs(none);
    }
}
