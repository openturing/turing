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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.repository.embedding.TurEmbeddingModelRepository;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * Unit tests for {@link TurEmbeddingDimensionService}. Uses an offline resolver
 * (probe always returns null) so the compare logic is exercised network-free.
 */
class TurEmbeddingDimensionServiceTest {

    private final TurHuggingFaceRepoResolver offlineResolver =
            new TurHuggingFaceRepoResolver("http://127.0.0.1:9");
    private final TurGlobalSettingsService settings = mock(TurGlobalSettingsService.class);
    private final TurEmbeddingModelRepository repo = mock(TurEmbeddingModelRepository.class);
    private final TurEmbeddingDimensionService service =
            new TurEmbeddingDimensionService(offlineResolver, settings, repo);

    @Test
    void noDefaultConfigured_neverFlagsMismatch() {
        when(settings.getDefaultEmbeddingModelId()).thenReturn("");

        TurEmbeddingDimensionService.DimensionCheck check =
                service.checkHuggingFace("sentence-transformers/all-MiniLM-L6-v2");

        // Offline probe → unknown dimensions; no default → never differs.
        assertThat(check.dimensions()).isNull();
        assertThat(check.defaultDimensions()).isNull();
        assertThat(check.differs()).isFalse();
    }
}
