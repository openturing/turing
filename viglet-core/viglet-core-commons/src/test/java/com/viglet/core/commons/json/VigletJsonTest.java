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
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.core.commons.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.Map;

import org.junit.jupiter.api.Test;

class VigletJsonTest {

    @Test
    void acceptsObjectsAndArrays() {
        assertThat(VigletJson.isValidJson("{\"a\":1}")).isTrue();
        assertThat(VigletJson.isValidJson("[1,2,3]")).isTrue();
    }

    @Test
    void rejectsMalformedJson() {
        assertThat(VigletJson.isValidJson("{not json")).isFalse();
        assertThat(VigletJson.isValidJson("plain text")).isFalse();
    }

    @Test
    void serializesObjectToJsonString() {
        assertThatNoException().isThrownBy(() -> {
            String json = VigletJson.asJsonString(Map.of("name", "turing"));
            assertThat(json).contains("\"name\"").contains("\"turing\"");
        });
    }
}
