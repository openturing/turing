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

package com.viglet.turing.persistence.model.se;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TurSEInstance.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSEInstanceTest {

    @Mock
    private TurSEVendor turSEVendor;

    private TurSEInstance turSEInstance;

    @BeforeEach
    void setUp() {
        turSEInstance = new TurSEInstance();
    }

    @Test
    void testGettersAndSetters() {
        String id = "test-id-123";
        String title = "Test SE Instance";
        String description = "Test Description";
        int enabled = 1;
        String endpointUrl = "http://localhost:8983/solr";

        turSEInstance.setId(id);
        turSEInstance.setTitle(title);
        turSEInstance.setDescription(description);
        turSEInstance.setEnabled(enabled);
        turSEInstance.setEndpointUrl(endpointUrl);
        turSEInstance.setTurSEVendor(turSEVendor);

        assertThat(turSEInstance.getId()).isEqualTo(id);
        assertThat(turSEInstance.getTitle()).isEqualTo(title);
        assertThat(turSEInstance.getDescription()).isEqualTo(description);
        assertThat(turSEInstance.getEnabled()).isEqualTo(enabled);
        assertThat(turSEInstance.getEndpointUrl()).isEqualTo(endpointUrl);
        assertThat(turSEInstance.getTurSEVendor()).isEqualTo(turSEVendor);
    }

    @Test
    void testDefaultValues() {
        assertThat(turSEInstance.getId()).isNull();
        assertThat(turSEInstance.getTitle()).isNull();
        assertThat(turSEInstance.getDescription()).isNull();
        assertThat(turSEInstance.getEnabled()).isZero();
        assertThat(turSEInstance.getEndpointUrl()).isNull();
        assertThat(turSEInstance.getTurSEVendor()).isNull();
    }

    @Test
    void testSetEnabledWithDifferentValues() {
        turSEInstance.setEnabled(0);
        assertThat(turSEInstance.getEnabled()).isZero();

        turSEInstance.setEnabled(1);
        assertThat(turSEInstance.getEnabled()).isEqualTo(1);
    }

    @Test
    void testSetEndpointUrlWithDifferentValues() {
        turSEInstance.setEndpointUrl("http://localhost:8983/solr");
        assertThat(turSEInstance.getEndpointUrl()).isEqualTo("http://localhost:8983/solr");

        turSEInstance.setEndpointUrl("http://192.168.1.1:9200");
        assertThat(turSEInstance.getEndpointUrl()).isEqualTo("http://192.168.1.1:9200");

        turSEInstance.setEndpointUrl("https://solr.example.com/solr");
        assertThat(turSEInstance.getEndpointUrl()).isEqualTo("https://solr.example.com/solr");
    }

    @Test
    void testSetNullValues() {
        turSEInstance.setId("test-id");
        turSEInstance.setTitle("title");
        turSEInstance.setDescription("description");
        turSEInstance.setEndpointUrl("http://localhost:8983/solr");
        turSEInstance.setTurSEVendor(turSEVendor);

        turSEInstance.setId(null);
        turSEInstance.setTitle(null);
        turSEInstance.setDescription(null);
        turSEInstance.setEndpointUrl(null);
        turSEInstance.setTurSEVendor(null);

        assertThat(turSEInstance.getId()).isNull();
        assertThat(turSEInstance.getTitle()).isNull();
        assertThat(turSEInstance.getDescription()).isNull();
        assertThat(turSEInstance.getEndpointUrl()).isNull();
        assertThat(turSEInstance.getTurSEVendor()).isNull();
    }

    @Test
    void testSerialVersionUID() {
        assertThat(TurSEInstance.class)
                .hasDeclaredFields("serialVersionUID");
    }
}
