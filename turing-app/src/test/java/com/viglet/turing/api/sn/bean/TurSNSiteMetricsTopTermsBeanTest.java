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

import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TurSNSiteMetricsTopTermsBean.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurSNSiteMetricsTopTermsBeanTest {

    @Test
    void testNoArgsConstructor() {
        TurSNSiteMetricsTopTermsBean bean = new TurSNSiteMetricsTopTermsBean();
        
        assertThat(bean).isNotNull();
        assertThat(bean.getTopTerms()).isNotNull().isEmpty();
        assertThat(bean.getTotalTermsPeriod()).isZero();
        assertThat(bean.getTotalTermsPreviousPeriod()).isZero();
        assertThat(bean.getVariationPeriod()).isZero();
    }

    @Test
    void testAllArgsConstructorWithZeroPreviousPeriod() {
        List<TurSNSiteMetricAccessTerm> terms = new ArrayList<>();
        
        TurSNSiteMetricsTopTermsBean bean = new TurSNSiteMetricsTopTermsBean(terms, 100, 0);
        
        assertThat(bean.getTopTerms()).isEqualTo(terms);
        assertThat(bean.getTotalTermsPeriod()).isEqualTo(100);
        assertThat(bean.getTotalTermsPreviousPeriod()).isZero();
        assertThat(bean.getVariationPeriod()).isZero();
    }

    @Test
    void testAllArgsConstructorWithPositiveGrowth() {
        List<TurSNSiteMetricAccessTerm> terms = new ArrayList<>();
        
        TurSNSiteMetricsTopTermsBean bean = new TurSNSiteMetricsTopTermsBean(terms, 200, 100);
        
        assertThat(bean.getTopTerms()).isEqualTo(terms);
        assertThat(bean.getTotalTermsPeriod()).isEqualTo(200);
        assertThat(bean.getTotalTermsPreviousPeriod()).isEqualTo(100);
        assertThat(bean.getVariationPeriod()).isEqualTo(100);
    }

    @Test
    void testAllArgsConstructorWithNegativeGrowth() {
        List<TurSNSiteMetricAccessTerm> terms = new ArrayList<>();
        
        TurSNSiteMetricsTopTermsBean bean = new TurSNSiteMetricsTopTermsBean(terms, 50, 100);
        
        assertThat(bean.getTopTerms()).isEqualTo(terms);
        assertThat(bean.getTotalTermsPeriod()).isEqualTo(50);
        assertThat(bean.getTotalTermsPreviousPeriod()).isEqualTo(100);
        assertThat(bean.getVariationPeriod()).isEqualTo(-50);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("provideGrowthScenarios")
    void testAllArgsConstructorWithGrowthScenarios(int totalTermsPeriod, int totalTermsPreviousPeriod, int expectedVariation) {
        List<TurSNSiteMetricAccessTerm> terms = new ArrayList<>();
        TurSNSiteMetricsTopTermsBean bean = new TurSNSiteMetricsTopTermsBean(terms, totalTermsPeriod, totalTermsPreviousPeriod);

        assertThat(bean.getTotalTermsPeriod()).isEqualTo(totalTermsPeriod);
        assertThat(bean.getTotalTermsPreviousPeriod()).isEqualTo(totalTermsPreviousPeriod);
        assertThat(bean.getVariationPeriod()).isEqualTo(expectedVariation);
    }

    private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> provideGrowthScenarios() {
        return java.util.stream.Stream.of(
            org.junit.jupiter.params.provider.Arguments.of(300, 100, 200), // Doubling
            org.junit.jupiter.params.provider.Arguments.of(400, 100, 300), // Tripling
            org.junit.jupiter.params.provider.Arguments.of(125, 100, 25)   // 25% Growth
        );
    }

    @Test
    void testAllArgsConstructorWith50PercentDecline() {
        List<TurSNSiteMetricAccessTerm> terms = new ArrayList<>();
        
        TurSNSiteMetricsTopTermsBean bean = new TurSNSiteMetricsTopTermsBean(terms, 50, 100);
        
        assertThat(bean.getTotalTermsPeriod()).isEqualTo(50);
        assertThat(bean.getTotalTermsPreviousPeriod()).isEqualTo(100);
        assertThat(bean.getVariationPeriod()).isEqualTo(-50);
    }

    @Test
    void testAllArgsConstructorWith75PercentDecline() {
        List<TurSNSiteMetricAccessTerm> terms = new ArrayList<>();
        
        TurSNSiteMetricsTopTermsBean bean = new TurSNSiteMetricsTopTermsBean(terms, 25, 100);
        
        assertThat(bean.getTotalTermsPeriod()).isEqualTo(25);
        assertThat(bean.getTotalTermsPreviousPeriod()).isEqualTo(100);
        assertThat(bean.getVariationPeriod()).isEqualTo(-75);
    }

    @Test
    void testAllArgsConstructorWithNoChange() {
        List<TurSNSiteMetricAccessTerm> terms = new ArrayList<>();
        
        TurSNSiteMetricsTopTermsBean bean = new TurSNSiteMetricsTopTermsBean(terms, 100, 100);
        
        assertThat(bean.getTotalTermsPeriod()).isEqualTo(100);
        assertThat(bean.getTotalTermsPreviousPeriod()).isEqualTo(100);
        assertThat(bean.getVariationPeriod()).isZero();
    }

    @Test
    void testGettersAndSetters() {
        TurSNSiteMetricsTopTermsBean bean = new TurSNSiteMetricsTopTermsBean();
        List<TurSNSiteMetricAccessTerm> terms = new ArrayList<>();
        
        bean.setTopTerms(terms);
        bean.setTotalTermsPeriod(150);
        bean.setTotalTermsPreviousPeriod(100);
        bean.setVariationPeriod(50);
        
        assertThat(bean.getTopTerms()).isEqualTo(terms);
        assertThat(bean.getTotalTermsPeriod()).isEqualTo(150);
        assertThat(bean.getTotalTermsPreviousPeriod()).isEqualTo(100);
        assertThat(bean.getVariationPeriod()).isEqualTo(50);
    }

    @Test
    void testVariationCalculationWithLargeNumbers() {
        List<TurSNSiteMetricAccessTerm> terms = new ArrayList<>();
        
        TurSNSiteMetricsTopTermsBean bean = new TurSNSiteMetricsTopTermsBean(terms, 10000, 5000);
        
        assertThat(bean.getVariationPeriod()).isEqualTo(100);
    }

    @Test
    void testVariationCalculationWithSmallNumbers() {
        List<TurSNSiteMetricAccessTerm> terms = new ArrayList<>();
        
        TurSNSiteMetricsTopTermsBean bean = new TurSNSiteMetricsTopTermsBean(terms, 2, 1);
        
        assertThat(bean.getVariationPeriod()).isEqualTo(100);
    }

    @Test
    void testAllArgsConstructorWithNullTermsList() {
        TurSNSiteMetricsTopTermsBean bean = new TurSNSiteMetricsTopTermsBean(null, 100, 50);
        
        assertThat(bean.getTopTerms()).isNull();
        assertThat(bean.getTotalTermsPeriod()).isEqualTo(100);
        assertThat(bean.getTotalTermsPreviousPeriod()).isEqualTo(50);
        assertThat(bean.getVariationPeriod()).isEqualTo(100);
    }

    @Test
    void testVariationCalculationPrecision() {
        List<TurSNSiteMetricAccessTerm> terms = new ArrayList<>();
        
        TurSNSiteMetricsTopTermsBean bean1 = new TurSNSiteMetricsTopTermsBean(terms, 333, 100);
        assertThat(bean1.getVariationPeriod()).isEqualTo(233);
        
        TurSNSiteMetricsTopTermsBean bean2 = new TurSNSiteMetricsTopTermsBean(terms, 33, 100);
        assertThat(bean2.getVariationPeriod()).isEqualTo(-66);
    }
}
