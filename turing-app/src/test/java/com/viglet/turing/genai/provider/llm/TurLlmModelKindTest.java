/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.genai.provider.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for the pure {@link TurLlmModelKind#classify} heuristic (T750).
 */
class TurLlmModelKindTest {

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            // chat is the default for anything with no specialised keyword
            "gpt-4o,                    GPT-4o,                     CHAT",
            "claude-sonnet-4-5,         Claude Sonnet 4.5,          CHAT",
            "gemini-2.5-pro,            Gemini 2.5 Pro,             CHAT",
            "command-r,                 Command R,                  CHAT",
            "codestral-latest,          Codestral,                  CHAT",
            "llama3.2,                  Llama 3.2,                  CHAT",
            // embedding — id/label keyword or the voyage- family
            "text-embedding-3-small,    null,                       EMBEDDING",
            "mistral-embed,             Mistral Embed (embedding),  EMBEDDING",
            "voyage-3-large,            null,                       EMBEDDING",
            "gemini-embedding-001,      null,                       EMBEDDING",
            // rerank wins over embed even when the label carries the family
            "rerank-2,                  rerank-2 (rerank),          RERANK",
            "rerank-v3.5,               Rerank v3.5,                RERANK",
            // media kinds
            "dall-e-3,                  DALL-E 3,                   IMAGE",
            "gpt-image-1,               GPT Image 1,                IMAGE",
            "imagen-3.0-generate-002,   Imagen 3,                   IMAGE",
            "whisper-1,                 Whisper,                    TRANSCRIPTION",
            "gpt-4o-transcribe,         GPT-4o Transcribe,          TRANSCRIPTION",
            "tts-1,                     TTS 1,                      SPEECH",
            "gpt-4o-mini-tts,           GPT-4o mini TTS,            SPEECH",
            "sora,                      Sora,                       VIDEO",
            "veo-3.0-generate-preview,  Veo 3,                      VIDEO",
            "omni-moderation-latest,    Omni Moderation,            MODERATION",
    })
    void classifiesRepresentativeIds(String id, String label, TurLlmModelKind expected) {
        assertThat(TurLlmModelKind.classify(id, label)).isEqualTo(expected);
    }

    @Test
    void blankOrNullIdIsUnknown() {
        assertThat(TurLlmModelKind.classify(null, "whatever")).isEqualTo(TurLlmModelKind.UNKNOWN);
        assertThat(TurLlmModelKind.classify("", "whatever")).isEqualTo(TurLlmModelKind.UNKNOWN);
        assertThat(TurLlmModelKind.classify("   ", null)).isEqualTo(TurLlmModelKind.UNKNOWN);
    }

    @Test
    void fromGeminiGenerationMethodsUsesAuthoritativeSignal() {
        // authoritative methods win even when the id would heuristically read as chat
        assertThat(TurLlmModelKind.fromGeminiGenerationMethods(
                List.of("embedContent"), "some-model", null)).isEqualTo(TurLlmModelKind.EMBEDDING);
        assertThat(TurLlmModelKind.fromGeminiGenerationMethods(
                List.of("generateContent", "countTokens"), "gemini-2.5-pro", null))
                .isEqualTo(TurLlmModelKind.CHAT);
        assertThat(TurLlmModelKind.fromGeminiGenerationMethods(
                List.of("predictLongRunning"), "veo", null)).isEqualTo(TurLlmModelKind.VIDEO);
        assertThat(TurLlmModelKind.fromGeminiGenerationMethods(
                List.of("predict"), "imagen-x", null)).isEqualTo(TurLlmModelKind.IMAGE);
        // absent methods → fall back to the name heuristic
        assertThat(TurLlmModelKind.fromGeminiGenerationMethods(
                null, "text-embedding-004", null)).isEqualTo(TurLlmModelKind.EMBEDDING);
        assertThat(TurLlmModelKind.fromGeminiGenerationMethods(
                List.of(), "gemini-2.5-flash", null)).isEqualTo(TurLlmModelKind.CHAT);
    }

    @Test
    void fromVendorEndpointsUsesAuthoritativeSignal() {
        // rerank endpoint wins even though the id has no "rerank" token
        assertThat(TurLlmModelKind.fromVendorEndpoints(
                List.of("rerank"), "model-x", null)).isEqualTo(TurLlmModelKind.RERANK);
        assertThat(TurLlmModelKind.fromVendorEndpoints(
                List.of("embed"), "model-y", null)).isEqualTo(TurLlmModelKind.EMBEDDING);
        assertThat(TurLlmModelKind.fromVendorEndpoints(
                List.of("chat"), "command-x", null)).isEqualTo(TurLlmModelKind.CHAT);
        // absent → heuristic
        assertThat(TurLlmModelKind.fromVendorEndpoints(
                null, "rerank-2", null)).isEqualTo(TurLlmModelKind.RERANK);
    }

    @Test
    void parseOrClassifyHonoursValidOverrideElseFallsBack() {
        // explicit valid override wins over the heuristic
        assertThat(TurLlmModelKind.parseOrClassify("embedding", "some-chatty-id", null))
                .isEqualTo(TurLlmModelKind.EMBEDDING);
        assertThat(TurLlmModelKind.parseOrClassify("CHAT", "text-embedding-3-small", null))
                .isEqualTo(TurLlmModelKind.CHAT);
        // unknown/blank override falls back to the classifier
        assertThat(TurLlmModelKind.parseOrClassify("bogus", "text-embedding-3-small", null))
                .isEqualTo(TurLlmModelKind.EMBEDDING);
        assertThat(TurLlmModelKind.parseOrClassify(null, "gpt-4o", null))
                .isEqualTo(TurLlmModelKind.CHAT);
    }
}
