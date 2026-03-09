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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TurSNSiteFilterQueryBean.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurSNSiteFilterQueryBeanTest {

    @Test
    void testNoArgsConstructor() {
        TurSNSiteFilterQueryBean bean = new TurSNSiteFilterQueryBean();
        
        assertThat(bean).isNotNull();
        assertThat(bean.getFacetsInFilterQueries()).isNull();
        assertThat(bean.getItems()).isNull();
    }

    @Test
    void testGettersAndSetters() {
        TurSNSiteFilterQueryBean bean = new TurSNSiteFilterQueryBean();
        
        List<String> facets = Arrays.asList("category", "author", "date");
        List<String> items = Arrays.asList("item1", "item2", "item3");
        
        bean.setFacetsInFilterQueries(facets);
        bean.setItems(items);
        
        assertThat(bean.getFacetsInFilterQueries()).containsExactly("category", "author", "date");
        assertThat(bean.getItems()).containsExactly("item1", "item2", "item3");
    }

    @Test
    void testChainedSetters() {
        List<String> facets = Arrays.asList("type", "status");
        List<String> items = Arrays.asList("doc1", "doc2");
        
        TurSNSiteFilterQueryBean bean = new TurSNSiteFilterQueryBean()
            .setFacetsInFilterQueries(facets)
            .setItems(items);
        
        assertThat(bean.getFacetsInFilterQueries()).containsExactly("type", "status");
        assertThat(bean.getItems()).containsExactly("doc1", "doc2");
    }

    @Test
    void testChainedSettersReturnsSameInstance() {
        TurSNSiteFilterQueryBean bean = new TurSNSiteFilterQueryBean();
        
        TurSNSiteFilterQueryBean result = bean
            .setFacetsInFilterQueries(Arrays.asList("facet1"))
            .setItems(Arrays.asList("item1"));
        
        assertThat(result).isSameAs(bean);
    }

    @Test
    void testWithEmptyLists() {
        TurSNSiteFilterQueryBean bean = new TurSNSiteFilterQueryBean();
        
        bean.setFacetsInFilterQueries(Collections.emptyList());
        bean.setItems(Collections.emptyList());
        
        assertThat(bean.getFacetsInFilterQueries()).isEmpty();
        assertThat(bean.getItems()).isEmpty();
    }

    @Test
    void testWithSingleElementLists() {
        TurSNSiteFilterQueryBean bean = new TurSNSiteFilterQueryBean();
        
        bean.setFacetsInFilterQueries(Collections.singletonList("single_facet"));
        bean.setItems(Collections.singletonList("single_item"));
        
        assertThat(bean.getFacetsInFilterQueries()).containsExactly("single_facet");
        assertThat(bean.getItems()).containsExactly("single_item");
    }

    @Test
    void testWithNullValues() {
        TurSNSiteFilterQueryBean bean = new TurSNSiteFilterQueryBean();
        
        bean.setFacetsInFilterQueries(null);
        bean.setItems(null);
        
        assertThat(bean.getFacetsInFilterQueries()).isNull();
        assertThat(bean.getItems()).isNull();
    }

    @Test
    void testMultipleUpdates() {
        TurSNSiteFilterQueryBean bean = new TurSNSiteFilterQueryBean();
        
        bean.setFacetsInFilterQueries(Arrays.asList("facet1", "facet2"));
        assertThat(bean.getFacetsInFilterQueries()).hasSize(2);
        
        bean.setFacetsInFilterQueries(Arrays.asList("facet3", "facet4", "facet5"));
        assertThat(bean.getFacetsInFilterQueries()).hasSize(3);
        
        bean.setItems(Arrays.asList("item1"));
        assertThat(bean.getItems()).hasSize(1);
        
        bean.setItems(Arrays.asList("item2", "item3"));
        assertThat(bean.getItems()).hasSize(2);
    }

    @Test
    void testChainedSettersWithComplexData() {
        TurSNSiteFilterQueryBean bean = new TurSNSiteFilterQueryBean()
            .setFacetsInFilterQueries(Arrays.asList(
                "category:technology",
                "status:published",
                "author:john_doe"
            ))
            .setItems(Arrays.asList(
                "doc-001",
                "doc-002",
                "doc-003"
            ));
        
        assertThat(bean.getFacetsInFilterQueries()).containsExactly(
            "category:technology",
            "status:published",
            "author:john_doe"
        );
        assertThat(bean.getItems()).containsExactly("doc-001", "doc-002", "doc-003");
    }

    @Test
    void testIndependentFieldUpdates() {
        TurSNSiteFilterQueryBean bean = new TurSNSiteFilterQueryBean();
        
        bean.setFacetsInFilterQueries(Arrays.asList("facet1"));
        assertThat(bean.getFacetsInFilterQueries()).isNotNull();
        assertThat(bean.getItems()).isNull();
        
        bean.setItems(Arrays.asList("item1"));
        assertThat(bean.getFacetsInFilterQueries()).isNotNull();
        assertThat(bean.getItems()).isNotNull();
    }
}
