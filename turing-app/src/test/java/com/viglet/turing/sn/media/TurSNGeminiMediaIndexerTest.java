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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.genai.nativeapi.gemini.TurGeminiVideoUnderstandingService;
import com.viglet.turing.genai.nativeapi.gemini.TurGeminiVideoUnderstandingService.VideoUnderstanding;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;

/**
 * T501 / §X.19 — unit coverage for the index-time media-understanding enricher:
 * the opt-in gate, the Gemini-only gate, media detection, the transcript append,
 * and fail-open behavior.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurSNGeminiMediaIndexerTest {

    @Mock
    private TurGeminiVideoUnderstandingService videoUnderstanding;

    @InjectMocks
    private TurSNGeminiMediaIndexer indexer;

    private TurLLMInstance llm;
    private TurSNSite site;

    @BeforeEach
    void setUp() {
        llm = new TurLLMInstance();
        llm.setId("gemini-1");
        llm.setEnabled(1);

        TurAIAgent agent = new TurAIAgent();
        agent.setLlmInstances(new java.util.LinkedHashSet<>(List.of(llm)));

        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setMediaUnderstandingIndexingEnabled(true);
        genAi.setTurAIAgent(agent);

        site = new TurSNSite();
        site.setName("media-site");
        site.setTurSNSiteGenAi(genAi);
    }

    private static VideoUnderstanding understanding(String raw) {
        return new VideoUnderstanding("[00:00] hello", "[00:00] a desk", raw);
    }

    @Test
    void appendsTranscriptToTextFieldForMediaUrl() {
        when(videoUnderstanding.supports(llm)).thenReturn(true);
        when(videoUnderstanding.understandUrl(eq(llm), eq("https://cdn.example.com/clip.mp4"), eq("video/mp4")))
                .thenReturn(Optional.of(understanding("[00:00] transcript text")));

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.URL, "https://cdn.example.com/clip.mp4");

        indexer.enrich(site, attributes);

        assertThat(attributes.get(TurSNFieldName.TEXT)).isEqualTo("[00:00] transcript text");
    }

    @Test
    void preservesExistingBodyAndAppends() {
        when(videoUnderstanding.supports(llm)).thenReturn(true);
        when(videoUnderstanding.understandUrl(any(), any(), any()))
                .thenReturn(Optional.of(understanding("transcript")));

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.URL, "https://cdn.example.com/clip.mp4");
        attributes.put(TurSNFieldName.TEXT, "authored description");

        indexer.enrich(site, attributes);

        assertThat(attributes.get(TurSNFieldName.TEXT)).isEqualTo("authored description\ntranscript");
    }

    @Test
    void usesSiteDefaultTextFieldWhenConfigured() {
        site.setDefaultTextField("body");
        when(videoUnderstanding.supports(llm)).thenReturn(true);
        when(videoUnderstanding.understandUrl(any(), any(), any()))
                .thenReturn(Optional.of(understanding("transcript")));

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.URL, "https://cdn.example.com/clip.mp4");

        indexer.enrich(site, attributes);

        assertThat(attributes.get("body")).isEqualTo("transcript");
        assertThat(attributes).doesNotContainKey(TurSNFieldName.TEXT);
    }

    @Test
    void mediaUrlAttributeOverridesUrlField() {
        when(videoUnderstanding.supports(llm)).thenReturn(true);
        when(videoUnderstanding.understandUrl(eq(llm), eq("https://cdn.example.com/audio.mp3"), eq("audio/mpeg")))
                .thenReturn(Optional.of(understanding("audio transcript")));

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.URL, "https://example.com/page.html");
        attributes.put(TurSNGeminiMediaIndexer.MEDIA_URL_ATTR, "https://cdn.example.com/audio.mp3");

        indexer.enrich(site, attributes);

        assertThat(attributes.get(TurSNFieldName.TEXT)).isEqualTo("audio transcript");
    }

    @Test
    void youTubeUrlIsUnderstoodWithNullMime() {
        when(videoUnderstanding.supports(llm)).thenReturn(true);
        when(videoUnderstanding.understandUrl(eq(llm), eq("https://youtu.be/abc123"), isNull()))
                .thenReturn(Optional.of(understanding("yt transcript")));

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.URL, "https://youtu.be/abc123");

        indexer.enrich(site, attributes);

        assertThat(attributes.get(TurSNFieldName.TEXT)).isEqualTo("yt transcript");
    }

    @Test
    void disabledFlagIsNoOp() {
        site.getTurSNSiteGenAi().setMediaUnderstandingIndexingEnabled(false);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.URL, "https://cdn.example.com/clip.mp4");

        indexer.enrich(site, attributes);

        assertThat(attributes).doesNotContainKey(TurSNFieldName.TEXT);
        verify(videoUnderstanding, never()).understandUrl(any(), any(), any());
    }

    @Test
    void nonGeminiInstanceIsNoOp() {
        when(videoUnderstanding.supports(llm)).thenReturn(false);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.URL, "https://cdn.example.com/clip.mp4");

        indexer.enrich(site, attributes);

        assertThat(attributes).doesNotContainKey(TurSNFieldName.TEXT);
        verify(videoUnderstanding, never()).understandUrl(any(), any(), any());
    }

    @Test
    void nonMediaUrlIsNoOp() {
        when(videoUnderstanding.supports(llm)).thenReturn(true);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.URL, "https://example.com/article.html");

        indexer.enrich(site, attributes);

        assertThat(attributes).doesNotContainKey(TurSNFieldName.TEXT);
        verify(videoUnderstanding, never()).understandUrl(any(), any(), any());
    }

    @Test
    void emptyUnderstandingLeavesDocumentUntouched() {
        when(videoUnderstanding.supports(llm)).thenReturn(true);
        when(videoUnderstanding.understandUrl(any(), any(), any())).thenReturn(Optional.empty());

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.URL, "https://cdn.example.com/clip.mp4");

        indexer.enrich(site, attributes);

        assertThat(attributes).doesNotContainKey(TurSNFieldName.TEXT);
    }

    @Test
    void understandingErrorIsSwallowed() {
        when(videoUnderstanding.supports(llm)).thenReturn(true);
        when(videoUnderstanding.understandUrl(any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.URL, "https://cdn.example.com/clip.mp4");

        // Must not throw — fail-open so a problem never blocks indexing.
        indexer.enrich(site, attributes);

        assertThat(attributes).doesNotContainKey(TurSNFieldName.TEXT);
    }

    @Test
    void noGenAiOrNoAttributesIsNoOp() {
        site.setTurSNSiteGenAi(null);
        Map<String, Object> attributes = new HashMap<>();
        indexer.enrich(site, attributes);
        indexer.enrich(null, attributes);
        indexer.enrich(site, null);
        assertThat(attributes).isEmpty();
        verify(videoUnderstanding, never()).understandUrl(any(), any(), any());
    }

    @Test
    void noEnabledLlmFallsBackToFirstThenSkipsWhenNotGemini() {
        // When the only instance is disabled, it is still considered (first), but a
        // non-Gemini verdict skips. Here flip it disabled and keep supports=false.
        llm.setEnabled(0);
        lenient().when(videoUnderstanding.supports(llm)).thenReturn(false);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.URL, "https://cdn.example.com/clip.mp4");

        indexer.enrich(site, attributes);

        assertThat(attributes).doesNotContainKey(TurSNFieldName.TEXT);
    }

    @Test
    void mediaMimeTypeMapsKnownExtensionsAndIgnoresQuery() {
        assertThat(TurSNGeminiMediaIndexer.mediaMimeType("a/clip.mp4")).isEqualTo("video/mp4");
        assertThat(TurSNGeminiMediaIndexer.mediaMimeType("a/clip.webm")).isEqualTo("video/webm");
        assertThat(TurSNGeminiMediaIndexer.mediaMimeType("a/voice.mp3")).isEqualTo("audio/mpeg");
        assertThat(TurSNGeminiMediaIndexer.mediaMimeType("a/voice.wav?token=x")).isEqualTo("audio/wav");
        assertThat(TurSNGeminiMediaIndexer.mediaMimeType("a/page.html")).isNull();
        assertThat(TurSNGeminiMediaIndexer.mediaMimeType("a/noext")).isNull();
        assertThat(TurSNGeminiMediaIndexer.mediaMimeType("")).isNull();
    }

    @Test
    void isYouTubeRecognizesBothHosts() {
        assertThat(TurSNGeminiMediaIndexer.isYouTube("https://www.youtube.com/watch?v=x")).isTrue();
        assertThat(TurSNGeminiMediaIndexer.isYouTube("https://youtu.be/x")).isTrue();
        assertThat(TurSNGeminiMediaIndexer.isYouTube("https://example.com/x")).isFalse();
        assertThat(TurSNGeminiMediaIndexer.isYouTube(null)).isFalse();
    }
}
