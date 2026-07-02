/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.genai.Client;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.genai.nativeapi.files.TurFilesApiBridge;
import com.viglet.turing.genai.nativeapi.gemini.TurGeminiVideoUnderstandingService.VideoUnderstanding;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;

/**
 * T501 / §X.19 — unit coverage for the deterministic surface of Gemini video
 * understanding: the vendor gate, fail-open paths, the labeled-answer parser, and
 * per-instance model resolution. The live {@code generateContent} call needs a
 * real Google GenAI client (a {@code final} class) and is exercised by the
 * real-LLM-gated path, not here.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurGeminiVideoUnderstandingServiceTest {

    @Mock
    private TurNativeProviderClient nativeClient;
    @Mock
    private TurFilesApiBridge filesApiBridge;

    private TurGeminiVideoUnderstandingService service() {
        return new TurGeminiVideoUnderstandingService(nativeClient, filesApiBridge,
                new TurProviderOptionsParser());
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

    @Test
    void supportsOnlyGeminiVendor() {
        TurGeminiVideoUnderstandingService service = service();
        assertThat(service.supports(instance("gemini"))).isTrue();
        assertThat(service.supports(instance("openai"))).isFalse();
        assertThat(service.supports(new TurLLMInstance())).isFalse();
    }

    @Test
    void understandReturnsEmptyForNonGeminiInstance() {
        assertThat(service().understand(instance("openai"), new byte[] {1, 2, 3}, "video/mp4", "clip"))
                .isEmpty();
    }

    @Test
    void understandReturnsEmptyForEmptyOrTypelessInput() {
        TurGeminiVideoUnderstandingService service = service();
        TurLLMInstance gemini = instance("gemini");
        assertThat(service.understand(gemini, new byte[0], "video/mp4", "c")).isEmpty();
        assertThat(service.understand(gemini, new byte[] {1}, "  ", "c")).isEmpty();
        assertThat(service.understandUrl(gemini, "  ", "video/mp4")).isEmpty();
    }

    @Test
    void understandFailsOpenWhenNoNativeClient() {
        TurLLMInstance gemini = instance("gemini");
        lenient().when(nativeClient.gemini(gemini)).thenReturn(Optional.<Client>empty());
        assertThat(service().understand(gemini, new byte[] {1, 2, 3}, "video/mp4", "clip")).isEmpty();
        assertThat(service().understandUrl(gemini, "https://youtu.be/x", "video/mp4")).isEmpty();
    }

    @Test
    void parseSplitsTranscriptAndScenes() {
        String answer = """
                TRANSCRIPT:
                [00:01] Hello world
                [00:05] Second line
                SCENES:
                [00:00] A title card
                [00:04] A person speaking""";
        VideoUnderstanding u = TurGeminiVideoUnderstandingService.parse(answer);
        assertThat(u.transcript()).contains("[00:01] Hello world").doesNotContain("title card");
        assertThat(u.sceneDescription()).contains("A title card").doesNotContain("Hello world");
        assertThat(u.rawText()).isEqualTo(answer);
    }

    @Test
    void parsePreservesRawTextWhenUnlabeled() {
        VideoUnderstanding u = TurGeminiVideoUnderstandingService.parse("just some prose");
        assertThat(u.transcript()).isEmpty();
        assertThat(u.sceneDescription()).isEmpty();
        assertThat(u.rawText()).isEqualTo("just some prose");
    }

    @Test
    void resolveModelPrefersVideoModelThenChatModelThenDefault() {
        TurGeminiVideoUnderstandingService service = service();
        TurLLMInstance gemini = instance("gemini");
        assertThat(service.resolveModel(gemini)).isEqualTo(TurGeminiVideoUnderstandingService.DEFAULT_MODEL);

        gemini.setProviderOptionsJson("{\"chatModel\":\"gemini-2.5-flash\"}");
        assertThat(service.resolveModel(gemini)).isEqualTo("gemini-2.5-flash");

        gemini.setProviderOptionsJson("{\"videoModel\":\"gemini-2.5-pro\",\"chatModel\":\"gemini-2.5-flash\"}");
        assertThat(service.resolveModel(gemini)).isEqualTo("gemini-2.5-pro");
    }
}
