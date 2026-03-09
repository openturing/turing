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

package com.viglet.turing.api.ocr;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TurOcrFromUrl.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurOcrFromUrlTest {

    @Test
    void testDefaultConstructor() {
        TurOcrFromUrl ocrFromUrl = new TurOcrFromUrl();
        
        assertThat(ocrFromUrl).isNotNull();
        assertThat(ocrFromUrl.getUrl()).isNull();
    }

    @Test
    void testSetAndGetUrl() {
        TurOcrFromUrl ocrFromUrl = new TurOcrFromUrl();
        
        String url = "https://example.com/document.pdf";
        ocrFromUrl.setUrl(url);
        
        assertThat(ocrFromUrl.getUrl()).isEqualTo(url);
    }

    @Test
    void testSetUrlWithHttpUrl() {
        TurOcrFromUrl ocrFromUrl = new TurOcrFromUrl();
        
        ocrFromUrl.setUrl("http://example.com/file.pdf");
        
        assertThat(ocrFromUrl.getUrl()).isEqualTo("http://example.com/file.pdf");
    }

    @Test
    void testSetUrlWithHttpsUrl() {
        TurOcrFromUrl ocrFromUrl = new TurOcrFromUrl();
        
        ocrFromUrl.setUrl("https://secure.example.com/document.pdf");
        
        assertThat(ocrFromUrl.getUrl()).isEqualTo("https://secure.example.com/document.pdf");
    }

    @Test
    void testSetUrlWithNull() {
        TurOcrFromUrl ocrFromUrl = new TurOcrFromUrl();
        
        ocrFromUrl.setUrl(null);
        
        assertThat(ocrFromUrl.getUrl()).isNull();
    }

    @Test
    void testSetUrlWithEmptyString() {
        TurOcrFromUrl ocrFromUrl = new TurOcrFromUrl();
        
        ocrFromUrl.setUrl("");
        
        assertThat(ocrFromUrl.getUrl()).isEmpty();
    }

    @Test
    void testUpdateUrl() {
        TurOcrFromUrl ocrFromUrl = new TurOcrFromUrl();
        
        ocrFromUrl.setUrl("https://example.com/old.pdf");
        assertThat(ocrFromUrl.getUrl()).isEqualTo("https://example.com/old.pdf");
        
        ocrFromUrl.setUrl("https://example.com/new.pdf");
        assertThat(ocrFromUrl.getUrl()).isEqualTo("https://example.com/new.pdf");
    }

    @Test
    void testSetUrlWithQueryParameters() {
        TurOcrFromUrl ocrFromUrl = new TurOcrFromUrl();
        
        String urlWithParams = "https://example.com/document.pdf?version=1&lang=en";
        ocrFromUrl.setUrl(urlWithParams);
        
        assertThat(ocrFromUrl.getUrl()).isEqualTo(urlWithParams);
    }

    @Test
    void testSetUrlWithFragment() {
        TurOcrFromUrl ocrFromUrl = new TurOcrFromUrl();
        
        String urlWithFragment = "https://example.com/document.pdf#page=5";
        ocrFromUrl.setUrl(urlWithFragment);
        
        assertThat(ocrFromUrl.getUrl()).isEqualTo(urlWithFragment);
    }

    @Test
    void testSetUrlWithPort() {
        TurOcrFromUrl ocrFromUrl = new TurOcrFromUrl();
        
        String urlWithPort = "https://example.com:8080/document.pdf";
        ocrFromUrl.setUrl(urlWithPort);
        
        assertThat(ocrFromUrl.getUrl()).isEqualTo(urlWithPort);
    }

    @Test
    void testSetUrlWithPath() {
        TurOcrFromUrl ocrFromUrl = new TurOcrFromUrl();
        
        String urlWithPath = "https://example.com/path/to/document.pdf";
        ocrFromUrl.setUrl(urlWithPath);
        
        assertThat(ocrFromUrl.getUrl()).isEqualTo(urlWithPath);
    }

    @Test
    void testSetUrlWithDifferentFileTypes() {
        TurOcrFromUrl pdfUrl = new TurOcrFromUrl();
        pdfUrl.setUrl("https://example.com/document.pdf");
        
        TurOcrFromUrl imageUrl = new TurOcrFromUrl();
        imageUrl.setUrl("https://example.com/image.jpg");
        
        TurOcrFromUrl docUrl = new TurOcrFromUrl();
        docUrl.setUrl("https://example.com/document.docx");
        
        assertThat(pdfUrl.getUrl()).isEqualTo("https://example.com/document.pdf");
        assertThat(imageUrl.getUrl()).isEqualTo("https://example.com/image.jpg");
        assertThat(docUrl.getUrl()).isEqualTo("https://example.com/document.docx");
    }

    @Test
    void testSetUrlWithEncodedCharacters() {
        TurOcrFromUrl ocrFromUrl = new TurOcrFromUrl();
        
        String encodedUrl = "https://example.com/document%20name.pdf";
        ocrFromUrl.setUrl(encodedUrl);
        
        assertThat(ocrFromUrl.getUrl()).isEqualTo(encodedUrl);
    }

    @Test
    void testSetUrlMultipleTimes() {
        TurOcrFromUrl ocrFromUrl = new TurOcrFromUrl();
        
        ocrFromUrl.setUrl("https://example.com/file1.pdf");
        ocrFromUrl.setUrl("https://example.com/file2.pdf");
        ocrFromUrl.setUrl("https://example.com/file3.pdf");
        
        assertThat(ocrFromUrl.getUrl()).isEqualTo("https://example.com/file3.pdf");
    }

    @Test
    void testSetUrlWithLocalhost() {
        TurOcrFromUrl ocrFromUrl = new TurOcrFromUrl();
        
        String localhostUrl = "http://localhost:8080/document.pdf";
        ocrFromUrl.setUrl(localhostUrl);
        
        assertThat(ocrFromUrl.getUrl()).isEqualTo(localhostUrl);
    }
}
