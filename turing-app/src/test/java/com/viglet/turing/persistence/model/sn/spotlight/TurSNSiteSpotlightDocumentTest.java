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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for TurSNSiteSpotlightDocument.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteSpotlightDocumentTest {

    @Mock
    private TurSNSiteSpotlight turSNSiteSpotlight;

    private TurSNSiteSpotlightDocument turSNSiteSpotlightDocument;

    @BeforeEach
    void setUp() {
        turSNSiteSpotlightDocument = new TurSNSiteSpotlightDocument();
    }

    @Test
    void testGettersAndSetters() {
        String id = "doc-id-123";
        int position = 5;
        String title = "Test Document Title";
        String type = "article";
        String referenceId = "ref-123";
        String content = "This is the document content";
        String link = "https://example.com/document";

        turSNSiteSpotlightDocument.setId(id);
        turSNSiteSpotlightDocument.setPosition(position);
        turSNSiteSpotlightDocument.setTitle(title);
        turSNSiteSpotlightDocument.setType(type);
        turSNSiteSpotlightDocument.setReferenceId(referenceId);
        turSNSiteSpotlightDocument.setContent(content);
        turSNSiteSpotlightDocument.setLink(link);
        turSNSiteSpotlightDocument.setTurSNSiteSpotlight(turSNSiteSpotlight);

        assertThat(turSNSiteSpotlightDocument.getId()).isEqualTo(id);
        assertThat(turSNSiteSpotlightDocument.getPosition()).isEqualTo(position);
        assertThat(turSNSiteSpotlightDocument.getTitle()).isEqualTo(title);
        assertThat(turSNSiteSpotlightDocument.getType()).isEqualTo(type);
        assertThat(turSNSiteSpotlightDocument.getReferenceId()).isEqualTo(referenceId);
        assertThat(turSNSiteSpotlightDocument.getContent()).isEqualTo(content);
        assertThat(turSNSiteSpotlightDocument.getLink()).isEqualTo(link);
        assertThat(turSNSiteSpotlightDocument.getTurSNSiteSpotlight()).isEqualTo(turSNSiteSpotlight);
    }

    @Test
    void testDefaultConstructor() {
        assertThat(turSNSiteSpotlightDocument).isNotNull();
        assertThat(turSNSiteSpotlightDocument.getId()).isNull();
        assertThat(turSNSiteSpotlightDocument.getPosition()).isZero();
        assertThat(turSNSiteSpotlightDocument.getTitle()).isNull();
        assertThat(turSNSiteSpotlightDocument.getType()).isNull();
        assertThat(turSNSiteSpotlightDocument.getReferenceId()).isNull();
        assertThat(turSNSiteSpotlightDocument.getContent()).isNull();
        assertThat(turSNSiteSpotlightDocument.getLink()).isNull();
        assertThat(turSNSiteSpotlightDocument.getTurSNSiteSpotlight()).isNull();
    }

    @Test
    void testPositionField() {
        turSNSiteSpotlightDocument.setPosition(0);
        assertThat(turSNSiteSpotlightDocument.getPosition()).isZero();

        turSNSiteSpotlightDocument.setPosition(10);
        assertThat(turSNSiteSpotlightDocument.getPosition()).isEqualTo(10);

        turSNSiteSpotlightDocument.setPosition(999);
        assertThat(turSNSiteSpotlightDocument.getPosition()).isEqualTo(999);
    }

    @Test
    void testTitleField() {
        String title1 = "First Title";
        turSNSiteSpotlightDocument.setTitle(title1);
        assertThat(turSNSiteSpotlightDocument.getTitle()).isEqualTo(title1);

        String title2 = "Second Title";
        turSNSiteSpotlightDocument.setTitle(title2);
        assertThat(turSNSiteSpotlightDocument.getTitle()).isEqualTo(title2);
    }

    @Test
    void testTypeField() {
        turSNSiteSpotlightDocument.setType("blog");
        assertThat(turSNSiteSpotlightDocument.getType()).isEqualTo("blog");

        turSNSiteSpotlightDocument.setType("news");
        assertThat(turSNSiteSpotlightDocument.getType()).isEqualTo("news");
    }

    @Test
    void testReferenceIdField() {
        String refId = "reference-abc-123";
        turSNSiteSpotlightDocument.setReferenceId(refId);
        assertThat(turSNSiteSpotlightDocument.getReferenceId()).isEqualTo(refId);
    }

    @Test
    void testContentField() {
        String content = "This is a long piece of content that describes the document in detail.";
        turSNSiteSpotlightDocument.setContent(content);
        assertThat(turSNSiteSpotlightDocument.getContent()).isEqualTo(content);
    }

    @Test
    void testContentFieldMaxLength() {
        String longContent = "a".repeat(2000);
        turSNSiteSpotlightDocument.setContent(longContent);
        assertThat(turSNSiteSpotlightDocument.getContent()).hasSize(2000);
    }

    @Test
    void testLinkField() {
        String link1 = "https://example.com/page1";
        turSNSiteSpotlightDocument.setLink(link1);
        assertThat(turSNSiteSpotlightDocument.getLink()).isEqualTo(link1);

        String link2 = "https://example.com/page2";
        turSNSiteSpotlightDocument.setLink(link2);
        assertThat(turSNSiteSpotlightDocument.getLink()).isEqualTo(link2);
    }

    @Test
    void testSpotlightRelationship() {
        turSNSiteSpotlightDocument.setTurSNSiteSpotlight(turSNSiteSpotlight);
        assertThat(turSNSiteSpotlightDocument.getTurSNSiteSpotlight()).isEqualTo(turSNSiteSpotlight);
    }

    @Test
    void testCompleteDocument() {
        String id = "complete-doc-id";
        int position = 3;
        String title = "Complete Document";
        String type = "product";
        String referenceId = "prod-ref-789";
        String content = "Complete document content with all details";
        String link = "https://example.com/complete";

        turSNSiteSpotlightDocument.setId(id);
        turSNSiteSpotlightDocument.setPosition(position);
        turSNSiteSpotlightDocument.setTitle(title);
        turSNSiteSpotlightDocument.setType(type);
        turSNSiteSpotlightDocument.setReferenceId(referenceId);
        turSNSiteSpotlightDocument.setContent(content);
        turSNSiteSpotlightDocument.setLink(link);
        turSNSiteSpotlightDocument.setTurSNSiteSpotlight(turSNSiteSpotlight);

        assertThat(turSNSiteSpotlightDocument.getId()).isEqualTo(id);
        assertThat(turSNSiteSpotlightDocument.getPosition()).isEqualTo(position);
        assertThat(turSNSiteSpotlightDocument.getTitle()).isEqualTo(title);
        assertThat(turSNSiteSpotlightDocument.getType()).isEqualTo(type);
        assertThat(turSNSiteSpotlightDocument.getReferenceId()).isEqualTo(referenceId);
        assertThat(turSNSiteSpotlightDocument.getContent()).isEqualTo(content);
        assertThat(turSNSiteSpotlightDocument.getLink()).isEqualTo(link);
        assertThat(turSNSiteSpotlightDocument.getTurSNSiteSpotlight()).isEqualTo(turSNSiteSpotlight);
    }

    @Test
    void testNullableFields() {
        turSNSiteSpotlightDocument.setTitle(null);
        turSNSiteSpotlightDocument.setType(null);
        turSNSiteSpotlightDocument.setReferenceId(null);
        turSNSiteSpotlightDocument.setContent(null);
        turSNSiteSpotlightDocument.setLink(null);

        assertThat(turSNSiteSpotlightDocument.getTitle()).isNull();
        assertThat(turSNSiteSpotlightDocument.getType()).isNull();
        assertThat(turSNSiteSpotlightDocument.getReferenceId()).isNull();
        assertThat(turSNSiteSpotlightDocument.getContent()).isNull();
        assertThat(turSNSiteSpotlightDocument.getLink()).isNull();
    }

    @Test
    void testIdField() {
        String testId = "test-document-uuid";
        turSNSiteSpotlightDocument.setId(testId);
        assertThat(turSNSiteSpotlightDocument.getId()).isEqualTo(testId);
    }

    @Test
    void testMultiplePositionSettings() {
        turSNSiteSpotlightDocument.setPosition(1);
        assertThat(turSNSiteSpotlightDocument.getPosition()).isEqualTo(1);

        turSNSiteSpotlightDocument.setPosition(5);
        assertThat(turSNSiteSpotlightDocument.getPosition()).isEqualTo(5);

        turSNSiteSpotlightDocument.setPosition(10);
        assertThat(turSNSiteSpotlightDocument.getPosition()).isEqualTo(10);
    }

    @Test
    void testEmptyStrings() {
        turSNSiteSpotlightDocument.setTitle("");
        turSNSiteSpotlightDocument.setType("");
        turSNSiteSpotlightDocument.setReferenceId("");
        turSNSiteSpotlightDocument.setContent("");
        turSNSiteSpotlightDocument.setLink("");

        assertThat(turSNSiteSpotlightDocument.getTitle()).isEmpty();
        assertThat(turSNSiteSpotlightDocument.getType()).isEmpty();
        assertThat(turSNSiteSpotlightDocument.getReferenceId()).isEmpty();
        assertThat(turSNSiteSpotlightDocument.getContent()).isEmpty();
        assertThat(turSNSiteSpotlightDocument.getLink()).isEmpty();
    }

    @Test
    void testLongTitle() {
        String longTitle = "This is a very long title that might contain many words to describe the document";
        turSNSiteSpotlightDocument.setTitle(longTitle);
        assertThat(turSNSiteSpotlightDocument.getTitle()).isEqualTo(longTitle);
    }

    @Test
    void testVariousLinkFormats() {
        turSNSiteSpotlightDocument.setLink("http://example.com");
        assertThat(turSNSiteSpotlightDocument.getLink()).isEqualTo("http://example.com");

        turSNSiteSpotlightDocument.setLink("https://secure.example.com/path");
        assertThat(turSNSiteSpotlightDocument.getLink()).isEqualTo("https://secure.example.com/path");

        turSNSiteSpotlightDocument.setLink("/relative/path");
        assertThat(turSNSiteSpotlightDocument.getLink()).isEqualTo("/relative/path");
    }
}
