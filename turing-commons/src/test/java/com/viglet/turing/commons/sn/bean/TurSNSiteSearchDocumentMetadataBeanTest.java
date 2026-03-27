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

package com.viglet.turing.commons.sn.bean;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for TurSNSiteSearchDocumentMetadataBean.
 *
 * @author Alexandre Oliveira
 * @since 0.3.4
 */
class TurSNSiteSearchDocumentMetadataBeanTest {

    @Test
    void testDefaultConstructor() {
        TurSNSiteSearchDocumentMetadataBean metadata = new TurSNSiteSearchDocumentMetadataBean();

        assertThat(metadata.getHref()).isNull();
        assertThat(metadata.getText()).isNull();
    }

    @Test
    void testAllArgsConstructor() {
        String href = "https://example.com/document/123";
        String text = "Example Document Title";

        TurSNSiteSearchDocumentMetadataBean metadata = new TurSNSiteSearchDocumentMetadataBean(href, text);

        assertThat(metadata.getHref()).isEqualTo(href);
        assertThat(metadata.getText()).isEqualTo(text);
    }

    @Test
    void testBuilderPattern() {
        TurSNSiteSearchDocumentMetadataBean metadata = TurSNSiteSearchDocumentMetadataBean.builder()
                .href("https://viglet.org")
                .text("Viglet Turing")
                .build();

        assertThat(metadata.getHref()).isEqualTo("https://viglet.org");
        assertThat(metadata.getText()).isEqualTo("Viglet Turing");
    }

    @Test
    void testBuilderWithNullValues() {
        TurSNSiteSearchDocumentMetadataBean metadata = TurSNSiteSearchDocumentMetadataBean.builder()
                .href(null)
                .text(null)
                .build();

        assertThat(metadata.getHref()).isNull();
        assertThat(metadata.getText()).isNull();
    }

    @Test
    void testSettersAndGetters() {
        TurSNSiteSearchDocumentMetadataBean metadata = new TurSNSiteSearchDocumentMetadataBean();

        metadata.setHref("https://example.org/page");
        metadata.setText("Sample Page");

        assertThat(metadata.getHref()).isEqualTo("https://example.org/page");
        assertThat(metadata.getText()).isEqualTo("Sample Page");
    }

    @Test
    void testToBuilder() {
        TurSNSiteSearchDocumentMetadataBean original = TurSNSiteSearchDocumentMetadataBean.builder()
                .href("https://original.com")
                .text("Original Text")
                .build();

        TurSNSiteSearchDocumentMetadataBean modified = original.toBuilder()
                .text("Modified Text")
                .build();

        // Original should remain unchanged
        assertThat(original.getHref()).isEqualTo("https://original.com");
        assertThat(original.getText()).isEqualTo("Original Text");

        // Modified should have updated text but same href
        assertThat(modified.getHref()).isEqualTo("https://original.com");
        assertThat(modified.getText()).isEqualTo("Modified Text");
    }

    @Test
    void testImplementsSerializable() {
        TurSNSiteSearchDocumentMetadataBean metadata = new TurSNSiteSearchDocumentMetadataBean();

        assertThat(metadata).isInstanceOf(Serializable.class);
    }

    @Test
    void testUpdateFields() {
        TurSNSiteSearchDocumentMetadataBean metadata = new TurSNSiteSearchDocumentMetadataBean();

        // Start with null values
        assertThat(metadata.getHref()).isNull();
        assertThat(metadata.getText()).isNull();

        // Set initial values
        metadata.setHref("https://first.com");
        metadata.setText("First Text");

        assertThat(metadata.getHref()).isEqualTo("https://first.com");
        assertThat(metadata.getText()).isEqualTo("First Text");

        // Update values
        metadata.setHref("https://second.com");
        metadata.setText("Second Text");

        assertThat(metadata.getHref()).isEqualTo("https://second.com");
        assertThat(metadata.getText()).isEqualTo("Second Text");
    }

    @Test
    void testEmptyStringValues() {
        TurSNSiteSearchDocumentMetadataBean metadata = TurSNSiteSearchDocumentMetadataBean.builder()
                .href("")
                .text("")
                .build();

        assertThat(metadata.getHref()).isEmpty();
        assertThat(metadata.getText()).isEmpty();
    }

    @Test
    void testLongUrlAndText() {
        String longUrl = "https://example.com/very/long/path/to/document/with/many/segments/and/parameters?param1=value1&param2=value2&param3=value3";
        String longText = "This is a very long document title that might contain multiple sentences and special characters like émotions, números, and símbolos.";

        TurSNSiteSearchDocumentMetadataBean metadata = TurSNSiteSearchDocumentMetadataBean.builder()
                .href(longUrl)
                .text(longText)
                .build();

        assertThat(metadata.getHref()).isEqualTo(longUrl);
        assertThat(metadata.getText()).isEqualTo(longText);
        assertThat(metadata.getHref().length()).isGreaterThan(100);
        assertThat(metadata.getText().length()).isGreaterThan(100);
    }

    @Test
    void testSpecialCharacters() {
        String specialHref = "https://example.com/документ/文档/مستند";
        String specialText = "Document with émotions (1), números [2], and símbolos {3}";

        TurSNSiteSearchDocumentMetadataBean metadata = new TurSNSiteSearchDocumentMetadataBean();
        metadata.setHref(specialHref);
        metadata.setText(specialText);

        assertThat(metadata.getHref()).isEqualTo(specialHref);
        assertThat(metadata.getText()).isEqualTo(specialText);
    }
}