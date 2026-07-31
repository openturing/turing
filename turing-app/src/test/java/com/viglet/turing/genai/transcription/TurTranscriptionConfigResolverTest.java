/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.transcription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurTranscriptionProperty;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * T687 / §XLII.1 — precedence tests for {@link TurTranscriptionConfigResolver}:
 * a non-blank {@code turing.transcription.*} env prop wins over the DB Global
 * Settings row; an unset env prop falls back to the DB value.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurTranscriptionConfigResolverTest {

    @Mock
    private TurGlobalSettingsService globalSettings;

    @Mock
    private TurTranscriptionKeywordSource keywordSource;

    private TurTranscriptionConfigResolver resolver(TurTranscriptionProperty props) {
        TurConfigProperties config = new TurConfigProperties();
        config.setTranscription(props);
        return new TurTranscriptionConfigResolver(globalSettings, keywordSource, config);
    }

    private void stubDbDefaults() {
        lenient().when(globalSettings.getTranscriptionStrategy())
                .thenReturn(TurTranscriptionProviderType.OPENAI);
        lenient().when(globalSettings.getTranscriptionEndpoint()).thenReturn("https://db-endpoint/v1");
        lenient().when(globalSettings.getTranscriptionModel()).thenReturn("db-model");
        lenient().when(globalSettings.getTranscriptionApiKey()).thenReturn("db-key");
        lenient().when(globalSettings.getTranscriptionMaxUploadBytes()).thenReturn(26_214_400L);
        stubDbHintDefaults();
    }

    /** T847 — hints are resolved on every call, so every path needs them stubbed. */
    private void stubDbHintDefaults() {
        lenient().when(globalSettings.getTranscriptionPrompt()).thenReturn("");
        lenient().when(globalSettings.getTranscriptionKeywords()).thenReturn("");
        lenient().when(globalSettings.isTranscriptionKeywordsFromThesaurus()).thenReturn(false);
    }

    @Test
    void fallsBackToDbWhenEnvPropsUnset() {
        stubDbDefaults();
        TurTranscriptionConfig config = resolver(new TurTranscriptionProperty()).resolve();

        assertThat(config.type()).isEqualTo(TurTranscriptionProviderType.OPENAI);
        assertThat(config.endpoint()).isEqualTo("https://db-endpoint/v1");
        assertThat(config.model()).isEqualTo("db-model");
        assertThat(config.apiKey()).isEqualTo("db-key");
        assertThat(config.maxUploadBytes()).isEqualTo(26_214_400L);
    }

    @Test
    void envPropsOverrideDb() {
        stubDbDefaults();
        TurTranscriptionProperty props = new TurTranscriptionProperty();
        props.setType(TurTranscriptionProviderType.OPENAI_COMPATIBLE);
        props.setEndpoint("http://faster-whisper:8000/v1");
        props.setModel("Systran/faster-whisper-large-v3");
        props.setApiKey("env-key");
        props.setMaxUploadBytes(500_000_000L);

        TurTranscriptionConfig config = resolver(props).resolve();

        assertThat(config.type()).isEqualTo(TurTranscriptionProviderType.OPENAI_COMPATIBLE);
        assertThat(config.endpoint()).isEqualTo("http://faster-whisper:8000/v1");
        assertThat(config.model()).isEqualTo("Systran/faster-whisper-large-v3");
        assertThat(config.apiKey()).isEqualTo("env-key");
        assertThat(config.maxUploadBytes()).isEqualTo(500_000_000L);
    }

    @Test
    void blankMaxUploadBytesEnvFallsBackToDb() {
        when(globalSettings.getTranscriptionStrategy()).thenReturn(TurTranscriptionProviderType.OPENAI);
        when(globalSettings.getTranscriptionEndpoint()).thenReturn("");
        when(globalSettings.getTranscriptionModel()).thenReturn("");
        when(globalSettings.getTranscriptionApiKey()).thenReturn("");
        when(globalSettings.getTranscriptionMaxUploadBytes()).thenReturn(26_214_400L);
        stubDbHintDefaults();

        TurTranscriptionProperty props = new TurTranscriptionProperty();
        props.setMaxUploadBytes(0L); // non-positive → ignore, use DB

        TurTranscriptionConfig config = resolver(props).resolve();

        assertThat(config.maxUploadBytes()).isEqualTo(26_214_400L);
        assertThat(config.endpoint()).isEmpty();
    }

    // ---- T847 / §LXIII.2 — context hints -------------------------------

    /** An install that configured nothing gets no hints at all — never a fabricated one. */
    @Test
    void noHintsConfiguredYieldsEmptyHints() {
        stubDbDefaults();
        assertThat(resolver(new TurTranscriptionProperty()).resolve().hints().isEmpty()).isTrue();
    }

    @Test
    void globalSettingsKeywordsAreSplitOnCommas() {
        stubDbDefaults();
        when(globalSettings.getTranscriptionPrompt()).thenReturn("Support call about search.");
        when(globalSettings.getTranscriptionKeywords()).thenReturn("Turing, Solr ,, Lucene");

        TurTranscriptionHints hints = resolver(new TurTranscriptionProperty()).resolve().hints();

        assertThat(hints.prompt()).isEqualTo("Support call about search.");
        assertThat(hints.keywords()).containsExactly("Turing", "Solr", "Lucene");
    }

    @Test
    void envPropKeywordsWinOverGlobalSettings() {
        stubDbDefaults();
        lenient().when(globalSettings.getTranscriptionKeywords()).thenReturn("db-term");
        TurTranscriptionProperty props = new TurTranscriptionProperty();
        props.setKeywords(java.util.List.of("env-term"));

        assertThat(resolver(props).resolve().hints().keywords()).containsExactly("env-term");
    }

    /** The Thesaurus source is opt-in — off by default, never queried. */
    @Test
    void thesaurusSourceIsNotQueriedWhenDisabled() {
        stubDbDefaults();
        resolver(new TurTranscriptionProperty()).resolve();
        org.mockito.Mockito.verifyNoInteractions(keywordSource);
    }

    /** Opt-in on: explicit keywords come first, the Thesaurus tops the rest up. */
    @Test
    void thesaurusTopsUpAfterTheOperatorsOwnKeywords() {
        stubDbDefaults();
        when(globalSettings.getTranscriptionKeywords()).thenReturn("Turing");
        when(globalSettings.isTranscriptionKeywordsFromThesaurus()).thenReturn(true);
        when(keywordSource.keywords(TurTranscriptionHints.MAX_KEYWORDS - 1))
                .thenReturn(java.util.List.of("Semantic Navigation", "Microthesaurus"));

        assertThat(resolver(new TurTranscriptionProperty()).resolve().hints().keywords())
                .containsExactly("Turing", "Semantic Navigation", "Microthesaurus");
    }
}
