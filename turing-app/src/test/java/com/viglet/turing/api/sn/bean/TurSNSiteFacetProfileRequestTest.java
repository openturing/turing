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
package com.viglet.turing.api.sn.bean;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * Regression: the facet profile create/update payload must deserialize from a
 * partial body (no primitives) under Jackson 3, whose
 * {@code FAIL_ON_NULL_FOR_PRIMITIVES} defaults to {@code true}.
 */
class TurSNSiteFacetProfileRequestTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void deserializesBodyWithOnlyName() {
        TurSNSiteFacetProfileRequest request =
                mapper.readValue("{\"name\":\"Cursos EE\"}", TurSNSiteFacetProfileRequest.class);
        assertThat(request.name()).isEqualTo("Cursos EE");
        assertThat(request.description()).isNull();
    }

    @Test
    void deserializesBodyWithNameAndDescription() {
        TurSNSiteFacetProfileRequest request = mapper.readValue(
                "{\"name\":\"Cursos EE\",\"description\":\"Ordenacao para EE\"}",
                TurSNSiteFacetProfileRequest.class);
        assertThat(request.name()).isEqualTo("Cursos EE");
        assertThat(request.description()).isEqualTo("Ordenacao para EE");
    }
}
