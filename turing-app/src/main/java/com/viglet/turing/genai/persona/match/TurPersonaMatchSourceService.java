/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.match;

import java.time.Instant;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.commons.file.TurFileAttributes;
import com.viglet.turing.genai.persona.TurPersonaSnDocResolver;
import com.viglet.turing.genai.urlfetch.TurUrlFetchService;
import com.viglet.turing.persistence.model.persona.TurPersonaSourceStatus;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchSource;
import com.viglet.turing.utils.TurFileUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Project-scoped content extraction for Persona Match (Block AT / §XLIII, T697).
 * The direct analogue of Block AA's {@code TurPersonaSourceService}, reusing the
 * same underlying extraction paths verbatim — the SSRF-guarded URL fetch, Tika
 * document parsing, and the SN-document resolver — but operating on a
 * project-owned {@link TurPersonaMatchSource} instead of a persona-owned source.
 * Never throws on a bad source: failures are recorded on the row as
 * {@code FAILED} so the studio can fix the reference and re-extract.
 *
 * <p>On success it also stamps a {@link #contentHash(String) content hash} so the
 * N×N runner (T698) can skip re-evaluating cells whose content is unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurPersonaMatchSourceService {

    private final TurPersonaSnDocResolver snDocResolver;
    private final TurUrlFetchService urlFetchService;

    public TurPersonaMatchSourceService(TurPersonaSnDocResolver snDocResolver,
            TurUrlFetchService urlFetchService) {
        this.snDocResolver = snDocResolver;
        this.urlFetchService = urlFetchService;
    }

    /**
     * (Re-)extract text for an SN_DOC or URL source. ASSET sources cannot be
     * re-fetched (the binary is not retained); their text is captured once via
     * {@link #extractFromUpload}.
     */
    public TurPersonaMatchSource extract(TurPersonaMatchSource source) {
        if (source == null || source.getType() == null) {
            return source;
        }
        return switch (source.getType()) {
            case URL -> applyText(source, extractUrl(source.getRef()));
            case SN_DOC -> applyText(source,
                    snDocResolver.resolveText(source.getSiteName(), source.getRef()));
            case ASSET -> reaffirmAsset(source);
        };
    }

    /** Capture text from a freshly uploaded asset (PDF/DOC/etc.). */
    public TurPersonaMatchSource extractFromUpload(TurPersonaMatchSource source,
            MultipartFile file) {
        try {
            TurFileAttributes attributes = TurFileUtils.documentToText(file);
            return applyText(source, attributes == null ? null : attributes.getContent());
        } catch (RuntimeException e) {
            log.warn("[PersonaMatchSource] asset extraction failed for {}: {}",
                    source.getRef(), e.getMessage());
            return fail(source, "Could not read uploaded file");
        }
    }

    /**
     * Stable change-detection hash of extracted text (length + content hashCode
     * as hex). Cheap and low-collision enough to decide whether a matrix cell
     * must be recomputed on a re-run.
     */
    public static String contentHash(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        return Integer.toHexString(text.length()) + "-" + Integer.toHexString(text.hashCode());
    }

    private String extractUrl(String urlString) {
        // T739 / §XLVIII — routed through the pluggable URL-fetch seam, which
        // applies the configured mode (SIMPLE / HEADLESS / AUTO). AUTO renders
        // JS-heavy SPAs in the browserless sidecar when the cheap HTTP path
        // extracts too little text. Guard + malformed-URL handling live in the
        // service; it never throws and returns blank content on failure.
        TurFileAttributes attributes = urlFetchService.fetch(urlString);
        return attributes == null ? null : attributes.getContent();
    }

    private TurPersonaMatchSource reaffirmAsset(TurPersonaMatchSource source) {
        if (StringUtils.isNotBlank(source.getCachedText())) {
            source.setExtractionStatus(TurPersonaSourceStatus.EXTRACTED);
            source.setExtractionError(null);
            return source;
        }
        return fail(source, "Asset text unavailable — re-upload the file");
    }

    private TurPersonaMatchSource applyText(TurPersonaMatchSource source, String text) {
        if (StringUtils.isBlank(text)) {
            return fail(source, "No text could be extracted from this source");
        }
        source.setCachedText(text);
        source.setContentHash(contentHash(text));
        source.setExtractedAt(Instant.now());
        source.setExtractionStatus(TurPersonaSourceStatus.EXTRACTED);
        source.setExtractionError(null);
        return source;
    }

    private TurPersonaMatchSource fail(TurPersonaMatchSource source, String reason) {
        source.setExtractionStatus(TurPersonaSourceStatus.FAILED);
        source.setExtractionError(reason);
        source.setExtractedAt(Instant.now());
        return source;
    }
}
