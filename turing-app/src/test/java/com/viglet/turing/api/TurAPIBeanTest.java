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

package com.viglet.turing.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TurAPIBean.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurAPIBeanTest {

    @Test
    void testGettersAndSetters() {
        TurAPIBean bean = new TurAPIBean();
        
        bean.setProduct("Turing");
        bean.setMultiTenant(true);
        bean.setKeycloak(false);
        
        assertThat(bean.getProduct()).isEqualTo("Turing");
        assertThat(bean.isMultiTenant()).isTrue();
        assertThat(bean.isKeycloak()).isFalse();
    }

    @Test
    void testDefaultValues() {
        TurAPIBean bean = new TurAPIBean();
        
        assertThat(bean.getProduct()).isNull();
        assertThat(bean.isMultiTenant()).isFalse();
        assertThat(bean.isKeycloak()).isFalse();
    }

    @Test
    void testProductField() {
        TurAPIBean bean = new TurAPIBean();
        
        String productName = "Turing AI Search";
        bean.setProduct(productName);
        
        assertThat(bean.getProduct()).isEqualTo(productName);
    }

    @Test
    void testMultiTenantField() {
        TurAPIBean bean = new TurAPIBean();
        
        bean.setMultiTenant(true);
        assertThat(bean.isMultiTenant()).isTrue();
        
        bean.setMultiTenant(false);
        assertThat(bean.isMultiTenant()).isFalse();
    }

    @Test
    void testKeycloakField() {
        TurAPIBean bean = new TurAPIBean();
        
        bean.setKeycloak(true);
        assertThat(bean.isKeycloak()).isTrue();
        
        bean.setKeycloak(false);
        assertThat(bean.isKeycloak()).isFalse();
    }
}
