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
package com.viglet.turing.genai.nativeapi.gemini;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.genai.nativeapi.files.TurFilesApiBridge;
import com.viglet.turing.genai.nativeapi.files.TurFilesApiBridge.TurVendorFileRef;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import lombok.extern.slf4j.Slf4j;

/**
 * T501 / §X.19 — native video (and audio) understanding via Gemini. Gemini
 * ingests a clip's frames + audio track natively, so a media asset can be turned
 * into searchable text — a <b>timestamped transcript</b> plus a <b>scene/visual
 * description</b> — that the text-only retrieval stack can index and answer over.
 * A media-catalog differentiator OpenAI/Anthropic can't match.
 *
 * <p>Three source shapes, all funnelling into one {@code generateContent} call:
 * <ul>
 *   <li>a <b>YouTube / public URL</b> → a {@code fileData} {@link Part#fromUri}
 *       (Gemini fetches it in Google's infra — no Turing download);</li>
 *   <li><b>small inline bytes</b> (≤ {@value #INLINE_MAX_BYTES}) → an inline
 *       {@link Part#fromBytes} data part;</li>
 *   <li><b>large bytes</b> → mirrored once into the Gemini Files API via the
 *       T497 {@link TurFilesApiBridge} (content-addressed, 48 h retention) and
 *       referenced by its URI.</li>
 * </ul>
 *
 * <p>Strictly opt-in and <b>fail-open</b>: a non-Gemini instance, a missing
 * client, an empty source, or any SDK error yields {@link Optional#empty()} so
 * the caller (a VIDEO slot upload, T64; or an index-time SN-doc enrichment)
 * simply skips the enrichment — it is never a hard dependency.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurGeminiVideoUnderstandingService {

    static final String PLUGIN_TYPE = "gemini";
    /** A video-capable default; overridable per instance via the {@code chatModel} option. */
    static final String DEFAULT_MODEL = "gemini-2.0-flash";
    /** Above this, bytes go through the Files API rather than an inline data part. */
    static final int INLINE_MAX_BYTES = 18 * 1024 * 1024;

    /** Labels the structured prompt asks the model to emit, so we can split the answer. */
    static final String TRANSCRIPT_LABEL = "TRANSCRIPT:";
    static final String SCENES_LABEL = "SCENES:";

    private static final String DEFAULT_PROMPT = """
            Analyze this media. Respond in exactly two labeled sections and nothing else:
            TRANSCRIPT:
            <a timestamped transcript of all spoken audio, one line per segment as [mm:ss] text; \
            write "(no speech)" if there is none>
            SCENES:
            <a chronological, timestamped description of the visual scenes / on-screen content as \
            [mm:ss] description; for audio-only media write "(audio only)">
            Use the same language as the media. Be faithful — describe only what is present.""";

    private final TurNativeProviderClient nativeClient;
    private final TurFilesApiBridge filesApiBridge;
    private final TurProviderOptionsParser optionsParser;

    public TurGeminiVideoUnderstandingService(TurNativeProviderClient nativeClient,
            TurFilesApiBridge filesApiBridge, TurProviderOptionsParser optionsParser) {
        this.nativeClient = nativeClient;
        this.filesApiBridge = filesApiBridge;
        this.optionsParser = optionsParser;
    }

    /**
     * Structured result of understanding one clip.
     *
     * @param transcript       the timestamped spoken transcript (may be blank)
     * @param sceneDescription the timestamped visual/scene description (may be blank)
     * @param rawText          the model's full answer (always present on success) —
     *                         the canonical text to write to an SN doc / slot
     */
    public record VideoUnderstanding(String transcript, String sceneDescription, String rawText) {
    }

    /** True when this instance can do native video understanding (a Gemini instance). */
    public boolean supports(TurLLMInstance instance) {
        return PLUGIN_TYPE.equalsIgnoreCase(vendorPlugin(instance));
    }

    /**
     * Understand an uploaded clip. Small clips go inline; larger ones are mirrored
     * into the Gemini Files API (T497) first. Returns empty on any failure.
     */
    public Optional<VideoUnderstanding> understand(TurLLMInstance instance, byte[] data,
            String mimeType, String displayName) {
        if (data == null || data.length == 0 || !StringUtils.hasText(mimeType)) {
            return Optional.empty();
        }
        return run(instance, () -> mediaPart(instance, data, mimeType, displayName), mimeType);
    }

    /**
     * Understand a clip referenced by a URL — a YouTube link or any public media
     * URL Gemini can fetch, or a Gemini Files API URI. Returns empty on failure.
     */
    public Optional<VideoUnderstanding> understandUrl(TurLLMInstance instance, String url,
            String mimeType) {
        if (!StringUtils.hasText(url)) {
            return Optional.empty();
        }
        String type = StringUtils.hasText(mimeType) ? mimeType : "video/mp4";
        return run(instance, () -> Part.fromUri(url, type), type);
    }

    private Optional<VideoUnderstanding> run(TurLLMInstance instance,
            java.util.function.Supplier<Part> mediaPartSupplier, String mimeType) {
        if (!supports(instance)) {
            return Optional.empty();
        }
        Client client = nativeClient.gemini(instance).orElse(null);
        if (client == null) {
            return Optional.empty();
        }
        try {
            Part media = mediaPartSupplier.get();
            if (media == null) {
                return Optional.empty();
            }
            Content content = Content.fromParts(media, Part.fromText(DEFAULT_PROMPT));
            GenerateContentResponse response = client.models.generateContent(
                    resolveModel(instance), List.of(content), GenerateContentConfig.builder().build());
            String text = safeText(response);
            if (!StringUtils.hasText(text)) {
                return Optional.empty();
            }
            log.info("[Native][Gemini] video understanding produced {} chars ({})",
                    text.length(), mimeType);
            return Optional.of(parse(text));
        } catch (RuntimeException e) {
            log.warn("[Native][Gemini] video understanding failed ({}) — skipping enrichment",
                    e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Build the media {@link Part}: inline when small, else mirrored into the
     * Gemini Files API and referenced by URI. Returns {@code null} when a large
     * upload could not be mirrored (caller fails open).
     */
    private Part mediaPart(TurLLMInstance instance, byte[] data, String mimeType, String displayName) {
        if (data.length <= INLINE_MAX_BYTES) {
            return Part.fromBytes(data, mimeType);
        }
        Optional<TurVendorFileRef> ref = filesApiBridge.ensureUploaded(instance, data,
                StringUtils.hasText(displayName) ? displayName : "media", mimeType);
        return ref.map(r -> Part.fromUri(r.vendorFileId(), mimeType)).orElse(null);
    }

    /**
     * Split the labeled answer into its {@code TRANSCRIPT:} / {@code SCENES:}
     * sections. The full text is always preserved in {@link VideoUnderstanding#rawText()}
     * so an unparseable answer is never lost.
     */
    static VideoUnderstanding parse(String text) {
        String transcript = section(text, TRANSCRIPT_LABEL, SCENES_LABEL);
        String scenes = section(text, SCENES_LABEL, null);
        return new VideoUnderstanding(transcript, scenes, text);
    }

    /** Extract the text between {@code label} and {@code nextLabel} (or end), trimmed. */
    private static String section(String text, String label, String nextLabel) {
        int start = indexOfIgnoreCase(text, label);
        if (start < 0) {
            return "";
        }
        start += label.length();
        int end = nextLabel == null ? text.length() : indexOfIgnoreCase(text, nextLabel);
        if (end < start) {
            end = text.length();
        }
        return text.substring(start, end).strip();
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    String resolveModel(TurLLMInstance instance) {
        Map<String, Object> options = optionsParser.parse(instance.getProviderOptionsJson());
        String configured = firstNonBlank(optionsParser.stringValue(options, "videoModel"),
                optionsParser.stringValue(options, "chatModel"),
                optionsParser.stringValue(options, "model"),
                instance.getModelName());
        return StringUtils.hasText(configured) ? configured : DEFAULT_MODEL;
    }

    private static String safeText(GenerateContentResponse response) {
        try {
            return response.text();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private String vendorPlugin(TurLLMInstance instance) {
        if (instance == null || instance.getTurLLMVendor() == null) {
            return null;
        }
        String plugin = instance.getTurLLMVendor().getPlugin();
        if (!StringUtils.hasText(plugin)) {
            plugin = instance.getTurLLMVendor().getId();
        }
        return plugin == null ? null : plugin.toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
