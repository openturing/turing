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

package com.viglet.turing.api.sn.bean;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TurSolrCoreExists.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurSolrCoreExistsTest {

    @Test
    void testNoArgsConstructor() {
        TurSolrCoreExists coreExists = new TurSolrCoreExists();
        
        assertThat(coreExists).isNotNull();
        assertThat(coreExists.getName()).isNull();
        assertThat(coreExists.isExists()).isFalse();
    }

    @Test
    void testBuilder() {
        TurSolrCoreExists coreExists = TurSolrCoreExists.builder()
            .name("turing_core")
            .exists(true)
            .build();
        
        assertThat(coreExists.getName()).isEqualTo("turing_core");
        assertThat(coreExists.isExists()).isTrue();
    }

    @Test
    void testBuilderWithNonExistentCore() {
        TurSolrCoreExists coreExists = TurSolrCoreExists.builder()
            .name("missing_core")
            .exists(false)
            .build();
        
        assertThat(coreExists.getName()).isEqualTo("missing_core");
        assertThat(coreExists.isExists()).isFalse();
    }

    @Test
    void testGettersAndSetters() {
        TurSolrCoreExists coreExists = new TurSolrCoreExists();
        
        coreExists.setName("test_core");
        coreExists.setExists(true);
        
        assertThat(coreExists.getName()).isEqualTo("test_core");
        assertThat(coreExists.isExists()).isTrue();
    }

    @Test
    void testSettersWithDifferentValues() {
        TurSolrCoreExists coreExists = new TurSolrCoreExists();
        
        coreExists.setName("core1");
        assertThat(coreExists.getName()).isEqualTo("core1");
        
        coreExists.setName("core2");
        assertThat(coreExists.getName()).isEqualTo("core2");
        
        coreExists.setExists(true);
        assertThat(coreExists.isExists()).isTrue();
        
        coreExists.setExists(false);
        assertThat(coreExists.isExists()).isFalse();
    }

    @Test
    void testBuilderWithNullName() {
        TurSolrCoreExists coreExists = TurSolrCoreExists.builder()
            .name(null)
            .exists(false)
            .build();
        
        assertThat(coreExists.getName()).isNull();
        assertThat(coreExists.isExists()).isFalse();
    }

    @Test
    void testBuilderWithEmptyName() {
        TurSolrCoreExists coreExists = TurSolrCoreExists.builder()
            .name("")
            .exists(true)
            .build();
        
        assertThat(coreExists.getName()).isEmpty();
        assertThat(coreExists.isExists()).isTrue();
    }

    @Test
    void testMultipleCoreExistenceChecks() {
        TurSolrCoreExists existingCore = TurSolrCoreExists.builder()
            .name("production_core")
            .exists(true)
            .build();
        
        TurSolrCoreExists missingCore = TurSolrCoreExists.builder()
            .name("development_core")
            .exists(false)
            .build();
        
        assertThat(existingCore.getName()).isEqualTo("production_core");
        assertThat(existingCore.isExists()).isTrue();
        
        assertThat(missingCore.getName()).isEqualTo("development_core");
        assertThat(missingCore.isExists()).isFalse();
    }
}
