/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import com.viglet.turing.genai.citation.TurCitationDocument;
import com.viglet.turing.genai.nativeapi.files.TurFilesApiBridge.TurVendorFileRef;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.service.storage.TurStorageObjectStat;
import com.viglet.turing.service.storage.TurStorageService;

/**
 * T176 — coverage for {@link TurNativeDocumentGroundingService}: native PDF
 * enrichment, per-source dedup, non-PDF passthrough, the storage-off fallback,
 * and the oversize / download fail-open paths.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurNativeDocumentGroundingServiceTest {

    private static final byte[] PDF = "%PDF-1.7 body".getBytes(StandardCharsets.UTF_8);

    @Mock
    private TurStorageService storageService;
    @Mock
    private TurFilesApiBridge filesApiBridge;
    @InjectMocks
    private TurNativeDocumentGroundingService service;

    private final TurLLMInstance instance = mock(TurLLMInstance.class);

    private static Document pdfChunk(String id, String objectName, String text) {
        return Document.builder().id(id).text(text)
                .metadata(Map.of("objectName", objectName, "contentType", "application/pdf",
                        "source_id", objectName, "title", objectName))
                .build();
    }

    @Test
    void attachesNativePdfAndCachesFileId() {
        when(storageService.isEnabled()).thenReturn(true);
        when(storageService.statObject("report.pdf"))
                .thenReturn(new TurStorageObjectStat("report.pdf", PDF.length, "application/pdf", ""));
        when(storageService.downloadObject("report.pdf")).thenReturn(new ByteArrayInputStream(PDF));
        when(filesApiBridge.ensureUploaded(eq(instance), any(), any(), eq("application/pdf")))
                .thenReturn(Optional.of(new TurVendorFileRef("file_1", "anthropic", "report.pdf",
                        "application/pdf", PDF.length)));

        List<TurCitationDocument> out = service.buildCitationDocuments(
                List.of(pdfChunk("c1", "report.pdf", "chunk one")), instance);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).hasNativePdf()).isTrue();
        assertThat(out.get(0).nativePdf().mediaType()).isEqualTo("application/pdf");
        assertThat(out.get(0).nativePdf().vendorFileId()).isEqualTo("file_1");
        assertThat(out.get(0).nativePdf().base64())
                .isEqualTo(java.util.Base64.getEncoder().encodeToString(PDF));
    }

    @Test
    void collapsesMultipleChunksOfTheSamePdfToOneNativeBlock() {
        when(storageService.isEnabled()).thenReturn(true);
        lenient().when(storageService.statObject("report.pdf"))
                .thenReturn(new TurStorageObjectStat("report.pdf", PDF.length, "application/pdf", ""));
        when(storageService.downloadObject("report.pdf")).thenReturn(new ByteArrayInputStream(PDF));
        when(filesApiBridge.ensureUploaded(any(), any(), any(), any()))
                .thenReturn(Optional.of(new TurVendorFileRef("file_1", "anthropic", "report.pdf",
                        "application/pdf", PDF.length)));

        List<TurCitationDocument> out = service.buildCitationDocuments(List.of(
                pdfChunk("c1", "report.pdf", "chunk one"),
                pdfChunk("c2", "report.pdf", "chunk two")), instance);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).hasNativePdf()).isTrue();
        // Downloaded / uploaded exactly once despite two chunks.
        verify(storageService).downloadObject("report.pdf");
        verify(filesApiBridge).ensureUploaded(any(), any(), any(), any());
    }

    @Test
    void keepsNonPdfPassagesAsText() {
        when(storageService.isEnabled()).thenReturn(true);
        Document html = Document.builder().id("h1").text("hello")
                .metadata(Map.of("objectName", "page.html", "contentType", "text/html",
                        "source_id", "page.html", "title", "Page"))
                .build();

        List<TurCitationDocument> out = service.buildCitationDocuments(List.of(html), instance);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).hasNativePdf()).isFalse();
        verify(filesApiBridge, never()).ensureUploaded(any(), any(), any(), any());
    }

    @Test
    void fallsBackToTextProjectionWhenStorageDisabled() {
        when(storageService.isEnabled()).thenReturn(false);

        List<TurCitationDocument> out = service.buildCitationDocuments(
                List.of(pdfChunk("c1", "report.pdf", "chunk one")), instance);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).hasNativePdf()).isFalse();
        verify(storageService, never()).downloadObject(any());
        verify(filesApiBridge, never()).ensureUploaded(any(), any(), any(), any());
    }

    @Test
    void keepsTextWhenPdfExceedsSizeCap() {
        when(storageService.isEnabled()).thenReturn(true);
        when(storageService.statObject("huge.pdf"))
                .thenReturn(new TurStorageObjectStat("huge.pdf", 30L * 1024 * 1024, "application/pdf", ""));

        List<TurCitationDocument> out = service.buildCitationDocuments(
                List.of(pdfChunk("c1", "huge.pdf", "chunk one")), instance);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).hasNativePdf()).isFalse();
        verify(storageService, never()).downloadObject(any());
        verify(filesApiBridge, never()).ensureUploaded(any(), any(), any(), any());
    }

    @Test
    void stillProducesBase64WhenBridgeUploadFails() {
        when(storageService.isEnabled()).thenReturn(true);
        lenient().when(storageService.statObject(any()))
                .thenReturn(new TurStorageObjectStat("report.pdf", PDF.length, "application/pdf", ""));
        when(storageService.downloadObject("report.pdf")).thenReturn(new ByteArrayInputStream(PDF));
        when(filesApiBridge.ensureUploaded(any(), any(), any(), any())).thenReturn(Optional.empty());

        List<TurCitationDocument> out = service.buildCitationDocuments(
                List.of(pdfChunk("c1", "report.pdf", "chunk one")), instance);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).hasNativePdf()).isTrue();
        assertThat(out.get(0).nativePdf().vendorFileId()).isNull();
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertThat(service.buildCitationDocuments(List.of(), instance)).isEmpty();
        assertThat(service.buildCitationDocuments(null, instance)).isEmpty();
    }
}
