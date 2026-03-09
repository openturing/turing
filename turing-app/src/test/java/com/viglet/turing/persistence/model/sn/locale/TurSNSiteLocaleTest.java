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

package com.viglet.turing.persistence.model.sn.locale;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.sn.TurSNSite;

/**
 * Unit tests for TurSNSiteLocale.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteLocaleTest {

    @Mock
    private TurSNSite turSNSite;

    private TurSNSiteLocale turSNSiteLocale;

    @BeforeEach
    void setUp() {
        turSNSiteLocale = new TurSNSiteLocale();
    }

    @Test
    void testGettersAndSetters() {
        String id = "locale-id-123";
        Locale language = Locale.ENGLISH;
        String core = "en_core";

        turSNSiteLocale.setId(id);
        turSNSiteLocale.setLanguage(language);
        turSNSiteLocale.setCore(core);
        turSNSiteLocale.setTurSNSite(turSNSite);

        assertThat(turSNSiteLocale.getId()).isEqualTo(id);
        assertThat(turSNSiteLocale.getLanguage()).isEqualTo(language);
        assertThat(turSNSiteLocale.getCore()).isEqualTo(core);
        assertThat(turSNSiteLocale.getTurSNSite()).isEqualTo(turSNSite);
    }

    @Test
    void testDefaultConstructor() {
        assertThat(turSNSiteLocale).isNotNull();
        assertThat(turSNSiteLocale.getId()).isNull();
        assertThat(turSNSiteLocale.getLanguage()).isNull();
        assertThat(turSNSiteLocale.getCore()).isNull();
        assertThat(turSNSiteLocale.getTurSNSite()).isNull();
    }

    @Test
    void testWithEnglishLocale() {
        turSNSiteLocale.setLanguage(Locale.ENGLISH);
        turSNSiteLocale.setCore("en_US_core");

        assertThat(turSNSiteLocale.getLanguage()).isEqualTo(Locale.ENGLISH);
        assertThat(turSNSiteLocale.getCore()).isEqualTo("en_US_core");
    }

    @Test
    void testWithFrenchLocale() {
        turSNSiteLocale.setLanguage(Locale.FRENCH);
        turSNSiteLocale.setCore("fr_FR_core");

        assertThat(turSNSiteLocale.getLanguage()).isEqualTo(Locale.FRENCH);
        assertThat(turSNSiteLocale.getCore()).isEqualTo("fr_FR_core");
    }

    @Test
    void testWithGermanLocale() {
        turSNSiteLocale.setLanguage(Locale.GERMAN);
        turSNSiteLocale.setCore("de_DE_core");

        assertThat(turSNSiteLocale.getLanguage()).isEqualTo(Locale.GERMAN);
        assertThat(turSNSiteLocale.getCore()).isEqualTo("de_DE_core");
    }

    @Test
    void testWithCustomLocale() {
        Locale customLocale = Locale.of("pt", "BR");
        turSNSiteLocale.setLanguage(customLocale);
        turSNSiteLocale.setCore("pt_BR_core");

        assertThat(turSNSiteLocale.getLanguage()).isEqualTo(customLocale);
        assertThat(turSNSiteLocale.getCore()).isEqualTo("pt_BR_core");
    }

    @Test
    void testSiteRelationship() {
        turSNSiteLocale.setTurSNSite(turSNSite);
        assertThat(turSNSiteLocale.getTurSNSite()).isEqualTo(turSNSite);
    }

    @Test
    void testIdField() {
        String testId = "test-locale-uuid-123";
        turSNSiteLocale.setId(testId);
        assertThat(turSNSiteLocale.getId()).isEqualTo(testId);
    }

    @Test
    void testCoreField() {
        String core1 = "en_core";
        turSNSiteLocale.setCore(core1);
        assertThat(turSNSiteLocale.getCore()).isEqualTo(core1);

        String core2 = "fr_core";
        turSNSiteLocale.setCore(core2);
        assertThat(turSNSiteLocale.getCore()).isEqualTo(core2);
    }

    @Test
    void testLocaleLanguageField() {
        Locale locale1 = Locale.US;
        turSNSiteLocale.setLanguage(locale1);
        assertThat(turSNSiteLocale.getLanguage()).isEqualTo(locale1);

        Locale locale2 = Locale.UK;
        turSNSiteLocale.setLanguage(locale2);
        assertThat(turSNSiteLocale.getLanguage()).isEqualTo(locale2);
    }

    @Test
    void testMultipleLanguageSettings() {
        turSNSiteLocale.setLanguage(Locale.JAPANESE);
        turSNSiteLocale.setCore("ja_core");
        assertThat(turSNSiteLocale.getLanguage()).isEqualTo(Locale.JAPANESE);
        assertThat(turSNSiteLocale.getCore()).isEqualTo("ja_core");

        turSNSiteLocale.setLanguage(Locale.KOREAN);
        turSNSiteLocale.setCore("ko_core");
        assertThat(turSNSiteLocale.getLanguage()).isEqualTo(Locale.KOREAN);
        assertThat(turSNSiteLocale.getCore()).isEqualTo("ko_core");
    }

    @Test
    void testAllFieldsTogether() {
        String id = "complete-locale-id";
        Locale language = Locale.ITALY;
        String core = "it_IT_core";

        turSNSiteLocale.setId(id);
        turSNSiteLocale.setLanguage(language);
        turSNSiteLocale.setCore(core);
        turSNSiteLocale.setTurSNSite(turSNSite);

        assertThat(turSNSiteLocale.getId()).isEqualTo(id);
        assertThat(turSNSiteLocale.getLanguage()).isEqualTo(language);
        assertThat(turSNSiteLocale.getCore()).isEqualTo(core);
        assertThat(turSNSiteLocale.getTurSNSite()).isEqualTo(turSNSite);
    }
}
