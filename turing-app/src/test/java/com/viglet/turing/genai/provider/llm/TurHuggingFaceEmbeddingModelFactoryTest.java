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
 * Unit tests for {@link TurHuggingFaceEmbeddingModelFactory}. Only the cheap,
 * network-free paths are exercised — actually resolving a repo would hit the
 * HuggingFace tree API and download an ONNX model.
 */
class TurHuggingFaceEmbeddingModelFactoryTest {

    private final TurHuggingFaceEmbeddingModelFactory factory =
            new TurHuggingFaceEmbeddingModelFactory(
                    new TurHuggingFaceRepoResolver("https://huggingface.co"),
                    new TurTransformersEmbeddingModelBuilder(new TurOnnxModelDownloader(""), ""));

    @Test
    void supportsOnlyTheHuggingFaceSentinelCaseInsensitively() {
        assertTrue(factory.supports("HUGGINGFACE"));
        assertTrue(factory.supports("huggingface"));
        assertFalse(factory.supports("TRANSFORMERS_LOCAL"));
        assertFalse(factory.supports("OPENAI"));
        assertFalse(factory.supports(""));
        assertFalse(factory.supports(null));
    }

    @Test
    void resolveThrowsWhenRepoIdMissing() {
        TurEmbeddingModel model = new TurEmbeddingModel();
        model.setModelName("hf");
        model.setProviderType(TurHuggingFaceEmbeddingModelFactory.PROVIDER_TYPE);
        // modelReference (repo id) left null
        assertThrows(IllegalStateException.class, () -> factory.resolve(model));
    }
}
