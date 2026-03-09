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

package com.viglet.turing.plugins.se;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TurSearchEngineInstanceManager interface.
 * Tests a mock implementation to verify interface contract.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurSearchEngineInstanceManagerTest {

    private TurSearchEngineInstanceManager manager;

    @BeforeEach
    void setUp() {
        manager = new TestSearchEngineInstanceManager();
    }

    @Test
    void testInitInstanceWithValidParameters() {
        Optional<Object> result = manager.initInstance("testSite", Locale.ENGLISH);

        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(String.class);
        assertThat(result.get().toString()).contains("Instance for testSite").contains("locale en");
    }

    @Test
    void testInitInstanceWithNullSiteName() {
        Optional<Object> result = manager.initInstance(null, Locale.ENGLISH);

        assertThat(result).isEmpty();
    }

    @Test
    void testInitInstanceWithEmptySiteName() {
        Optional<Object> result = manager.initInstance("", Locale.ENGLISH);

        assertThat(result).isEmpty();
    }

    @Test
    void testInitInstanceWithNullLocale() {
        Optional<Object> result = manager.initInstance("testSite", null);

        assertThat(result).isEmpty();
    }

    @Test
    void testInitInstanceWithDifferentLocales() {
        Optional<Object> resultEn = manager.initInstance("site1", Locale.ENGLISH);
        Optional<Object> resultFr = manager.initInstance("site1", Locale.FRENCH);
        Optional<Object> resultDe = manager.initInstance("site1", Locale.GERMAN);

        assertThat(resultEn).isPresent();
        assertThat(resultFr).isPresent();
        assertThat(resultDe).isPresent();
        assertThat(resultEn.get()).isNotEqualTo(resultFr.get());
        assertThat(resultEn.get()).isNotEqualTo(resultDe.get());
    }

    @Test
    void testGetSearchEngineUrlWithValidParameters() {
        Optional<URL> result = manager.getSearchEngineUrl("testSite", Locale.ENGLISH);

        assertThat(result).isPresent();
        assertThat(result.get().toString()).contains("testSite");
        assertThat(result.get().toString()).contains("en");
    }

    @Test
    void testGetSearchEngineUrlWithNullSiteName() {
        Optional<URL> result = manager.getSearchEngineUrl(null, Locale.ENGLISH);

        assertThat(result).isEmpty();
    }

    @Test
    void testGetSearchEngineUrlWithEmptySiteName() {
        Optional<URL> result = manager.getSearchEngineUrl("", Locale.ENGLISH);

        assertThat(result).isEmpty();
    }

    @Test
    void testGetSearchEngineUrlWithNullLocale() {
        Optional<URL> result = manager.getSearchEngineUrl("testSite", null);

        assertThat(result).isEmpty();
    }

    @Test
    void testGetEngineType() {
        String engineType = manager.getEngineType();

        assertThat(engineType)
                .isNotNull()
                .isNotEmpty()
                .isEqualTo("test");
    }

    @Test
    void testGetEngineTypeIsConsistent() {
        String type1 = manager.getEngineType();
        String type2 = manager.getEngineType();

        assertThat(type1).isEqualTo(type2);
    }

    @Test
    void testInitInstanceReturnsOptional() {
        Optional<Object> result = manager.initInstance("testSite", Locale.ENGLISH);

        assertThat(result).isInstanceOf(Optional.class);
    }

    @Test
    void testGetSearchEngineUrlReturnsOptional() {
        Optional<URL> result = manager.getSearchEngineUrl("testSite", Locale.ENGLISH);

        assertThat(result).isInstanceOf(Optional.class);
    }

    @Test
    void testInitInstanceWithSpecialCharactersInSiteName() {
        Optional<Object> result = manager.initInstance("test-site_123", Locale.ENGLISH);

        assertThat(result).isPresent();
    }

    @Test
    void testGetSearchEngineUrlWithDifferentLocales() {
        Optional<URL> resultEn = manager.getSearchEngineUrl("site1", Locale.ENGLISH);
        Optional<URL> resultFr = manager.getSearchEngineUrl("site1", Locale.FRENCH);

        assertThat(resultEn).isPresent();
        assertThat(resultFr).isPresent();
        assertThat(resultEn.get()).isNotEqualTo(resultFr.get());
    }

    @Test
    void testInitInstanceWithJapaneseLocale() {
        Optional<Object> result = manager.initInstance("testSite", Locale.JAPANESE);

        assertThat(result).isPresent();
        assertThat(result.get().toString()).contains("ja");
    }

    @Test
    void testGetSearchEngineUrlWithChineseLocale() {
        Optional<URL> result = manager.getSearchEngineUrl("testSite", Locale.CHINESE);

        assertThat(result).isPresent();
        assertThat(result.get().toString()).contains("zh");
    }

    /**
     * Test implementation of TurSearchEngineInstanceManager for testing purposes.
     */
    private static class TestSearchEngineInstanceManager implements TurSearchEngineInstanceManager {

        @Override
        public Optional<Object> initInstance(String siteName, Locale locale) {
            if (siteName == null || siteName.isEmpty() || locale == null) {
                return Optional.empty();
            }
            return Optional.of("Instance for " + siteName + " with locale " + locale.getLanguage());
        }

        @Override
        public Optional<URL> getSearchEngineUrl(String siteName, Locale locale) {
            if (siteName == null || siteName.isEmpty() || locale == null) {
                return Optional.empty();
            }
            try {
                return Optional
                        .of(URI.create("http://localhost:8983/solr/" + siteName + "/" + locale.getLanguage()).toURL());
            } catch (MalformedURLException e) {
                return Optional.empty();
            }
        }

        @Override
        public String getEngineType() {
            return "test";
        }
    }
}
