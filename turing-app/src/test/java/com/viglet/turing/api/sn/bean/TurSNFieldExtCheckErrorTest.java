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
 * Unit tests for TurSNFieldExtCheckError.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurSNFieldExtCheckErrorTest {

    @Test
    void testNoArgsConstructor() {
        TurSNFieldExtCheckError error = new TurSNFieldExtCheckError();
        
        assertThat(error).isNotNull();
        assertThat(error.getCoreName()).isNull();
        assertThat(error.getFieldName()).isNull();
        assertThat(error.isType()).isFalse();
        assertThat(error.isMultivalued()).isFalse();
    }

    @Test
    void testGettersAndSetters() {
        TurSNFieldExtCheckError error = new TurSNFieldExtCheckError();
        
        error.setCoreName("core-sample");
        error.setFieldName("title");
        error.setType(true);
        error.setMultivalued(false);
        
        assertThat(error.getCoreName()).isEqualTo("core-sample");
        assertThat(error.getFieldName()).isEqualTo("title");
        assertThat(error.isType()).isTrue();
        assertThat(error.isMultivalued()).isFalse();
    }

    @Test
    void testSettersWithDifferentBooleanCombinations() {
        TurSNFieldExtCheckError error1 = new TurSNFieldExtCheckError();
        error1.setType(true);
        error1.setMultivalued(true);
        assertThat(error1.isType()).isTrue();
        assertThat(error1.isMultivalued()).isTrue();
        
        TurSNFieldExtCheckError error2 = new TurSNFieldExtCheckError();
        error2.setType(false);
        error2.setMultivalued(true);
        assertThat(error2.isType()).isFalse();
        assertThat(error2.isMultivalued()).isTrue();
        
        TurSNFieldExtCheckError error3 = new TurSNFieldExtCheckError();
        error3.setType(true);
        error3.setMultivalued(false);
        assertThat(error3.isType()).isTrue();
        assertThat(error3.isMultivalued()).isFalse();
        
        TurSNFieldExtCheckError error4 = new TurSNFieldExtCheckError();
        error4.setType(false);
        error4.setMultivalued(false);
        assertThat(error4.isType()).isFalse();
        assertThat(error4.isMultivalued()).isFalse();
    }

    @Test
    void testSettersWithNullValues() {
        TurSNFieldExtCheckError error = new TurSNFieldExtCheckError();
        
        error.setCoreName(null);
        error.setFieldName(null);
        
        assertThat(error.getCoreName()).isNull();
        assertThat(error.getFieldName()).isNull();
    }

    @Test
    void testTypeError() {
        TurSNFieldExtCheckError error = new TurSNFieldExtCheckError();
        
        error.setCoreName("solr_core_en");
        error.setFieldName("date_field");
        error.setType(true);
        error.setMultivalued(false);
        
        assertThat(error.getCoreName()).isEqualTo("solr_core_en");
        assertThat(error.getFieldName()).isEqualTo("date_field");
        assertThat(error.isType()).isTrue();
        assertThat(error.isMultivalued()).isFalse();
    }

    @Test
    void testMultivaluedError() {
        TurSNFieldExtCheckError error = new TurSNFieldExtCheckError();
        
        error.setCoreName("solr_core_fr");
        error.setFieldName("tags");
        error.setType(false);
        error.setMultivalued(true);
        
        assertThat(error.getCoreName()).isEqualTo("solr_core_fr");
        assertThat(error.getFieldName()).isEqualTo("tags");
        assertThat(error.isType()).isFalse();
        assertThat(error.isMultivalued()).isTrue();
    }

    @Test
    void testBothErrorTypes() {
        TurSNFieldExtCheckError error = new TurSNFieldExtCheckError();
        
        error.setCoreName("solr_core_pt");
        error.setFieldName("content");
        error.setType(true);
        error.setMultivalued(true);
        
        assertThat(error.getCoreName()).isEqualTo("solr_core_pt");
        assertThat(error.getFieldName()).isEqualTo("content");
        assertThat(error.isType()).isTrue();
        assertThat(error.isMultivalued()).isTrue();
    }

    @Test
    void testNoErrors() {
        TurSNFieldExtCheckError error = new TurSNFieldExtCheckError();
        
        error.setCoreName("solr_core_de");
        error.setFieldName("id");
        error.setType(false);
        error.setMultivalued(false);
        
        assertThat(error.getCoreName()).isEqualTo("solr_core_de");
        assertThat(error.getFieldName()).isEqualTo("id");
        assertThat(error.isType()).isFalse();
        assertThat(error.isMultivalued()).isFalse();
    }

    @Test
    void testSetCoreNameWithEmptyString() {
        TurSNFieldExtCheckError error = new TurSNFieldExtCheckError();
        
        error.setCoreName("");
        
        assertThat(error.getCoreName()).isEmpty();
    }

    @Test
    void testSetFieldNameWithEmptyString() {
        TurSNFieldExtCheckError error = new TurSNFieldExtCheckError();
        
        error.setFieldName("");
        
        assertThat(error.getFieldName()).isEmpty();
    }

    @Test
    void testMultipleUpdates() {
        TurSNFieldExtCheckError error = new TurSNFieldExtCheckError();
        
        error.setCoreName("core1");
        assertThat(error.getCoreName()).isEqualTo("core1");
        
        error.setCoreName("core2");
        assertThat(error.getCoreName()).isEqualTo("core2");
        
        error.setFieldName("field1");
        assertThat(error.getFieldName()).isEqualTo("field1");
        
        error.setFieldName("field2");
        assertThat(error.getFieldName()).isEqualTo("field2");
        
        error.setType(true);
        assertThat(error.isType()).isTrue();
        
        error.setType(false);
        assertThat(error.isType()).isFalse();
    }

    @Test
    void testCompleteErrorScenario() {
        TurSNFieldExtCheckError error = new TurSNFieldExtCheckError();
        
        error.setCoreName("production_core");
        error.setFieldName("published_date");
        error.setType(true);
        error.setMultivalued(false);
        
        assertThat(error.getCoreName()).isEqualTo("production_core");
        assertThat(error.getFieldName()).isEqualTo("published_date");
        assertThat(error.isType()).isTrue();
        assertThat(error.isMultivalued()).isFalse();
    }

    @Test
    void testBooleanAccessorMethods() {
        TurSNFieldExtCheckError error = new TurSNFieldExtCheckError();
        
        assertThat(error.isType()).isFalse();
        assertThat(error.isMultivalued()).isFalse();
        
        error.setType(true);
        error.setMultivalued(true);
        
        assertThat(error.isType()).isTrue();
        assertThat(error.isMultivalued()).isTrue();
    }
}
