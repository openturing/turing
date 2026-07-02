/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;

/**
 * T499 / §X.20 — unit coverage for the cross-vendor context-caching seam: the
 * no-op default, the factory's vendor routing, and the Gemini provider's gating
 * + fail-open behavior. The successful {@code caches().create} round-trip needs a
 * live Google GenAI client and is exercised by the real-LLM-gated path, not here.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurContextCacheSeamTest {

    @Mock
    private TurNativeProviderClient nativeClient;

    private final TurNoOpContextCacheProvider noOp = new TurNoOpContextCacheProvider();

    private TurGeminiContextCacheProvider geminiProvider() {
        return new TurGeminiContextCacheProvider(nativeClient, new TurProviderOptionsParser());
    }

    private static TurLLMInstance instance(String pluginType) {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("inst-1");
        TurLLMVendor vendor = new TurLLMVendor();
        vendor.setId(pluginType);
        vendor.setPlugin(pluginType);
        instance.setTurLLMVendor(vendor);
        return instance;
    }

    private static TurContextCacheRequest request(TurLLMInstance instance) {
        return TurContextCacheRequest.ofSystemInstruction(
                instance, "gemini-2.0-flash", "a stable system prompt", Duration.ofMinutes(10));
    }

    // ----- no-op default -----

    @Test
    void noOpNeverCaches() {
        TurLLMInstance instance = instance("openai");
        assertThat(noOp.getPluginType()).isEqualTo("none");
        assertThat(noOp.supports(instance)).isFalse();
        assertThat(noOp.ensureCache(request(instance))).isEmpty();
    }

    // ----- factory routing -----

    @Test
    void factoryResolvesGeminiProviderForGeminiInstance() {
        TurGeminiContextCacheProvider gemini = geminiProvider();
        TurContextCacheProviderFactory factory =
                new TurContextCacheProviderFactory(List.of(noOp, gemini), noOp);

        assertThat(factory.resolve(instance("gemini"))).isSameAs(gemini);
    }

    @Test
    void factoryFallsBackToNoOpForNonGeminiInstance() {
        TurContextCacheProviderFactory factory =
                new TurContextCacheProviderFactory(List.of(noOp, geminiProvider()), noOp);

        assertThat(factory.resolve(instance("openai"))).isSameAs(noOp);
    }

    @Test
    void factoryFallsBackToNoOpWhenInstanceHasNoVendor() {
        TurContextCacheProviderFactory factory =
                new TurContextCacheProviderFactory(List.of(noOp, geminiProvider()), noOp);

        assertThat(factory.resolve(new TurLLMInstance())).isSameAs(noOp);
    }

    // ----- Gemini provider gating + fail-open -----

    @Test
    void geminiSupportsOnlyGeminiVendor() {
        TurGeminiContextCacheProvider gemini = geminiProvider();
        assertThat(gemini.getPluginType()).isEqualTo("gemini");
        assertThat(gemini.supports(instance("gemini"))).isTrue();
        assertThat(gemini.supports(instance("openai"))).isFalse();
        assertThat(gemini.supports(new TurLLMInstance())).isFalse();
    }

    @Test
    void geminiReturnsEmptyForEmptyOrModellessRequest() {
        TurGeminiContextCacheProvider gemini = geminiProvider();
        TurLLMInstance instance = instance("gemini");

        // Blank prefix.
        assertThat(gemini.ensureCache(TurContextCacheRequest.ofSystemInstruction(
                instance, "gemini-2.0-flash", "   ", null))).isEmpty();
        // Blank model.
        assertThat(gemini.ensureCache(TurContextCacheRequest.ofSystemInstruction(
                instance, "  ", "real prompt", null))).isEmpty();
    }

    @Test
    void geminiFailsOpenWhenNoNativeClient() {
        TurLLMInstance instance = instance("gemini");
        when(nativeClient.gemini(instance)).thenReturn(Optional.empty());
        TurGeminiContextCacheProvider gemini = geminiProvider();

        assertThat(gemini.ensureCache(request(instance))).isEmpty();
    }

    @Test
    void requestNormalizesNullPinnedContentsAndDetectsEmptiness() {
        TurLLMInstance instance = instance("gemini");
        TurContextCacheRequest blank = new TurContextCacheRequest(
                instance, "gemini-2.0-flash", "  ", null, null);
        assertThat(blank.pinnedContents()).isEmpty();
        assertThat(blank.isEmpty()).isTrue();

        TurContextCacheRequest withPinned = new TurContextCacheRequest(
                instance, "gemini-2.0-flash", null, List.of("big corpus block"), null);
        assertThat(withPinned.isEmpty()).isFalse();
    }

    // ----- T502 Anthropic provider (inline cache_control) -----

    @Test
    void anthropicProviderGatesOnOptInAndCarriesTtlInHandle() {
        TurAnthropicContextCacheProvider anthropic =
                new TurAnthropicContextCacheProvider(new TurProviderOptionsParser());
        assertThat(anthropic.getPluginType()).isEqualTo("anthropic");
        assertThat(anthropic.supports(instance("anthropic"))).isTrue();
        assertThat(anthropic.supports(instance("gemini"))).isFalse();

        TurLLMInstance off = instance("anthropic");
        assertThat(anthropic.ensureCache(request(off))).isEmpty();

        TurLLMInstance on = instance("anthropic");
        on.setProviderOptionsJson("{\"cacheControlEnabled\":true,\"cacheControlTtl\":\"1h\"}");
        Optional<TurContextCacheRef> ref = anthropic.ensureCache(request(on));
        assertThat(ref).isPresent();
        assertThat(ref.get().pluginType()).isEqualTo("anthropic");
        assertThat(ref.get().handle()).isEqualTo("1h");
    }

    @Test
    void factoryRoutesAnthropicInstanceToAnthropicProvider() {
        TurAnthropicContextCacheProvider anthropic =
                new TurAnthropicContextCacheProvider(new TurProviderOptionsParser());
        TurContextCacheProviderFactory factory =
                new TurContextCacheProviderFactory(List.of(noOp, anthropic), noOp);
        assertThat(factory.resolve(instance("anthropic"))).isSameAs(anthropic);
    }

    @Test
    void resolveTtlPrefersConfiguredSecondsElseDefault() {
        TurGeminiContextCacheProvider gemini = geminiProvider();

        assertThat(gemini.resolveTtl(java.util.Map.of("contextCacheTtlSeconds", 1800)))
                .isEqualTo(Duration.ofSeconds(1800));
        assertThat(gemini.resolveTtl(java.util.Map.of()))
                .isEqualTo(TurGeminiContextCacheProvider.DEFAULT_TTL);
    }
}
