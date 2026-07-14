/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona;

import java.time.Instant;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.commons.file.TurFileAttributes;
import com.viglet.turing.genai.urlfetch.TurUrlFetchService;
import com.viglet.turing.persistence.model.persona.TurPersonaSource;
import com.viglet.turing.persistence.model.persona.TurPersonaSourceStatus;
import com.viglet.turing.utils.TurFileUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the cached text of a {@link TurPersonaSource} from its underlying
 * reference (Block AA / §XXVI.2). Pure orchestration over already-present
 * extraction paths — the SSRF-guarded URL fetch, Tika document parsing, and the
 * SN-document resolver — leaving persistence to the controller. Never throws on
 * a bad source: failures are recorded on the row as {@code FAILED} so the admin
 * can fix the reference and re-extract.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurPersonaSourceService {

    private final TurPersonaSnDocResolver snDocResolver;
    private final TurUrlFetchService urlFetchService;

    public TurPersonaSourceService(TurPersonaSnDocResolver snDocResolver,
            TurUrlFetchService urlFetchService) {
        this.snDocResolver = snDocResolver;
        this.urlFetchService = urlFetchService;
    }

    /**
     * (Re-)extract text for an SN_DOC or URL source. ASSET sources cannot be
     * re-fetched (the binary is not retained) — their text is captured once via
     * {@link #extractFromUpload}; this keeps the existing text if present.
     */
    public TurPersonaSource extract(TurPersonaSource source) {
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
    public TurPersonaSource extractFromUpload(TurPersonaSource source, MultipartFile file) {
        try {
            TurFileAttributes attributes = TurFileUtils.documentToText(file);
            return applyText(source, attributes == null ? null : attributes.getContent());
        } catch (RuntimeException e) {
            log.warn("[PersonaSource] asset extraction failed for {}: {}",
                    source.getRef(), e.getMessage());
            return fail(source, "Could not read uploaded file");
        }
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

    private TurPersonaSource reaffirmAsset(TurPersonaSource source) {
        if (StringUtils.isNotBlank(source.getCachedText())) {
            source.setExtractionStatus(TurPersonaSourceStatus.EXTRACTED);
            source.setExtractionError(null);
            return source;
        }
        return fail(source, "Asset text unavailable — re-upload the file");
    }

    private TurPersonaSource applyText(TurPersonaSource source, String text) {
        if (StringUtils.isBlank(text)) {
            return fail(source, "No text could be extracted from this source");
        }
        source.setCachedText(text);
        source.setExtractedAt(Instant.now());
        source.setExtractionStatus(TurPersonaSourceStatus.EXTRACTED);
        source.setExtractionError(null);
        return source;
    }

    private TurPersonaSource fail(TurPersonaSource source, String reason) {
        source.setExtractionStatus(TurPersonaSourceStatus.FAILED);
        source.setExtractionError(reason);
        source.setExtractedAt(Instant.now());
        return source;
    }
}
