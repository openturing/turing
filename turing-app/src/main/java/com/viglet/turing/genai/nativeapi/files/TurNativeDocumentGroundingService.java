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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.citation.TurCitationDocument;
import com.viglet.turing.genai.nativeapi.files.TurFilesApiBridge.TurVendorFileRef;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.service.storage.TurStorageObjectStat;
import com.viglet.turing.service.storage.TurStorageService;

import lombok.extern.slf4j.Slf4j;

/**
 * T176 / §X.12.b — turns the RAG retrieval hits into citation documents where
 * the PDF-backed passages carry the <em>original binary</em> for native
 * grounding, not just the Tika-extracted text.
 *
 * <p>For each retrieved chunk whose source is an indexed PDF still present in
 * {@link TurStorageService}, the source binary is downloaded once, mirrored into
 * the vendor Files API through {@link TurFilesApiBridge} (T175, which caches the
 * {@code file_id}), base64-encoded, and attached as a
 * {@link TurCitationDocument.NativePdf}. The Anthropic path then sends it as a
 * citations-enabled {@code document} block, so Claude reads the real
 * layout/tables/figures and cites by page. Non-PDF chunks (and PDFs once storage
 * is off or the binary is gone) keep the unchanged text passage.
 *
 * <p>Retrieved chunks are <em>collapsed per source</em>: a PDF that produced
 * several chunks is attached natively exactly once (Claude reads the whole
 * document, so the per-chunk text is redundant). Everything is fail-open — any
 * miss degrades that passage to text, never failing the turn.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurNativeDocumentGroundingService {

    private static final String OBJECT_NAME = "objectName";
    private static final String CONTENT_TYPE = "contentType";
    private static final String PDF_MEDIA_TYPE = "application/pdf";

    /**
     * Upper bound on a single native PDF (Anthropic caps document requests at
     * ~32 MB / 100 pages; keep a margin and avoid base64-bloating huge files).
     */
    private static final long MAX_PDF_BYTES = 25L * 1024 * 1024;

    private final TurStorageService storageService;
    private final TurFilesApiBridge filesApiBridge;

    public TurNativeDocumentGroundingService(TurStorageService storageService,
            TurFilesApiBridge filesApiBridge) {
        this.storageService = storageService;
        this.filesApiBridge = filesApiBridge;
    }

    /**
     * Adapt retrieved documents into citation documents, attaching the native
     * PDF source to PDF-backed passages (deduplicated per source). Falls back to
     * the plain text projection ({@link TurCitationDocument#fromDocuments}) when
     * storage is disabled or the list is empty.
     *
     * @param rawDocs  the retrieval hits (same set the text path would cite)
     * @param instance the Anthropic LLM instance whose Files API caches the upload
     */
    public List<TurCitationDocument> buildCitationDocuments(List<Document> rawDocs,
            TurLLMInstance instance) {
        if (rawDocs == null || rawDocs.isEmpty()) {
            return List.of();
        }
        if (!storageService.isEnabled()) {
            return TurCitationDocument.fromDocuments(rawDocs);
        }
        List<TurCitationDocument> out = new ArrayList<>(rawDocs.size());
        Set<String> nativePdfSourcesSeen = new HashSet<>();
        for (Document doc : rawDocs) {
            TurCitationDocument base = TurCitationDocument.fromDocument(doc);
            if (base == null) {
                continue;
            }
            String objectName = raw(doc.getMetadata().get(OBJECT_NAME));
            String contentType = raw(doc.getMetadata().get(CONTENT_TYPE));
            boolean isPdf = PDF_MEDIA_TYPE.equalsIgnoreCase(contentType)
                    && StringUtils.hasText(objectName);
            if (!isPdf) {
                out.add(base);
                continue;
            }
            if (!nativePdfSourcesSeen.add(objectName)) {
                // Whole PDF already attached natively for an earlier chunk — drop
                // this redundant chunk rather than re-send the same document.
                continue;
            }
            TurCitationDocument.NativePdf nativePdf =
                    nativePdfFor(instance, objectName, base.title());
            out.add(nativePdf != null ? base.withNativePdf(nativePdf) : base);
        }
        return out;
    }

    /**
     * Download the PDF, mirror it into the vendor Files API (cached) and base64
     * encode it. Returns {@code null} on any miss (storage error, oversize,
     * empty) so the caller keeps the text passage.
     */
    private TurCitationDocument.NativePdf nativePdfFor(TurLLMInstance instance, String objectName,
            String title) {
        try {
            TurStorageObjectStat stat = safeStat(objectName);
            if (stat != null && stat.size() > MAX_PDF_BYTES) {
                log.debug("[Files-API] PDF '{}' is {} bytes (> {} cap) — keeping text grounding",
                        objectName, stat.size(), MAX_PDF_BYTES);
                return null;
            }
            byte[] bytes = download(objectName);
            if (bytes.length == 0 || bytes.length > MAX_PDF_BYTES) {
                return null;
            }
            String fileName = StringUtils.hasText(title) ? title : objectName;
            // T175 — cache the vendor file_id (dedup across agents/turns, T177;
            // future file-source migration). Fail-open: a failed upload still
            // yields a valid base64 native PDF block.
            Optional<TurVendorFileRef> ref =
                    filesApiBridge.ensureUploaded(instance, bytes, fileName, PDF_MEDIA_TYPE);
            String vendorFileId = ref.map(TurVendorFileRef::vendorFileId).orElse(null);
            String base64 = Base64.getEncoder().encodeToString(bytes);
            return new TurCitationDocument.NativePdf(base64, PDF_MEDIA_TYPE, vendorFileId);
        } catch (RuntimeException e) {
            log.warn("[Files-API] native PDF grounding for '{}' failed — keeping text: {}",
                    objectName, e.getMessage());
            return null;
        }
    }

    private TurStorageObjectStat safeStat(String objectName) {
        try {
            return storageService.statObject(objectName);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private byte[] download(String objectName) {
        try (InputStream in = storageService.downloadObject(objectName)) {
            return in == null ? new byte[0] : in.readAllBytes();
        } catch (Exception e) {
            log.debug("[Files-API] could not download '{}': {}", objectName, e.getMessage());
            return new byte[0];
        }
    }

    private static String raw(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
