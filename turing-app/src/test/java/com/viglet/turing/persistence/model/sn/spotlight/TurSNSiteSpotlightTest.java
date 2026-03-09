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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
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
 * Unit tests for TurSNSiteSpotlight.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteSpotlightTest {

    @Mock
    private TurSNSite turSNSite;

    private TurSNSiteSpotlight turSNSiteSpotlight;

    @BeforeEach
    void setUp() {
        turSNSiteSpotlight = new TurSNSiteSpotlight();
    }

    @Test
    void testGettersAndSetters() {
        String id = "spotlight-id-123";
        String name = "Test Spotlight";
        String description = "Test spotlight description";
        LocalDateTime modificationDate = LocalDateTime.now();
        int managed = 1;
        String unmanagedId = "unmanaged-123";
        String provider = "CUSTOM_PROVIDER";
        Locale language = Locale.ENGLISH;

        turSNSiteSpotlight.setId(id);
        turSNSiteSpotlight.setName(name);
        turSNSiteSpotlight.setDescription(description);
        turSNSiteSpotlight.setModificationDate(modificationDate);
        turSNSiteSpotlight.setManaged(managed);
        turSNSiteSpotlight.setUnmanagedId(unmanagedId);
        turSNSiteSpotlight.setProvider(provider);
        turSNSiteSpotlight.setLanguage(language);
        turSNSiteSpotlight.setTurSNSite(turSNSite);

        assertThat(turSNSiteSpotlight.getId()).isEqualTo(id);
        assertThat(turSNSiteSpotlight.getName()).isEqualTo(name);
        assertThat(turSNSiteSpotlight.getDescription()).isEqualTo(description);
        assertThat(turSNSiteSpotlight.getModificationDate()).isEqualTo(modificationDate);
        assertThat(turSNSiteSpotlight.getManaged()).isEqualTo(managed);
        assertThat(turSNSiteSpotlight.getUnmanagedId()).isEqualTo(unmanagedId);
        assertThat(turSNSiteSpotlight.getProvider()).isEqualTo(provider);
        assertThat(turSNSiteSpotlight.getLanguage()).isEqualTo(language);
        assertThat(turSNSiteSpotlight.getTurSNSite()).isEqualTo(turSNSite);
    }

    @Test
    void testDefaultConstructor() {
        assertThat(turSNSiteSpotlight).isNotNull();
        assertThat(turSNSiteSpotlight.getId()).isNull();
        assertThat(turSNSiteSpotlight.getName()).isNull();
        assertThat(turSNSiteSpotlight.getDescription()).isNull();
        assertThat(turSNSiteSpotlight.getModificationDate()).isNull();
        assertThat(turSNSiteSpotlight.getManaged()).isEqualTo(1);
        assertThat(turSNSiteSpotlight.getUnmanagedId()).isNull();
        assertThat(turSNSiteSpotlight.getProvider()).isEqualTo("TURING");
        assertThat(turSNSiteSpotlight.getLanguage()).isNull();
        assertThat(turSNSiteSpotlight.getTurSNSite()).isNull();
    }

    @Test
    void testDefaultManagedValue() {
        assertThat(turSNSiteSpotlight.getManaged()).isEqualTo(1);
    }

    @Test
    void testDefaultProviderValue() {
        assertThat(turSNSiteSpotlight.getProvider()).isEqualTo("TURING");
    }

    @Test
    void testSpotlightTermsInitialization() {
        assertThat(turSNSiteSpotlight.getTurSNSiteSpotlightTerms()).isNotNull().isEmpty();
    }

    @Test
    void testSpotlightDocumentsInitialization() {
        assertThat(turSNSiteSpotlight.getTurSNSiteSpotlightDocuments()).isNotNull().isEmpty();
    }

    @Test
    void testSetSpotlightTermsWithNull() {
        turSNSiteSpotlight.setTurSNSiteSpotlightTerms(null);
        assertThat(turSNSiteSpotlight.getTurSNSiteSpotlightTerms()).isEmpty();
    }

    @Test
    void testSetSpotlightTermsWithValues() {
        TurSNSiteSpotlightTerm term1 = new TurSNSiteSpotlightTerm();
        TurSNSiteSpotlightTerm term2 = new TurSNSiteSpotlightTerm();
        Set<TurSNSiteSpotlightTerm> terms = new HashSet<>();
        terms.add(term1);
        terms.add(term2);

        turSNSiteSpotlight.setTurSNSiteSpotlightTerms(terms);
        assertThat(turSNSiteSpotlight.getTurSNSiteSpotlightTerms()).hasSize(2);
    }

    @Test
    void testSetSpotlightTermsClearsExisting() {
        TurSNSiteSpotlightTerm term1 = new TurSNSiteSpotlightTerm();
        Set<TurSNSiteSpotlightTerm> terms1 = new HashSet<>();
        terms1.add(term1);
        turSNSiteSpotlight.setTurSNSiteSpotlightTerms(terms1);

        TurSNSiteSpotlightTerm term2 = new TurSNSiteSpotlightTerm();
        Set<TurSNSiteSpotlightTerm> terms2 = new HashSet<>();
        terms2.add(term2);
        turSNSiteSpotlight.setTurSNSiteSpotlightTerms(terms2);

        assertThat(turSNSiteSpotlight.getTurSNSiteSpotlightTerms()).hasSize(1).contains(term2);
    }

    @Test
    void testSetSpotlightDocumentsWithNull() {
        turSNSiteSpotlight.setTurSNSiteSpotlightDocuments(null);
        assertThat(turSNSiteSpotlight.getTurSNSiteSpotlightDocuments()).isEmpty();
    }

    @Test
    void testSetSpotlightDocumentsWithValues() {
        TurSNSiteSpotlightDocument doc1 = new TurSNSiteSpotlightDocument();
        TurSNSiteSpotlightDocument doc2 = new TurSNSiteSpotlightDocument();
        Set<TurSNSiteSpotlightDocument> docs = new HashSet<>();
        docs.add(doc1);
        docs.add(doc2);

        turSNSiteSpotlight.setTurSNSiteSpotlightDocuments(docs);
        assertThat(turSNSiteSpotlight.getTurSNSiteSpotlightDocuments()).hasSize(2);
    }

    @Test
    void testSetSpotlightDocumentsClearsExisting() {
        TurSNSiteSpotlightDocument doc1 = new TurSNSiteSpotlightDocument();
        Set<TurSNSiteSpotlightDocument> docs1 = new HashSet<>();
        docs1.add(doc1);
        turSNSiteSpotlight.setTurSNSiteSpotlightDocuments(docs1);

        TurSNSiteSpotlightDocument doc2 = new TurSNSiteSpotlightDocument();
        Set<TurSNSiteSpotlightDocument> docs2 = new HashSet<>();
        docs2.add(doc2);
        turSNSiteSpotlight.setTurSNSiteSpotlightDocuments(docs2);

        assertThat(turSNSiteSpotlight.getTurSNSiteSpotlightDocuments()).hasSize(1).contains(doc2);
    }

    @Test
    void testManagedField() {
        turSNSiteSpotlight.setManaged(0);
        assertThat(turSNSiteSpotlight.getManaged()).isZero();

        turSNSiteSpotlight.setManaged(1);
        assertThat(turSNSiteSpotlight.getManaged()).isEqualTo(1);
    }

    @Test
    void testUnmanagedIdField() {
        String unmanagedId = "external-spotlight-123";
        turSNSiteSpotlight.setUnmanagedId(unmanagedId);
        assertThat(turSNSiteSpotlight.getUnmanagedId()).isEqualTo(unmanagedId);
    }

    @Test
    void testProviderField() {
        turSNSiteSpotlight.setProvider("GOOGLE");
        assertThat(turSNSiteSpotlight.getProvider()).isEqualTo("GOOGLE");

        turSNSiteSpotlight.setProvider("AZURE");
        assertThat(turSNSiteSpotlight.getProvider()).isEqualTo("AZURE");
    }

    @Test
    void testLanguageField() {
        turSNSiteSpotlight.setLanguage(Locale.FRENCH);
        assertThat(turSNSiteSpotlight.getLanguage()).isEqualTo(Locale.FRENCH);

        turSNSiteSpotlight.setLanguage(Locale.GERMAN);
        assertThat(turSNSiteSpotlight.getLanguage()).isEqualTo(Locale.GERMAN);
    }

    @Test
    void testModificationDateField() {
        LocalDateTime date1 = LocalDateTime.of(2025, 1, 10, 10, 30);
        turSNSiteSpotlight.setModificationDate(date1);
        assertThat(turSNSiteSpotlight.getModificationDate()).isEqualTo(date1);

        LocalDateTime date2 = LocalDateTime.of(2025, 2, 15, 14, 45);
        turSNSiteSpotlight.setModificationDate(date2);
        assertThat(turSNSiteSpotlight.getModificationDate()).isEqualTo(date2);
    }

    @Test
    void testSiteRelationship() {
        turSNSiteSpotlight.setTurSNSite(turSNSite);
        assertThat(turSNSiteSpotlight.getTurSNSite()).isEqualTo(turSNSite);
    }

    @Test
    void testCompleteSpotlight() {
        String id = "complete-spotlight";
        String name = "Complete Spotlight";
        String description = "Complete description";
        LocalDateTime modDate = LocalDateTime.of(2025, 1, 10, 12, 0);
        int managed = 0;
        String unmanagedId = "ext-id-123";
        String provider = "EXTERNAL";
        Locale language = Locale.JAPANESE;

        turSNSiteSpotlight.setId(id);
        turSNSiteSpotlight.setName(name);
        turSNSiteSpotlight.setDescription(description);
        turSNSiteSpotlight.setModificationDate(modDate);
        turSNSiteSpotlight.setManaged(managed);
        turSNSiteSpotlight.setUnmanagedId(unmanagedId);
        turSNSiteSpotlight.setProvider(provider);
        turSNSiteSpotlight.setLanguage(language);
        turSNSiteSpotlight.setTurSNSite(turSNSite);

        assertThat(turSNSiteSpotlight.getId()).isEqualTo(id);
        assertThat(turSNSiteSpotlight.getName()).isEqualTo(name);
        assertThat(turSNSiteSpotlight.getDescription()).isEqualTo(description);
        assertThat(turSNSiteSpotlight.getModificationDate()).isEqualTo(modDate);
        assertThat(turSNSiteSpotlight.getManaged()).isEqualTo(managed);
        assertThat(turSNSiteSpotlight.getUnmanagedId()).isEqualTo(unmanagedId);
        assertThat(turSNSiteSpotlight.getProvider()).isEqualTo(provider);
        assertThat(turSNSiteSpotlight.getLanguage()).isEqualTo(language);
        assertThat(turSNSiteSpotlight.getTurSNSite()).isEqualTo(turSNSite);
    }

    @Test
    void testNullableFields() {
        turSNSiteSpotlight.setDescription(null);
        turSNSiteSpotlight.setModificationDate(null);
        turSNSiteSpotlight.setUnmanagedId(null);
        turSNSiteSpotlight.setLanguage(null);

        assertThat(turSNSiteSpotlight.getDescription()).isNull();
        assertThat(turSNSiteSpotlight.getModificationDate()).isNull();
        assertThat(turSNSiteSpotlight.getUnmanagedId()).isNull();
        assertThat(turSNSiteSpotlight.getLanguage()).isNull();
    }
}
