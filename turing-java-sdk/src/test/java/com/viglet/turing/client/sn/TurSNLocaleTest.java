/*
 * Copyright (C) 2016-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.viglet.turing.client.sn;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for TurSNLocale.
 *
 * @author Alexandre Oliveira
 * @since 0.3.5
 */
class TurSNLocaleTest {

    @Test
    void testDefaultConstructor() {
        TurSNLocale locale = new TurSNLocale();

        assertThat(locale.getLocale()).isNull();
        assertThat(locale.getLink()).isNull();
    }

    @Test
    void testSettersAndGetters() {
        TurSNLocale locale = new TurSNLocale();

        locale.setLocale("en_US");
        locale.setLink("/search?locale=en_US");

        assertThat(locale.getLocale()).isEqualTo("en_US");
        assertThat(locale.getLink()).isEqualTo("/search?locale=en_US");
    }

    @Test
    void testToStringWithBothValues() {
        TurSNLocale locale = new TurSNLocale();
        locale.setLocale("pt_BR");
        locale.setLink("/search?locale=pt_BR");

        String result = locale.toString();

        assertThat(result).isEqualTo("pt_BR: /search?locale=pt_BR");
    }

    @Test
    void testToStringWithNullValues() {
        TurSNLocale locale = new TurSNLocale();

        String result = locale.toString();

        assertThat(result).isEqualTo("null: null");
    }

    @Test
    void testToStringWithNullLocale() {
        TurSNLocale locale = new TurSNLocale();
        locale.setLink("/search");

        String result = locale.toString();

        assertThat(result).isEqualTo("null: /search");
    }

    @Test
    void testToStringWithNullLink() {
        TurSNLocale locale = new TurSNLocale();
        locale.setLocale("fr_FR");

        String result = locale.toString();

        assertThat(result).isEqualTo("fr_FR: null");
    }

    @Test
    void testSetLocaleWithDifferentFormats() {
        TurSNLocale locale = new TurSNLocale();

        // Test with language only
        locale.setLocale("en");
        assertThat(locale.getLocale()).isEqualTo("en");

        // Test with language and country
        locale.setLocale("en_US");
        assertThat(locale.getLocale()).isEqualTo("en_US");

        // Test with language, country and variant
        locale.setLocale("en_US_POSIX");
        assertThat(locale.getLocale()).isEqualTo("en_US_POSIX");
    }

    @Test
    void testSetLinkWithDifferentFormats() {
        TurSNLocale locale = new TurSNLocale();

        // Test absolute URL
        locale.setLink("https://example.com/search?locale=en");
        assertThat(locale.getLink()).isEqualTo("https://example.com/search?locale=en");

        // Test relative URL
        locale.setLink("/api/search");
        assertThat(locale.getLink()).isEqualTo("/api/search");

        // Test with query parameters
        locale.setLink("/search?q=test&locale=en_US&page=1");
        assertThat(locale.getLink()).isEqualTo("/search?q=test&locale=en_US&page=1");
    }

    @Test
    void testEmptyStringValues() {
        TurSNLocale locale = new TurSNLocale();

        locale.setLocale("");
        locale.setLink("");

        assertThat(locale.getLocale()).isEmpty();
        assertThat(locale.getLink()).isEmpty();
        assertThat(locale).hasToString(": ");
    }

    @Test
    void testUpdateValues() {
        TurSNLocale locale = new TurSNLocale();

        // Set initial values
        locale.setLocale("en");
        locale.setLink("/search/en");

        assertThat(locale.getLocale()).isEqualTo("en");
        assertThat(locale.getLink()).isEqualTo("/search/en");

        // Update values
        locale.setLocale("es");
        locale.setLink("/search/es");

        assertThat(locale.getLocale()).isEqualTo("es");
        assertThat(locale.getLink()).isEqualTo("/search/es");
    }

    @Test
    void testSpecialCharactersInLocale() {
        TurSNLocale locale = new TurSNLocale();

        locale.setLocale("zh_CN@calendar=chinese");
        assertThat(locale.getLocale()).isEqualTo("zh_CN@calendar=chinese");
    }

    @Test
    void testSpecialCharactersInLink() {
        TurSNLocale locale = new TurSNLocale();

        locale.setLink("/search?q=café&locale=fr_FR");
        assertThat(locale.getLink()).isEqualTo("/search?q=café&locale=fr_FR");
    }

    @Test
    void testToStringFormat() {
        TurSNLocale locale = new TurSNLocale();
        locale.setLocale("de_DE");
        locale.setLink("https://example.de/suchen");
        String result = locale.toString();
        assertThat(result).isEqualTo("de_DE: https://example.de/suchen");
    }

    @Test
    void testLocaleNormalization() {
        TurSNLocale locale = new TurSNLocale();

        // Test various locale formats
        String[] locales = {
                "en",
                "EN",
                "en_US",
                "en_us",
                "EN_US",
                "zh_Hans_CN"
        };

        for (String localeStr : locales) {
            locale.setLocale(localeStr);
            assertThat(locale.getLocale()).isEqualTo(localeStr);
        }
    }

    @Test
    void testLinkWithFragment() {
        TurSNLocale locale = new TurSNLocale();

        locale.setLink("/search?locale=en_US#results");
        assertThat(locale.getLink()).isEqualTo("/search?locale=en_US#results");

        String result = locale.toString();
        assertThat(result).contains("#results");
    }

    @Test
    void testMultipleLocaleObjects() {
        TurSNLocale locale1 = new TurSNLocale();
        locale1.setLocale("en_US");
        locale1.setLink("/en");

        TurSNLocale locale2 = new TurSNLocale();
        locale2.setLocale("fr_FR");
        locale2.setLink("/fr");

        assertThat(locale1.getLocale()).isNotEqualTo(locale2.getLocale());
        assertThat(locale1.getLink()).isNotEqualTo(locale2.getLink());
        assertThat(locale1.toString()).isNotEqualTo(locale2.toString());
    }
}