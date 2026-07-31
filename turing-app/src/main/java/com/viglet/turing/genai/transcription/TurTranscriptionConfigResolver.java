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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurTranscriptionProperty;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * T687 / §XLII.1 — the single source of the effective transcription config.
 * Merges the {@code turing.transcription.*} env props (which <b>win</b> when
 * set, so a container can pin the backend) over the UI-editable DB Global
 * Settings row, then over the hardcoded defaults. Every transcription
 * provider/orchestrator resolves its backend and connection details here rather
 * than reading the settings or properties directly, so the precedence lives in
 * exactly one place.
 *
 * <p>T847 / §LXIII.2 — it also resolves the global context hints
 * ({@code prompt} + {@code keywords}), including the opt-in Thesaurus top-up, so
 * a caller never has to assemble a vocabulary itself.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurTranscriptionConfigResolver {

    private final TurGlobalSettingsService globalSettingsService;
    private final TurTranscriptionKeywordSource keywordSource;
    private final TurTranscriptionProperty properties;

    public TurTranscriptionConfigResolver(TurGlobalSettingsService globalSettingsService,
            TurTranscriptionKeywordSource keywordSource,
            TurConfigProperties configProperties) {
        this.globalSettingsService = globalSettingsService;
        this.keywordSource = keywordSource;
        this.properties = configProperties.getTranscription() != null
                ? configProperties.getTranscription()
                : new TurTranscriptionProperty();
    }

    /** Resolve the effective config for the current request. Never returns null. */
    public TurTranscriptionConfig resolve() {
        TurTranscriptionProviderType type = properties.getType() != null
                ? properties.getType()
                : globalSettingsService.getTranscriptionStrategy();

        String endpoint = firstNonBlank(properties.getEndpoint(),
                globalSettingsService.getTranscriptionEndpoint());
        String model = firstNonBlank(properties.getModel(),
                globalSettingsService.getTranscriptionModel());
        String apiKey = firstNonBlank(properties.getApiKey(),
                globalSettingsService.getTranscriptionApiKey());
        long maxUploadBytes = properties.getMaxUploadBytes() != null && properties.getMaxUploadBytes() > 0
                ? properties.getMaxUploadBytes()
                : globalSettingsService.getTranscriptionMaxUploadBytes();
        return new TurTranscriptionConfig(type, endpoint, model, apiKey, maxUploadBytes,
                resolveHints());
    }

    /**
     * T847 — the globally configured context: an explicitly configured prompt +
     * keyword list, optionally topped up from the Thesaurus (Block AQ) when that
     * opt-in is on. The explicit list always comes first, so an operator's own
     * vocabulary is never crowded out by the automatic source once the
     * {@link TurTranscriptionHints#MAX_KEYWORDS} cap bites.
     */
    private TurTranscriptionHints resolveHints() {
        String prompt = firstNonBlank(properties.getPrompt(),
                globalSettingsService.getTranscriptionPrompt());

        List<String> keywords = new ArrayList<>(configuredKeywords());
        if (keywordsFromThesaurus() && keywords.size() < TurTranscriptionHints.MAX_KEYWORDS) {
            keywords.addAll(keywordSource.keywords(
                    TurTranscriptionHints.MAX_KEYWORDS - keywords.size()));
        }
        return new TurTranscriptionHints(prompt, keywords);
    }

    /** Env-prop list wins whole; otherwise the comma-separated Global Settings value. */
    private List<String> configuredKeywords() {
        List<String> fromProps = properties.getKeywords();
        if (fromProps != null && !fromProps.isEmpty()) {
            return fromProps;
        }
        return splitKeywords(globalSettingsService.getTranscriptionKeywords());
    }

    private boolean keywordsFromThesaurus() {
        return properties.isKeywordsFromThesaurus()
                || globalSettingsService.isTranscriptionKeywordsFromThesaurus();
    }

    /** Comma/newline-separated operator input → a keyword list (blanks dropped downstream). */
    static List<String> splitKeywords(String raw) {
        if (StringUtils.isBlank(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,\\r\\n]"))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .toList();
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return StringUtils.isNotBlank(preferred) ? preferred.trim()
                : StringUtils.defaultString(fallback);
    }
}
