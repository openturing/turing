/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn.media;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.genai.nativeapi.gemini.TurGeminiVideoUnderstandingService;
import com.viglet.turing.genai.nativeapi.gemini.TurGeminiVideoUnderstandingService.VideoUnderstanding;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * T501 / §X.19 — the <em>index-time</em> leg of native video/audio understanding.
 * Where the T64 VIDEO/AUDIO chat slot turns an uploaded clip into a transcript on
 * demand, this turns an <strong>indexed</strong> media asset into searchable text:
 * when a site opts in ({@link TurSNSiteGenAi}'s {@code isMediaUnderstandingIndexingEnabled()})
 * and its GenAI agent resolves to a Gemini LLM, every document that references a
 * video/audio asset is run through
 * {@link TurGeminiVideoUnderstandingService#understandUrl} as it is indexed, and the
 * resulting timestamped transcript + scene description is appended to the document's
 * text field — so the clip's spoken/visual content is retrievable through the normal
 * full-text path (the {@code text} field is the one copied into Solr's {@code _text_}
 * catch-all, hence the append rather than a separate, unsearchable field).
 *
 * <p>The media source is the document's {@value #MEDIA_URL_ATTR} attribute when
 * present, else its {@code url} field — but only when it points at a media asset
 * (a video/audio file extension, or a YouTube link Gemini fetches in Google's
 * infra). Using the URL path means no Turing download: Gemini fetches the clip
 * itself, riding the same {@code understandUrl} the T64 slot uses for links.
 *
 * <p>Mirrors {@code TurSNContentFitIndexer}'s contract exactly — mutate the
 * attributes map in place, and be <strong>defensive by construction</strong>: a
 * disabled site, a non-Gemini agent, a non-media document, or any understanding
 * error is swallowed and logged so a problem can never block indexing. Disabled is
 * byte-for-byte the legacy path. Unlike the deterministic content-fit signal, this
 * spends one LLM call per media document, so it is strictly opt-in.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TurSNGeminiMediaIndexer {

    /** Optional explicit attribute carrying the media URL; falls back to {@code url}. */
    static final String MEDIA_URL_ATTR = "media_url";

    private final TurGeminiVideoUnderstandingService videoUnderstanding;

    /**
     * Runs understanding on the document's media asset and appends the transcript
     * to its text field when the site has opted in. No-op (early return) otherwise.
     * Mutates {@code attributes} in place.
     */
    public void enrich(TurSNSite turSNSite, Map<String, Object> attributes) {
        if (turSNSite == null || attributes == null) {
            return;
        }
        try {
            TurSNSiteGenAi genAi = turSNSite.getTurSNSiteGenAi();
            if (genAi == null || !genAi.isMediaUnderstandingIndexingEnabled()) {
                return;
            }
            TurLLMInstance llm = resolveLlmInstance(genAi);
            if (llm == null || !videoUnderstanding.supports(llm)) {
                log.debug("[T501] media understanding enabled on site '{}' but its agent LLM is "
                        + "not a Gemini instance — skipping", turSNSite.getName());
                return;
            }
            String mediaUrl = resolveMediaUrl(attributes);
            if (StringUtils.isBlank(mediaUrl)) {
                return;
            }
            String mimeType = mediaMimeType(mediaUrl);
            if (mimeType == null && !isYouTube(mediaUrl)) {
                // Not a recognizable media asset — leave the document untouched.
                return;
            }
            Optional<VideoUnderstanding> result = videoUnderstanding.understandUrl(llm, mediaUrl, mimeType);
            if (result.isEmpty() || StringUtils.isBlank(result.get().rawText())) {
                return;
            }
            appendToTextField(turSNSite, attributes, result.get().rawText());
            log.info("[T501] appended media understanding ({} chars) for a document on site '{}'",
                    result.get().rawText().length(), turSNSite.getName());
        } catch (Exception e) {
            // Never let an understanding problem block indexing.
            log.warn("[T501] media understanding failed on site '{}': {}",
                    turSNSite.getName(), e.getMessage());
        }
    }

    /** The GenAI agent's active LLM instance (first enabled, else first), or null. */
    private TurLLMInstance resolveLlmInstance(TurSNSiteGenAi genAi) {
        TurAIAgent agent = genAi.getTurAIAgent();
        if (agent == null || agent.getLlmInstances() == null) {
            return null;
        }
        return agent.getLlmInstances().stream()
                .filter(l -> l.getEnabled() == 1)
                .findFirst()
                .orElseGet(() -> agent.getLlmInstances().stream().findFirst().orElse(null));
    }

    /** The {@value #MEDIA_URL_ATTR} attribute when present, else the {@code url} field. */
    private String resolveMediaUrl(Map<String, Object> attributes) {
        String explicit = firstString(attributes.get(MEDIA_URL_ATTR));
        if (StringUtils.isNotBlank(explicit)) {
            return explicit;
        }
        return firstString(attributes.get(TurSNFieldName.URL));
    }

    /**
     * Append the understanding text to the document's text field (site default, else
     * {@code text}), preserving any existing body so a media doc that already carries
     * a description keeps it.
     */
    private void appendToTextField(TurSNSite turSNSite, Map<String, Object> attributes, String text) {
        String field = StringUtils.defaultIfBlank(turSNSite.getDefaultTextField(), TurSNFieldName.TEXT);
        Object existing = attributes.get(field);
        String existingText = firstString(existing);
        String merged = StringUtils.isBlank(existingText) ? text : existingText + "\n" + text;
        attributes.put(field, merged);
    }

    /** First scalar string of a value that may be a single object or an {@link Iterable}. */
    private static String firstString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && StringUtils.isNotBlank(item.toString())) {
                    return item.toString();
                }
            }
            return null;
        }
        return value.toString();
    }

    /**
     * Map a media URL's file extension to a video/audio MIME type, or {@code null}
     * when the extension is not a recognized media type. Pure and self-contained so
     * the detection is unit-testable without the SDK.
     */
    static String mediaMimeType(String url) {
        if (StringUtils.isBlank(url)) {
            return null;
        }
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return null;
        }
        String ext = path.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "mp4", "m4v" -> "video/mp4";
            case "webm" -> "video/webm";
            case "mov" -> "video/quicktime";
            case "mkv" -> "video/x-matroska";
            case "avi" -> "video/x-msvideo";
            case "mpeg", "mpg" -> "video/mpeg";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "m4a" -> "audio/mp4";
            case "aac" -> "audio/aac";
            case "ogg", "oga" -> "audio/ogg";
            case "flac" -> "audio/flac";
            default -> null;
        };
    }

    /** True for a YouTube watch / share URL, which Gemini can fetch natively. */
    static boolean isYouTube(String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("youtube.com/") || lower.contains("youtu.be/");
    }
}
