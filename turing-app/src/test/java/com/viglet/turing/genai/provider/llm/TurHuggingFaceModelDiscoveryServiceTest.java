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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TurHuggingFaceModelDiscoveryService}. The live
 * {@code huggingface.co} query is forced offline (bogus base URL) so the
 * bundled catalog fallback + query filtering are exercised network-free.
 */
class TurHuggingFaceModelDiscoveryServiceTest {

    private TurHuggingFaceModelDiscoveryService service;

    @BeforeEach
    void setUp() {
        TurHuggingFaceModelCatalog catalog = new TurHuggingFaceModelCatalog();
        catalog.load();
        TurHuggingFaceRepoResolver resolver = new TurHuggingFaceRepoResolver("http://127.0.0.1:9");
        // Bogus base URL → live listing always fails → catalog fallback.
        service = new TurHuggingFaceModelDiscoveryService(resolver, catalog, "http://127.0.0.1:9", 30, 15);
    }

    @Test
    void fallsBackToCatalogWhenLiveUnavailable() {
        TurHuggingFaceModelDiscoveryService.Result result = service.listModels(null);
        assertEquals(TurHuggingFaceModelDiscoveryService.Source.CATALOG, result.source());
        assertFalse(result.models().isEmpty());
        assertTrue(result.models().stream()
                .anyMatch(m -> m.repoId().equals("sentence-transformers/all-MiniLM-L6-v2")));
        // Catalog entries are known-good ONNX repos.
        assertTrue(result.models().stream().allMatch(TurHuggingFaceModelOption::onnxVerified));
    }

    @Test
    void filtersCatalogByQuery() {
        TurHuggingFaceModelDiscoveryService.Result result = service.listModels("bge");
        assertEquals(TurHuggingFaceModelDiscoveryService.Source.CATALOG, result.source());
        assertFalse(result.models().isEmpty());
        assertTrue(result.models().stream().allMatch(m -> m.repoId().toLowerCase().contains("bge")));
    }

    @Test
    void reportsNoneWhenQueryMatchesNothing() {
        TurHuggingFaceModelDiscoveryService.Result result = service.listModels("no-such-model-xyz");
        assertEquals(TurHuggingFaceModelDiscoveryService.Source.NONE, result.source());
        assertTrue(result.models().isEmpty());
    }
}
