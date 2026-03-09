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
 * Unit tests for TurSNFieldExtType.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurSNFieldExtTypeTest {

    @Test
    void testConstructor() {
        TurSNFieldExtType fieldExtType = new TurSNFieldExtType("field-1", "Text Field");
        
        assertThat(fieldExtType).isNotNull();
        assertThat(fieldExtType.getId()).isEqualTo("field-1");
        assertThat(fieldExtType.getName()).isEqualTo("Text Field");
    }

    @Test
    void testConstructorWithNullValues() {
        TurSNFieldExtType fieldExtType = new TurSNFieldExtType(null, null);
        
        assertThat(fieldExtType).isNotNull();
        assertThat(fieldExtType.getId()).isNull();
        assertThat(fieldExtType.getName()).isNull();
    }

    @Test
    void testConstructorWithEmptyStrings() {
        TurSNFieldExtType fieldExtType = new TurSNFieldExtType("", "");
        
        assertThat(fieldExtType.getId()).isEmpty();
        assertThat(fieldExtType.getName()).isEmpty();
    }

    @Test
    void testGettersAndSetters() {
        TurSNFieldExtType fieldExtType = new TurSNFieldExtType("initial-id", "Initial Name");
        
        assertThat(fieldExtType.getId()).isEqualTo("initial-id");
        assertThat(fieldExtType.getName()).isEqualTo("Initial Name");
        
        fieldExtType.setId("updated-id");
        fieldExtType.setName("Updated Name");
        
        assertThat(fieldExtType.getId()).isEqualTo("updated-id");
        assertThat(fieldExtType.getName()).isEqualTo("Updated Name");
    }

    @Test
    void testSetIdWithDifferentValues() {
        TurSNFieldExtType fieldExtType = new TurSNFieldExtType("id-1", "Name 1");
        
        fieldExtType.setId("id-2");
        assertThat(fieldExtType.getId()).isEqualTo("id-2");
        
        fieldExtType.setId("id-3");
        assertThat(fieldExtType.getId()).isEqualTo("id-3");
        
        fieldExtType.setId(null);
        assertThat(fieldExtType.getId()).isNull();
    }

    @Test
    void testSetNameWithDifferentValues() {
        TurSNFieldExtType fieldExtType = new TurSNFieldExtType("id-1", "Name 1");
        
        fieldExtType.setName("Name 2");
        assertThat(fieldExtType.getName()).isEqualTo("Name 2");
        
        fieldExtType.setName("Name 3");
        assertThat(fieldExtType.getName()).isEqualTo("Name 3");
        
        fieldExtType.setName(null);
        assertThat(fieldExtType.getName()).isNull();
    }

    @Test
    void testMultipleFieldExtTypes() {
        TurSNFieldExtType textField = new TurSNFieldExtType("text", "Text Field");
        TurSNFieldExtType numberField = new TurSNFieldExtType("number", "Number Field");
        TurSNFieldExtType dateField = new TurSNFieldExtType("date", "Date Field");
        
        assertThat(textField.getId()).isEqualTo("text");
        assertThat(textField.getName()).isEqualTo("Text Field");
        
        assertThat(numberField.getId()).isEqualTo("number");
        assertThat(numberField.getName()).isEqualTo("Number Field");
        
        assertThat(dateField.getId()).isEqualTo("date");
        assertThat(dateField.getName()).isEqualTo("Date Field");
    }

    @Test
    void testConstructorWithSpecialCharacters() {
        TurSNFieldExtType fieldExtType = new TurSNFieldExtType(
            "field-id_123",
            "Field Name with Special Ch@rs!"
        );
        
        assertThat(fieldExtType.getId()).isEqualTo("field-id_123");
        assertThat(fieldExtType.getName()).isEqualTo("Field Name with Special Ch@rs!");
    }

    @Test
    void testConstructorWithLongValues() {
        String longId = "a".repeat(100);
        String longName = "Very Long Field Name ".repeat(10);
        
        TurSNFieldExtType fieldExtType = new TurSNFieldExtType(longId, longName);
        
        assertThat(fieldExtType.getId()).hasSize(100);
        assertThat(fieldExtType.getName()).contains("Very Long Field Name");
    }
}
