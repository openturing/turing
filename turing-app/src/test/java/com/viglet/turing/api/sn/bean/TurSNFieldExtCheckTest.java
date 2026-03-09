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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TurSNFieldExtCheck.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurSNFieldExtCheckTest {

    @Test
    void testNoArgsConstructor() {
        TurSNFieldExtCheck check = new TurSNFieldExtCheck();
        
        assertThat(check).isNotNull();
        assertThat(check.getCores()).isNull();
        assertThat(check.getFields()).isNull();
    }

    @Test
    void testBuilder() {
        List<TurSolrCoreExists> cores = new ArrayList<>();
        List<TurSolrFieldStatus> fields = new ArrayList<>();
        
        TurSNFieldExtCheck check = TurSNFieldExtCheck.builder()
            .cores(cores)
            .fields(fields)
            .build();
        
        assertThat(check).isNotNull();
        assertThat(check.getCores()).isEqualTo(cores);
        assertThat(check.getFields()).isEqualTo(fields);
    }

    @Test
    void testBuilderWithNull() {
        TurSNFieldExtCheck check = TurSNFieldExtCheck.builder()
            .cores(null)
            .fields(null)
            .build();
        
        assertThat(check).isNotNull();
        assertThat(check.getCores()).isNull();
        assertThat(check.getFields()).isNull();
    }

    @Test
    void testGettersAndSetters() {
        TurSNFieldExtCheck check = new TurSNFieldExtCheck();
        List<TurSolrCoreExists> cores = new ArrayList<>();
        List<TurSolrFieldStatus> fields = new ArrayList<>();
        
        check.setCores(cores);
        check.setFields(fields);
        
        assertThat(check.getCores()).isEqualTo(cores);
        assertThat(check.getFields()).isEqualTo(fields);
    }

    @Test
    void testSettersWithNullValues() {
        TurSNFieldExtCheck check = new TurSNFieldExtCheck();
        
        check.setCores(null);
        check.setFields(null);
        
        assertThat(check.getCores()).isNull();
        assertThat(check.getFields()).isNull();
    }

    @Test
    void testBuilderWithPopulatedCores() {
        List<TurSolrCoreExists> cores = new ArrayList<>();
        TurSolrCoreExists core1 = mock(TurSolrCoreExists.class);
        TurSolrCoreExists core2 = mock(TurSolrCoreExists.class);
        cores.add(core1);
        cores.add(core2);
        
        TurSNFieldExtCheck check = TurSNFieldExtCheck.builder()
            .cores(cores)
            .build();
        
        assertThat(check.getCores()).hasSize(2);
        assertThat(check.getCores()).contains(core1, core2);
    }

    @Test
    void testBuilderWithPopulatedFields() {
        List<TurSolrFieldStatus> fields = new ArrayList<>();
        TurSolrFieldStatus field1 = mock(TurSolrFieldStatus.class);
        TurSolrFieldStatus field2 = mock(TurSolrFieldStatus.class);
        TurSolrFieldStatus field3 = mock(TurSolrFieldStatus.class);
        fields.add(field1);
        fields.add(field2);
        fields.add(field3);
        
        TurSNFieldExtCheck check = TurSNFieldExtCheck.builder()
            .fields(fields)
            .build();
        
        assertThat(check.getFields()).hasSize(3);
        assertThat(check.getFields()).contains(field1, field2, field3);
    }

    @Test
    void testCompleteBuilderWithBothLists() {
        List<TurSolrCoreExists> cores = new ArrayList<>();
        TurSolrCoreExists core = mock(TurSolrCoreExists.class);
        cores.add(core);
        
        List<TurSolrFieldStatus> fields = new ArrayList<>();
        TurSolrFieldStatus field = mock(TurSolrFieldStatus.class);
        fields.add(field);
        
        TurSNFieldExtCheck check = TurSNFieldExtCheck.builder()
            .cores(cores)
            .fields(fields)
            .build();
        
        assertThat(check.getCores()).hasSize(1);
        assertThat(check.getFields()).hasSize(1);
        assertThat(check.getCores()).contains(core);
        assertThat(check.getFields()).contains(field);
    }

    @Test
    void testSettersUpdateValues() {
        TurSNFieldExtCheck check = new TurSNFieldExtCheck();
        
        List<TurSolrCoreExists> cores1 = new ArrayList<>();
        cores1.add(mock(TurSolrCoreExists.class));
        check.setCores(cores1);
        assertThat(check.getCores()).hasSize(1);
        
        List<TurSolrCoreExists> cores2 = new ArrayList<>();
        cores2.add(mock(TurSolrCoreExists.class));
        cores2.add(mock(TurSolrCoreExists.class));
        check.setCores(cores2);
        assertThat(check.getCores()).hasSize(2);
    }

    @Test
    void testBuilderWithEmptyLists() {
        List<TurSolrCoreExists> cores = new ArrayList<>();
        List<TurSolrFieldStatus> fields = new ArrayList<>();
        
        TurSNFieldExtCheck check = TurSNFieldExtCheck.builder()
            .cores(cores)
            .fields(fields)
            .build();
        
        assertThat(check.getCores()).isEmpty();
        assertThat(check.getFields()).isEmpty();
    }

    @Test
    void testSetCoresWithEmptyList() {
        TurSNFieldExtCheck check = new TurSNFieldExtCheck();
        
        check.setCores(new ArrayList<>());
        
        assertThat(check.getCores()).isNotNull().isEmpty();
    }

    @Test
    void testSetFieldsWithEmptyList() {
        TurSNFieldExtCheck check = new TurSNFieldExtCheck();
        
        check.setFields(new ArrayList<>());
        
        assertThat(check.getFields()).isNotNull().isEmpty();
    }

    @Test
    void testBuilderChaining() {
        TurSNFieldExtCheck check = TurSNFieldExtCheck.builder()
            .cores(new ArrayList<>())
            .fields(new ArrayList<>())
            .build();
        
        assertThat(check).isNotNull();
        assertThat(check.getCores()).isNotNull();
        assertThat(check.getFields()).isNotNull();
    }
}
