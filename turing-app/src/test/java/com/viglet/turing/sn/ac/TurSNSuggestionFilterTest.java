/*
 * Copyright (C) 2016-2025 the original author or authors.
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

package com.viglet.turing.sn.ac;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for TurSNSuggestionFilter.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
class TurSNSuggestionFilterTest {

    @Test
    void testDefaultStrategyFiltersStopWordsAndLength() {
        TurSNSuggestionFilter filter = new TurSNSuggestionFilter(List.of("the"));
        filter.defaultStrategyConfig(1);

        List<String> result = filter.filter(List.of("the world", "hello world", "hello"));

        assertThat(result).containsExactly("hello");
    }

    @Test
    void testAutomatonStrategyFiltersSuggestions() {
        TurSNSuggestionFilter filter = new TurSNSuggestionFilter(List.of());
        filter.automatonStrategyConfig(2);

        List<String> result = filter.filter(List.of("hello world"));

        assertThat(result).containsExactly("hello world");
    }
}
