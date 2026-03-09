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

package com.viglet.turing.persistence.model.sn.metric;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.sn.TurSNSite;

/**
 * Unit tests for TurSNSiteMetricAccess.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteMetricAccessTest {

    @Mock
    private TurSNSite turSNSite;

    private TurSNSiteMetricAccess turSNSiteMetricAccess;

    @BeforeEach
    void setUp() {
        turSNSiteMetricAccess = new TurSNSiteMetricAccess();
    }

    @Test
    void testGettersAndSetters() {
        String id = "metric-id-123";
        String userId = "user-123";
        Instant accessDate = Instant.now();
        String term = "Search Term";
        Locale language = Locale.ENGLISH;
        long numFound = 42L;

        turSNSiteMetricAccess.setId(id);
        turSNSiteMetricAccess.setUserId(userId);
        turSNSiteMetricAccess.setAccessDate(accessDate);
        turSNSiteMetricAccess.setTerm(term);
        turSNSiteMetricAccess.setLanguage(language);
        turSNSiteMetricAccess.setTurSNSite(turSNSite);
        turSNSiteMetricAccess.setNumFound(numFound);

        assertThat(turSNSiteMetricAccess.getId()).isEqualTo(id);
        assertThat(turSNSiteMetricAccess.getUserId()).isEqualTo(userId);
        assertThat(turSNSiteMetricAccess.getAccessDate()).isEqualTo(accessDate);
        assertThat(turSNSiteMetricAccess.getTerm()).isEqualTo(term);
        assertThat(turSNSiteMetricAccess.getLanguage()).isEqualTo(language);
        assertThat(turSNSiteMetricAccess.getTurSNSite()).isEqualTo(turSNSite);
        assertThat(turSNSiteMetricAccess.getNumFound()).isEqualTo(numFound);
    }

    @Test
    void testDefaultConstructor() {
        assertThat(turSNSiteMetricAccess).isNotNull();
        assertThat(turSNSiteMetricAccess.getId()).isNull();
        assertThat(turSNSiteMetricAccess.getUserId()).isNull();
        assertThat(turSNSiteMetricAccess.getAccessDate()).isNull();
        assertThat(turSNSiteMetricAccess.getTerm()).isNull();
        assertThat(turSNSiteMetricAccess.getLanguage()).isNull();
        assertThat(turSNSiteMetricAccess.getTurSNSite()).isNull();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("termSanitizationProvider")
    void testTermSanitization(String input, String expectedSanitized, boolean shouldNotContainSpecialChar,
            String specialChar) {
        turSNSiteMetricAccess.setTerm(input);

        assertThat(turSNSiteMetricAccess.getTerm()).isEqualTo(input);
        if (expectedSanitized != null) {
            assertThat(turSNSiteMetricAccess.getSanatizedTerm()).isEqualTo(expectedSanitized);
        }
        if (shouldNotContainSpecialChar && specialChar != null) {
            assertThat(turSNSiteMetricAccess.getSanatizedTerm()).doesNotContain(specialChar);
        }
    }

    private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> termSanitizationProvider() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("Café Français", "cafe francais", false, null),
                org.junit.jupiter.params.provider.Arguments.of("search   with    multiple     spaces",
                        "search with multiple spaces", false, null),
                org.junit.jupiter.params.provider.Arguments.of("Søren Kierkegård", null, true, "ø"),
                org.junit.jupiter.params.provider.Arguments.of("UPPER CASE SEARCH", "upper case search", false, null),
                org.junit.jupiter.params.provider.Arguments.of("  search term  ", "search term", false, null));
    }

    @Test
    void testTargetingRulesInitialization() {
        assertThat(turSNSiteMetricAccess.getTargetingRules()).isNotNull();
    }

    @Test
    void testSetTargetingRules() {
        Set<String> rules = new HashSet<>();
        rules.add("rule1");
        rules.add("rule2");
        rules.add("rule3");

        turSNSiteMetricAccess.setTargetingRules(rules);

        assertThat(turSNSiteMetricAccess.getTargetingRules()).hasSize(3);
        assertThat(turSNSiteMetricAccess.getTargetingRules()).containsExactlyInAnyOrder("rule1", "rule2", "rule3");
    }

    @Test
    void testSetTargetingRulesEmpty() {
        Set<String> emptyRules = new HashSet<>();
        turSNSiteMetricAccess.setTargetingRules(emptyRules);

        assertThat(turSNSiteMetricAccess.getTargetingRules()).isEmpty();
    }

    @Test
    void testAccessDateField() {
        Instant now = Instant.now();
        turSNSiteMetricAccess.setAccessDate(now);

        assertThat(turSNSiteMetricAccess.getAccessDate()).isEqualTo(now);
    }

    @Test
    void testLanguageField() {
        turSNSiteMetricAccess.setLanguage(Locale.FRENCH);
        assertThat(turSNSiteMetricAccess.getLanguage()).isEqualTo(Locale.FRENCH);

        turSNSiteMetricAccess.setLanguage(Locale.GERMAN);
        assertThat(turSNSiteMetricAccess.getLanguage()).isEqualTo(Locale.GERMAN);
    }

    @Test
    void testNumFoundField() {
        turSNSiteMetricAccess.setNumFound(100L);
        assertThat(turSNSiteMetricAccess.getNumFound()).isEqualTo(100L);

        turSNSiteMetricAccess.setNumFound(0L);
        assertThat(turSNSiteMetricAccess.getNumFound()).isZero();

        turSNSiteMetricAccess.setNumFound(0L);

        turSNSiteMetricAccess.setNumFound(999999L);
        assertThat(turSNSiteMetricAccess.getNumFound()).isEqualTo(999999L);
    }

    @Test
    void testUserIdField() {
        String userId1 = "user-abc-123";
        turSNSiteMetricAccess.setUserId(userId1);
        assertThat(turSNSiteMetricAccess.getUserId()).isEqualTo(userId1);

        String userId2 = "user-xyz-789";
        turSNSiteMetricAccess.setUserId(userId2);
        assertThat(turSNSiteMetricAccess.getUserId()).isEqualTo(userId2);
    }

    @Test
    void testSiteRelationship() {
        turSNSiteMetricAccess.setTurSNSite(turSNSite);
        assertThat(turSNSiteMetricAccess.getTurSNSite()).isEqualTo(turSNSite);
    }

    @Test
    void testCompleteMetricAccess() {
        String id = "metric-complete-123";
        String userId = "user-complete";
        Instant accessDate = Instant.parse("2025-01-10T10:15:30.00Z");
        String term = "Complete Search Term";
        Locale language = Locale.US;
        long numFound = 50L;
        Set<String> rules = new HashSet<>();
        rules.add("rule-A");
        rules.add("rule-B");

        turSNSiteMetricAccess.setId(id);
        turSNSiteMetricAccess.setUserId(userId);
        turSNSiteMetricAccess.setAccessDate(accessDate);
        turSNSiteMetricAccess.setTerm(term);
        turSNSiteMetricAccess.setLanguage(language);
        turSNSiteMetricAccess.setTurSNSite(turSNSite);
        turSNSiteMetricAccess.setNumFound(numFound);
        turSNSiteMetricAccess.setTargetingRules(rules);

        assertThat(turSNSiteMetricAccess.getId()).isEqualTo(id);
        assertThat(turSNSiteMetricAccess.getUserId()).isEqualTo(userId);
        assertThat(turSNSiteMetricAccess.getAccessDate()).isEqualTo(accessDate);
        assertThat(turSNSiteMetricAccess.getTerm()).isEqualTo(term);
        assertThat(turSNSiteMetricAccess.getSanatizedTerm()).isEqualTo("complete search term");
        assertThat(turSNSiteMetricAccess.getLanguage()).isEqualTo(language);
        assertThat(turSNSiteMetricAccess.getTurSNSite()).isEqualTo(turSNSite);
        assertThat(turSNSiteMetricAccess.getNumFound()).isEqualTo(numFound);
        assertThat(turSNSiteMetricAccess.getTargetingRules()).hasSize(2);
    }

    @Test
    void testSetTermMultipleTimes() {
        turSNSiteMetricAccess.setTerm("First Term");
        assertThat(turSNSiteMetricAccess.getTerm()).isEqualTo("First Term");
        assertThat(turSNSiteMetricAccess.getSanatizedTerm()).isEqualTo("first term");

        turSNSiteMetricAccess.setTerm("Second Term");
        assertThat(turSNSiteMetricAccess.getTerm()).isEqualTo("Second Term");
        assertThat(turSNSiteMetricAccess.getSanatizedTerm()).isEqualTo("second term");
    }

    @Test
    void testSanitizedTermWithNumbers() {
        String term = "Product 123 Model 456";
        turSNSiteMetricAccess.setTerm(term);

        assertThat(turSNSiteMetricAccess.getTerm()).isEqualTo(term);
        assertThat(turSNSiteMetricAccess.getSanatizedTerm()).isEqualTo("product 123 model 456");
    }

    @Test
    void testEmptyTargetingRules() {
        Set<String> rules = new HashSet<>();
        turSNSiteMetricAccess.setTargetingRules(rules);

        assertThat(turSNSiteMetricAccess.getTargetingRules()).isNotNull().isEmpty();
    }
}
