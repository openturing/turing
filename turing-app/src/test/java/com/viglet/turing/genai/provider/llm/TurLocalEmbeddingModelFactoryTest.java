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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;

/**
 * Unit tests for {@link TurLocalEmbeddingModelFactory}. Only the cheap,
 * network-free paths are exercised — actually building a
 * {@code TransformersEmbeddingModel} would download an ONNX model, which is not
 * a unit-test concern.
 */
class TurLocalEmbeddingModelFactoryTest {

    private final TurLocalEmbeddingModelFactory factory =
            new TurLocalEmbeddingModelFactory(
                    new TurTransformersEmbeddingModelBuilder(new TurOnnxModelDownloader(""), ""));

    @Test
    void supportsOnlyTheLocalSentinelCaseInsensitively() {
        assertTrue(factory.supports("TRANSFORMERS_LOCAL"));
        assertTrue(factory.supports("transformers_local"));
        assertFalse(factory.supports("OPENAI"));
        assertFalse(factory.supports(""));
        assertFalse(factory.supports(null));
    }

    @Test
    void resolveThrowsWhenModelPathMissing() {
        TurEmbeddingModel model = new TurEmbeddingModel();
        model.setModelName("local");
        model.setProviderType(TurLocalEmbeddingModelFactory.PROVIDER_TYPE);
        model.setTokenizerPath("file:/tmp/tokenizer.json");
        // modelPath left null
        assertThrows(IllegalStateException.class, () -> factory.resolve(model));
    }

    @Test
    void resolveThrowsWhenTokenizerPathMissing() {
        TurEmbeddingModel model = new TurEmbeddingModel();
        model.setModelName("local");
        model.setProviderType(TurLocalEmbeddingModelFactory.PROVIDER_TYPE);
        model.setModelPath("file:/tmp/model.onnx");
        // tokenizerPath left null
        assertThrows(IllegalStateException.class, () -> factory.resolve(model));
    }
}
