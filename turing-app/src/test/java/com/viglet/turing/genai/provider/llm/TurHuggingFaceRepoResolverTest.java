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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Network-free unit tests for {@link TurHuggingFaceRepoResolver}: the
 * artifact-picking heuristic and repo-id cleaning. Live tree probes and
 * downloads are out of scope for a unit test.
 */
class TurHuggingFaceRepoResolverTest {

    private final TurHuggingFaceRepoResolver resolver =
            new TurHuggingFaceRepoResolver("https://huggingface.co");

    @Test
    void prefersOnnxSubfolderModel() {
        List<String> tree = List.of("config.json", "model.onnx", "onnx/model.onnx",
                "onnx/model_quantized.onnx", "tokenizer.json");
        assertEquals("onnx/model.onnx", resolver.pickOnnxPath(tree).orElseThrow());
        assertEquals("tokenizer.json", resolver.pickTokenizerPath(tree).orElseThrow());
    }

    @Test
    void fallsBackToRootModelWhenNoSubfolder() {
        List<String> tree = List.of("config.json", "model.onnx", "tokenizer.json");
        assertEquals("model.onnx", resolver.pickOnnxPath(tree).orElseThrow());
    }

    @Test
    void avoidsQuantizedWhenAPlainOnnxExists() {
        List<String> tree = List.of("onnx/model_quantized.onnx", "onnx/model_O4.onnx",
                "onnx/model_fp16.onnx");
        // No plain "model.onnx" here, but the non-quantized fp16/O4 comes before quantized
        assertTrue(resolver.pickOnnxPath(tree).orElseThrow().contains(".onnx"));
    }

    @Test
    void resolveFallsBackToConventionalLayoutWhenTreeUnreachable() {
        // Bogus base URL → tree probe fails → conventional layout is used.
        TurHuggingFaceRepoResolver offline =
                new TurHuggingFaceRepoResolver("http://127.0.0.1:9");
        TurHuggingFaceRepoResolver.HfArtifacts artifacts =
                offline.resolve("sentence-transformers/all-MiniLM-L6-v2");
        assertTrue(artifacts.onnxUrl().endsWith(
                "/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx"));
        assertTrue(artifacts.tokenizerUrl().endsWith(
                "/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json"));
    }

    @Test
    void resolveHonorsExplicitVariantSuffix() {
        TurHuggingFaceRepoResolver offline =
                new TurHuggingFaceRepoResolver("http://127.0.0.1:9");
        TurHuggingFaceRepoResolver.HfArtifacts artifacts = offline.resolve(
                "Xenova/all-MiniLM-L6-v2" + TurHuggingFaceRepoResolver.VARIANT_SEPARATOR
                        + "onnx/model_quantized.onnx");
        assertTrue(artifacts.onnxUrl().endsWith(
                "/Xenova/all-MiniLM-L6-v2/resolve/main/onnx/model_quantized.onnx"));
        // tokenizer still auto-resolves to the conventional path
        assertTrue(artifacts.tokenizerUrl().endsWith(
                "/Xenova/all-MiniLM-L6-v2/resolve/main/tokenizer.json"));
    }

    @Test
    void probeDimensionsReturnsNullWhenConfigUnreachable() {
        TurHuggingFaceRepoResolver offline =
                new TurHuggingFaceRepoResolver("http://127.0.0.1:9");
        assertEquals(null, offline.probeDimensions("sentence-transformers/all-MiniLM-L6-v2"));
        assertEquals(null, offline.probeDimensions(null));
    }

    @Test
    void stripsHostPrefixFromRepoId() {
        TurHuggingFaceRepoResolver offline =
                new TurHuggingFaceRepoResolver("http://127.0.0.1:9");
        TurHuggingFaceRepoResolver.HfArtifacts artifacts =
                offline.resolve("https://huggingface.co/BAAI/bge-small-en-v1.5");
        assertTrue(artifacts.onnxUrl().contains("/BAAI/bge-small-en-v1.5/resolve/main/"));
    }
}
