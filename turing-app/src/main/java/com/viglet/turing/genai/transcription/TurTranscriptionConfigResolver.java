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
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurTranscriptionConfigResolver {

    private final TurGlobalSettingsService globalSettingsService;
    private final TurTranscriptionProperty properties;

    public TurTranscriptionConfigResolver(TurGlobalSettingsService globalSettingsService,
            TurConfigProperties configProperties) {
        this.globalSettingsService = globalSettingsService;
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
        return new TurTranscriptionConfig(type, endpoint, model, apiKey, maxUploadBytes);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return StringUtils.isNotBlank(preferred) ? preferred.trim()
                : StringUtils.defaultString(fallback);
    }
}
