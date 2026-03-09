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

package com.viglet.turing.persistence.model.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TurStoreInstance.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurStoreInstanceTest {

    @Mock
    private TurStoreVendor turStoreVendor;

    private TurStoreInstance turStoreInstance;

    @BeforeEach
    void setUp() {
        turStoreInstance = new TurStoreInstance();
    }

    @Test
    void testGettersAndSetters() {
        String id = "store-id-789";
        String title = "Test Store Instance";
        String description = "Test Store Description";
        int enabled = 1;
        String url = "http://localhost:9000";
        
        turStoreInstance.setId(id);
        turStoreInstance.setTitle(title);
        turStoreInstance.setDescription(description);
        turStoreInstance.setEnabled(enabled);
        turStoreInstance.setUrl(url);
        turStoreInstance.setTurStoreVendor(turStoreVendor);
        
        assertThat(turStoreInstance.getId()).isEqualTo(id);
        assertThat(turStoreInstance.getTitle()).isEqualTo(title);
        assertThat(turStoreInstance.getDescription()).isEqualTo(description);
        assertThat(turStoreInstance.getEnabled()).isEqualTo(enabled);
        assertThat(turStoreInstance.getUrl()).isEqualTo(url);
        assertThat(turStoreInstance.getTurStoreVendor()).isEqualTo(turStoreVendor);
    }

    @Test
    void testDefaultValues() {
        assertThat(turStoreInstance.getId()).isNull();
        assertThat(turStoreInstance.getTitle()).isNull();
        assertThat(turStoreInstance.getDescription()).isNull();
        assertThat(turStoreInstance.getEnabled()).isZero();
        assertThat(turStoreInstance.getUrl()).isNull();
        assertThat(turStoreInstance.getTurStoreVendor()).isNull();
    }

    @Test
    void testSetEnabledWithDifferentValues() {
        turStoreInstance.setEnabled(0);
        assertThat(turStoreInstance.getEnabled()).isZero();
        
        turStoreInstance.setEnabled(1);
        assertThat(turStoreInstance.getEnabled()).isEqualTo(1);
    }

    @Test
    void testSetUrlWithDifferentValues() {
        turStoreInstance.setUrl("http://localhost:9000");
        assertThat(turStoreInstance.getUrl()).isEqualTo("http://localhost:9000");
        
        turStoreInstance.setUrl("https://store.example.com");
        assertThat(turStoreInstance.getUrl()).isEqualTo("https://store.example.com");
        
        turStoreInstance.setUrl("s3://bucket-name");
        assertThat(turStoreInstance.getUrl()).isEqualTo("s3://bucket-name");
    }

    @Test
    void testSetTitleWithLongValue() {
        String longTitle = "A".repeat(100);
        turStoreInstance.setTitle(longTitle);
        
        assertThat(turStoreInstance.getTitle()).isEqualTo(longTitle);
        assertThat(turStoreInstance.getTitle()).hasSize(100);
    }

    @Test
    void testSetDescriptionWithLongValue() {
        String longDescription = "B".repeat(100);
        turStoreInstance.setDescription(longDescription);
        
        assertThat(turStoreInstance.getDescription()).isEqualTo(longDescription);
        assertThat(turStoreInstance.getDescription()).hasSize(100);
    }

    @Test
    void testSetNullValues() {
        turStoreInstance.setId("test-id");
        turStoreInstance.setTitle("title");
        turStoreInstance.setDescription("description");
        turStoreInstance.setUrl("url");
        turStoreInstance.setTurStoreVendor(turStoreVendor);
        
        turStoreInstance.setId(null);
        turStoreInstance.setTitle(null);
        turStoreInstance.setDescription(null);
        turStoreInstance.setUrl(null);
        turStoreInstance.setTurStoreVendor(null);
        
        assertThat(turStoreInstance.getId()).isNull();
        assertThat(turStoreInstance.getTitle()).isNull();
        assertThat(turStoreInstance.getDescription()).isNull();
        assertThat(turStoreInstance.getUrl()).isNull();
        assertThat(turStoreInstance.getTurStoreVendor()).isNull();
    }

    @Test
    void testMultipleInstancesIndependence() {
        TurStoreInstance instance1 = new TurStoreInstance();
        TurStoreInstance instance2 = new TurStoreInstance();
        
        instance1.setId("id1");
        instance1.setTitle("Instance 1");
        instance1.setEnabled(1);
        
        instance2.setId("id2");
        instance2.setTitle("Instance 2");
        instance2.setEnabled(0);
        
        assertThat(instance1.getId()).isEqualTo("id1");
        assertThat(instance2.getId()).isEqualTo("id2");
        assertThat(instance1.getTitle()).isEqualTo("Instance 1");
        assertThat(instance2.getTitle()).isEqualTo("Instance 2");
        assertThat(instance1.getEnabled()).isEqualTo(1);
        assertThat(instance2.getEnabled()).isZero();
    }

    @Test
    void testSerialVersionUID() {
        assertThat(TurStoreInstance.class)
                .hasDeclaredFields("serialVersionUID");
    }
}
