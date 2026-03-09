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

package com.viglet.turing.persistence.model.sn.spotlight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TurSNSiteSpotlightTerm.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteSpotlightTermTest {

    @Mock
    private TurSNSiteSpotlight turSNSiteSpotlight;

    private TurSNSiteSpotlightTerm turSNSiteSpotlightTerm;

    @BeforeEach
    void setUp() {
        turSNSiteSpotlightTerm = new TurSNSiteSpotlightTerm();
    }

    @Test
    void testGettersAndSetters() {
        String id = "term-id-123";
        String name = "Search Term";

        turSNSiteSpotlightTerm.setId(id);
        turSNSiteSpotlightTerm.setName(name);
        turSNSiteSpotlightTerm.setTurSNSiteSpotlight(turSNSiteSpotlight);

        assertThat(turSNSiteSpotlightTerm.getId()).isEqualTo(id);
        assertThat(turSNSiteSpotlightTerm.getName()).isEqualTo(name);
        assertThat(turSNSiteSpotlightTerm.getTurSNSiteSpotlight()).isEqualTo(turSNSiteSpotlight);
    }

    @Test
    void testDefaultConstructor() {
        assertThat(turSNSiteSpotlightTerm).isNotNull();
        assertThat(turSNSiteSpotlightTerm.getId()).isNull();
        assertThat(turSNSiteSpotlightTerm.getName()).isNull();
        assertThat(turSNSiteSpotlightTerm.getTurSNSiteSpotlight()).isNull();
    }

    @Test
    void testIdField() {
        String testId = "test-term-uuid-123";
        turSNSiteSpotlightTerm.setId(testId);
        assertThat(turSNSiteSpotlightTerm.getId()).isEqualTo(testId);
    }

    @Test
    void testNameField() {
        String name1 = "term1";
        turSNSiteSpotlightTerm.setName(name1);
        assertThat(turSNSiteSpotlightTerm.getName()).isEqualTo(name1);

        String name2 = "term2";
        turSNSiteSpotlightTerm.setName(name2);
        assertThat(turSNSiteSpotlightTerm.getName()).isEqualTo(name2);
    }

    @Test
    void testSpotlightRelationship() {
        turSNSiteSpotlightTerm.setTurSNSiteSpotlight(turSNSiteSpotlight);
        assertThat(turSNSiteSpotlightTerm.getTurSNSiteSpotlight()).isEqualTo(turSNSiteSpotlight);
    }

    @Test
    void testNameWithSpaces() {
        String nameWithSpaces = "multi word term";
        turSNSiteSpotlightTerm.setName(nameWithSpaces);
        assertThat(turSNSiteSpotlightTerm.getName()).isEqualTo(nameWithSpaces);
    }

    @Test
    void testNameWithSpecialCharacters() {
        String nameWithSpecialChars = "term-with-special_chars@123";
        turSNSiteSpotlightTerm.setName(nameWithSpecialChars);
        assertThat(turSNSiteSpotlightTerm.getName()).isEqualTo(nameWithSpecialChars);
    }

    @Test
    void testLongName() {
        String longName = "a".repeat(255);
        turSNSiteSpotlightTerm.setName(longName);
        assertThat(turSNSiteSpotlightTerm.getName()).hasSize(255);
    }

    @Test
    void testEmptyName() {
        turSNSiteSpotlightTerm.setName("");
        assertThat(turSNSiteSpotlightTerm.getName()).isEmpty();
    }

    @Test
    void testNullName() {
        turSNSiteSpotlightTerm.setName(null);
        assertThat(turSNSiteSpotlightTerm.getName()).isNull();
    }

    @Test
    void testNameCasePreservation() {
        String mixedCaseName = "MixedCaseTerm";
        turSNSiteSpotlightTerm.setName(mixedCaseName);
        assertThat(turSNSiteSpotlightTerm.getName()).isEqualTo(mixedCaseName);
    }

    @Test
    void testCompleteTerm() {
        String id = "complete-term-id";
        String name = "Complete Term Name";

        turSNSiteSpotlightTerm.setId(id);
        turSNSiteSpotlightTerm.setName(name);
        turSNSiteSpotlightTerm.setTurSNSiteSpotlight(turSNSiteSpotlight);

        assertThat(turSNSiteSpotlightTerm.getId()).isEqualTo(id);
        assertThat(turSNSiteSpotlightTerm.getName()).isEqualTo(name);
        assertThat(turSNSiteSpotlightTerm.getTurSNSiteSpotlight()).isEqualTo(turSNSiteSpotlight);
    }

    @Test
    void testMultipleNameSettings() {
        turSNSiteSpotlightTerm.setName("first");
        assertThat(turSNSiteSpotlightTerm.getName()).isEqualTo("first");

        turSNSiteSpotlightTerm.setName("second");
        assertThat(turSNSiteSpotlightTerm.getName()).isEqualTo("second");

        turSNSiteSpotlightTerm.setName("third");
        assertThat(turSNSiteSpotlightTerm.getName()).isEqualTo("third");
    }

    @Test
    void testNameWithNumbers() {
        String nameWithNumbers = "term123";
        turSNSiteSpotlightTerm.setName(nameWithNumbers);
        assertThat(turSNSiteSpotlightTerm.getName()).isEqualTo(nameWithNumbers);
    }

    @Test
    void testNameWithUnicode() {
        String unicodeName = "термин";
        turSNSiteSpotlightTerm.setName(unicodeName);
        assertThat(turSNSiteSpotlightTerm.getName()).isEqualTo(unicodeName);
    }

    @Test
    void testNameWithWhitespace() {
        String nameWithWhitespace = "  term  ";
        turSNSiteSpotlightTerm.setName(nameWithWhitespace);
        assertThat(turSNSiteSpotlightTerm.getName()).isEqualTo(nameWithWhitespace);
    }

    @Test
    void testAllFieldsSet() {
        turSNSiteSpotlightTerm.setId("id-123");
        turSNSiteSpotlightTerm.setName("test term");
        turSNSiteSpotlightTerm.setTurSNSiteSpotlight(turSNSiteSpotlight);

        assertThat(turSNSiteSpotlightTerm.getId()).isNotNull();
        assertThat(turSNSiteSpotlightTerm.getName()).isNotNull();
        assertThat(turSNSiteSpotlightTerm.getTurSNSiteSpotlight()).isNotNull();
    }
}
