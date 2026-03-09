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

package com.viglet.turing.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit tests for TurUtils.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurUtilsTest {

    @Test
    void testConstructorThrowsException() throws Exception {
        var constructor = TurUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Utility class");
    }

    @ParameterizedTest
    @MethodSource("urlTemplateProvider")
    void testGetUrlTemplateParameterized(String serviceUrl, String id, String expected) {
        String result = TurUtils.getUrlTemplate(serviceUrl, id);
        assertThat(result).isEqualTo(expected);
    }

    static java.util.stream.Stream<Arguments> urlTemplateProvider() {
        return java.util.stream.Stream.of(
                Arguments.of("http://example.com", "12345",
                        "http://example.com/12345"),
                Arguments.of("http://example.com/", "12345",
                        "http://example.com//12345"),
                Arguments.of("http://example.com/api", "item-123",
                        "http://example.com/api/item-123"),
                Arguments.of("http://example.com", "", "http://example.com/"),
                Arguments.of("http://example.com/api",
                        "item-with-dashes_and_underscores", "http://example.com/api/item-with-dashes_and_underscores"),
                Arguments.of("http://localhost:8080/service", "999",
                        "http://localhost:8080/service/999"));
    }
}
