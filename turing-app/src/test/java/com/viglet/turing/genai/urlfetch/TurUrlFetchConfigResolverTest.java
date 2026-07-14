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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurUrlFetchProperty;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * T739 / §XLVIII — precedence tests for {@link TurUrlFetchConfigResolver}:
 * {@code turing.url-fetch.*} env props win over the DB Global Settings row, which
 * wins over defaults.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurUrlFetchConfigResolverTest {

    @Mock
    private TurGlobalSettingsService globalSettingsService;

    private TurUrlFetchProperty props;
    private TurUrlFetchConfigResolver resolver;

    @BeforeEach
    void setUp() {
        props = new TurUrlFetchProperty();
        TurConfigProperties configProperties = new TurConfigProperties();
        configProperties.setUrlFetch(props);
        resolver = new TurUrlFetchConfigResolver(globalSettingsService, configProperties);
    }

    @Test
    void envModeWinsOverDb() {
        props.setMode(TurUrlFetchMode.HEADLESS);
        // DB would say AUTO, but the env prop must win.
        lenient().when(globalSettingsService.getUrlFetchMode()).thenReturn(TurUrlFetchMode.AUTO);

        assertThat(resolver.resolve().mode()).isEqualTo(TurUrlFetchMode.HEADLESS);
    }

    @Test
    void fallsBackToDbModeWhenEnvUnset() {
        props.setMode(null);
        when(globalSettingsService.getUrlFetchMode()).thenReturn(TurUrlFetchMode.AUTO);

        assertThat(resolver.resolve().mode()).isEqualTo(TurUrlFetchMode.AUTO);
    }

    @Test
    void envBrowserlessUrlWinsAndBlankFallsBackToDb() {
        // env URL set -> wins
        props.setBrowserlessUrl("http://env-browserless:3000");
        lenient().when(globalSettingsService.getUrlFetchMode()).thenReturn(TurUrlFetchMode.AUTO);
        lenient().when(globalSettingsService.getUrlFetchBrowserlessUrl())
                .thenReturn("http://db-browserless:3000");
        lenient().when(globalSettingsService.getUrlFetchBrowserlessToken()).thenReturn("");

        assertThat(resolver.resolve().browserlessUrl()).isEqualTo("http://env-browserless:3000");

        // env URL blank -> DB value used
        props.setBrowserlessUrl("");
        assertThat(resolver.resolve().browserlessUrl()).isEqualTo("http://db-browserless:3000");
    }

    @Test
    void clampsTimeoutAndAutoMinChars() {
        props.setMode(TurUrlFetchMode.SIMPLE);
        props.setTimeoutSeconds(0);
        props.setAutoMinChars(-5);
        lenient().when(globalSettingsService.getUrlFetchBrowserlessUrl()).thenReturn("");
        lenient().when(globalSettingsService.getUrlFetchBrowserlessToken()).thenReturn("");

        TurUrlFetchConfig config = resolver.resolve();
        assertThat(config.timeoutSeconds()).isGreaterThanOrEqualTo(1);
        assertThat(config.autoMinChars()).isGreaterThanOrEqualTo(0);
    }
}
