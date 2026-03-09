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
 * Unit tests for TurSNFieldRepairPayload.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurSNFieldRepairPayloadTest {

    @Test
    void testNoArgsConstructor() {
        TurSNFieldRepairPayload payload = new TurSNFieldRepairPayload();
        
        assertThat(payload).isNotNull();
        assertThat(payload.getId()).isNull();
        assertThat(payload.getCore()).isNull();
        assertThat(payload.getRepairType()).isNull();
        assertThat(payload.getValue()).isNull();
    }

    @Test
    void testGettersAndSetters() {
        TurSNFieldRepairPayload payload = new TurSNFieldRepairPayload();
        
        payload.setId("field-123");
        payload.setCore("core-sample");
        payload.setRepairType(TurSNFieldRepairType.SE_CREATE_FIELD);
        payload.setValue("text");
        
        assertThat(payload.getId()).isEqualTo("field-123");
        assertThat(payload.getCore()).isEqualTo("core-sample");
        assertThat(payload.getRepairType()).isEqualTo(TurSNFieldRepairType.SE_CREATE_FIELD);
        assertThat(payload.getValue()).isEqualTo("text");
    }

    @Test
    void testSettersWithDifferentRepairTypes() {
        TurSNFieldRepairPayload payload = new TurSNFieldRepairPayload();
        
        payload.setRepairType(TurSNFieldRepairType.SE_CHANGE_TYPE);
        assertThat(payload.getRepairType()).isEqualTo(TurSNFieldRepairType.SE_CHANGE_TYPE);
        
        payload.setRepairType(TurSNFieldRepairType.SE_ENABLE_MULTI_VALUE);
        assertThat(payload.getRepairType()).isEqualTo(TurSNFieldRepairType.SE_ENABLE_MULTI_VALUE);
        
        payload.setRepairType(TurSNFieldRepairType.SN_CHANGE_TYPE);
        assertThat(payload.getRepairType()).isEqualTo(TurSNFieldRepairType.SN_CHANGE_TYPE);
    }

    @Test
    void testSettersWithNullValues() {
        TurSNFieldRepairPayload payload = new TurSNFieldRepairPayload();
        
        payload.setId(null);
        payload.setCore(null);
        payload.setRepairType(null);
        payload.setValue(null);
        
        assertThat(payload.getId()).isNull();
        assertThat(payload.getCore()).isNull();
        assertThat(payload.getRepairType()).isNull();
        assertThat(payload.getValue()).isNull();
    }

    @Test
    void testCompletePayloadForCreateField() {
        TurSNFieldRepairPayload payload = new TurSNFieldRepairPayload();
        
        payload.setId("title_field");
        payload.setCore("solr_core_en");
        payload.setRepairType(TurSNFieldRepairType.SE_CREATE_FIELD);
        payload.setValue("string");
        
        assertThat(payload.getId()).isEqualTo("title_field");
        assertThat(payload.getCore()).isEqualTo("solr_core_en");
        assertThat(payload.getRepairType()).isEqualTo(TurSNFieldRepairType.SE_CREATE_FIELD);
        assertThat(payload.getValue()).isEqualTo("string");
    }

    @Test
    void testCompletePayloadForChangeType() {
        TurSNFieldRepairPayload payload = new TurSNFieldRepairPayload();
        
        payload.setId("date_field");
        payload.setCore("solr_core_fr");
        payload.setRepairType(TurSNFieldRepairType.SE_CHANGE_TYPE);
        payload.setValue("date");
        
        assertThat(payload.getId()).isEqualTo("date_field");
        assertThat(payload.getCore()).isEqualTo("solr_core_fr");
        assertThat(payload.getRepairType()).isEqualTo(TurSNFieldRepairType.SE_CHANGE_TYPE);
        assertThat(payload.getValue()).isEqualTo("date");
    }

    @Test
    void testCompletePayloadForEnableMultiValue() {
        TurSNFieldRepairPayload payload = new TurSNFieldRepairPayload();
        
        payload.setId("tags_field");
        payload.setCore("solr_core_pt");
        payload.setRepairType(TurSNFieldRepairType.SE_ENABLE_MULTI_VALUE);
        payload.setValue("true");
        
        assertThat(payload.getId()).isEqualTo("tags_field");
        assertThat(payload.getCore()).isEqualTo("solr_core_pt");
        assertThat(payload.getRepairType()).isEqualTo(TurSNFieldRepairType.SE_ENABLE_MULTI_VALUE);
        assertThat(payload.getValue()).isEqualTo("true");
    }

    @Test
    void testCompletePayloadForSNChangeType() {
        TurSNFieldRepairPayload payload = new TurSNFieldRepairPayload();
        
        payload.setId("content_field");
        payload.setCore("solr_core_de");
        payload.setRepairType(TurSNFieldRepairType.SN_CHANGE_TYPE);
        payload.setValue("text_general");
        
        assertThat(payload.getId()).isEqualTo("content_field");
        assertThat(payload.getCore()).isEqualTo("solr_core_de");
        assertThat(payload.getRepairType()).isEqualTo(TurSNFieldRepairType.SN_CHANGE_TYPE);
        assertThat(payload.getValue()).isEqualTo("text_general");
    }

    @Test
    void testSetIdWithEmptyString() {
        TurSNFieldRepairPayload payload = new TurSNFieldRepairPayload();
        
        payload.setId("");
        
        assertThat(payload.getId()).isEmpty();
    }

    @Test
    void testSetValueWithEmptyString() {
        TurSNFieldRepairPayload payload = new TurSNFieldRepairPayload();
        
        payload.setValue("");
        
        assertThat(payload.getValue()).isEmpty();
    }

    @Test
    void testMultipleUpdates() {
        TurSNFieldRepairPayload payload = new TurSNFieldRepairPayload();
        
        payload.setId("field1");
        assertThat(payload.getId()).isEqualTo("field1");
        
        payload.setId("field2");
        assertThat(payload.getId()).isEqualTo("field2");
        
        payload.setCore("core1");
        assertThat(payload.getCore()).isEqualTo("core1");
        
        payload.setCore("core2");
        assertThat(payload.getCore()).isEqualTo("core2");
    }
}
