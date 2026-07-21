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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link TurLlmModelCatalog} parses the T759 canonical <em>envelope</em>
 * catalog ({@code {version, lastUpdated, vendors:{...}}}) from the real bundled
 * resource, honouring the per-entry {@code kind}.
 */
class TurLlmModelCatalogTest {

    private final TurLlmModelCatalog catalog = new TurLlmModelCatalog();

    @BeforeEach
    void load() {
        catalog.load();
    }

    @Test
    void loadsVendorsFromTheEnvelope() {
        List<TurLlmModelOption> openai = catalog.staticModels("openai");
        assertThat(openai).isNotEmpty();
        assertThat(openai).extracting(TurLlmModelOption::id).contains("gpt-4o");
    }

    @Test
    void everyEntryCarriesAKind() {
        assertThat(catalog.staticModels("openai"))
                .allSatisfy(o -> assertThat(o.kind()).isNotNull());
    }

    @Test
    void honoursTheExplicitKindPerEntry() {
        // openai has both chat and embedding kinds in the catalog
        assertThat(catalog.staticModels("openai"))
                .anyMatch(o -> o.kind() == TurLlmModelKind.EMBEDDING)
                .anyMatch(o -> o.kind() == TurLlmModelKind.CHAT);
        // cohere carries a rerank model
        assertThat(catalog.staticModels("cohere"))
                .anyMatch(o -> o.kind() == TurLlmModelKind.RERANK);
    }

    @Test
    void unknownVendorYieldsEmpty() {
        assertThat(catalog.staticModels("does-not-exist")).isEmpty();
    }
}
