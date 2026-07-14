/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.urlfetch;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurUrlFetchProperty;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * T739 / §XLVIII — the single source of the effective URL-fetch config. Merges
 * the {@code turing.url-fetch.*} env props (which <b>win</b> when set, so a
 * container can pin the mode) over the UI-editable DB Global Settings row, then
 * over the hardcoded defaults. {@link TurUrlFetchService} resolves its mode and
 * sidecar details here rather than reading settings or properties directly, so
 * the precedence lives in exactly one place — mirroring
 * {@code TurTranscriptionConfigResolver}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurUrlFetchConfigResolver {

    private final TurGlobalSettingsService globalSettingsService;
    private final TurUrlFetchProperty properties;

    public TurUrlFetchConfigResolver(TurGlobalSettingsService globalSettingsService,
            TurConfigProperties configProperties) {
        this.globalSettingsService = globalSettingsService;
        this.properties = configProperties.getUrlFetch() != null
                ? configProperties.getUrlFetch()
                : new TurUrlFetchProperty();
    }

    /** Resolve the effective config for the current request. Never returns null. */
    public TurUrlFetchConfig resolve() {
        TurUrlFetchMode mode = properties.getMode() != null
                ? properties.getMode()
                : globalSettingsService.getUrlFetchMode();

        String browserlessUrl = firstNonBlank(properties.getBrowserlessUrl(),
                globalSettingsService.getUrlFetchBrowserlessUrl());
        String browserlessToken = firstNonBlank(properties.getBrowserlessToken(),
                globalSettingsService.getUrlFetchBrowserlessToken());
        int timeoutSeconds = Math.max(1, properties.getTimeoutSeconds());
        int autoMinChars = Math.max(0, properties.getAutoMinChars());
        return new TurUrlFetchConfig(mode, browserlessUrl, browserlessToken,
                timeoutSeconds, autoMinChars);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return StringUtils.isNotBlank(preferred) ? preferred.trim()
                : StringUtils.defaultString(fallback);
    }
}
