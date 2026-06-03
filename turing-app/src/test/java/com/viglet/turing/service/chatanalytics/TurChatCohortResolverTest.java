/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatanalytics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Pure-function unit tests for the T74 cohort classifiers
 * ({@link TurChatCohortResolver#classifyDeviceType(String)} and
 * {@link TurChatCohortResolver#parseLocale(String)}). The request-bound
 * {@code resolveFromCurrentRequest()} reads {@code RequestContextHolder} and
 * is exercised by the chat ITs; these tests pin the header-parsing contract
 * without a Spring context.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurChatCohortResolverTest {

    @Test
    void classifyDeviceType_iPhoneIsMobile() {
        String ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 "
                + "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
        assertThat(TurChatCohortResolver.classifyDeviceType(ua)).isEqualTo("mobile");
    }

    @Test
    void classifyDeviceType_androidPhoneIsMobile() {
        String ua = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/120.0.0.0 Mobile Safari/537.36";
        assertThat(TurChatCohortResolver.classifyDeviceType(ua)).isEqualTo("mobile");
    }

    @Test
    void classifyDeviceType_iPadIsTablet() {
        // iPad UAs also carry "Mobile" — tablet detection must win over mobile.
        String ua = "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 "
                + "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
        assertThat(TurChatCohortResolver.classifyDeviceType(ua)).isEqualTo("tablet");
    }

    @Test
    void classifyDeviceType_androidTabletIsTablet() {
        // Android without "Mobile" token is a tablet.
        String ua = "Mozilla/5.0 (Linux; Android 13; SM-X710) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/120.0.0.0 Safari/537.36";
        assertThat(TurChatCohortResolver.classifyDeviceType(ua)).isEqualTo("tablet");
    }

    @Test
    void classifyDeviceType_windowsDesktopIsDesktop() {
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/120.0.0.0 Safari/537.36";
        assertThat(TurChatCohortResolver.classifyDeviceType(ua)).isEqualTo("desktop");
    }

    @Test
    void classifyDeviceType_macDesktopIsDesktop() {
        String ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 "
                + "(KHTML, like Gecko) Version/17.0 Safari/605.1.15";
        assertThat(TurChatCohortResolver.classifyDeviceType(ua)).isEqualTo("desktop");
    }

    @Test
    void classifyDeviceType_googlebotIsBot() {
        // Googlebot spoofs an Android phone UA but must classify as bot first.
        String ua = "Mozilla/5.0 (Linux; Android 6.0.1; Nexus 5X Build/MMB29P) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 "
                + "(compatible; Googlebot/2.1; +http://www.google.com/bot.html)";
        assertThat(TurChatCohortResolver.classifyDeviceType(ua)).isEqualTo("bot");
    }

    @Test
    void classifyDeviceType_curlIsBot() {
        assertThat(TurChatCohortResolver.classifyDeviceType("curl/8.4.0")).isEqualTo("bot");
    }

    @Test
    void classifyDeviceType_nullOrBlankIsUnknown() {
        assertThat(TurChatCohortResolver.classifyDeviceType(null)).isEqualTo("unknown");
        assertThat(TurChatCohortResolver.classifyDeviceType("")).isEqualTo("unknown");
        assertThat(TurChatCohortResolver.classifyDeviceType("   ")).isEqualTo("unknown");
    }

    @Test
    void parseLocale_returnsPrimaryTagStrippingWeights() {
        assertThat(TurChatCohortResolver.parseLocale("pt-BR,pt;q=0.9,en;q=0.8")).isEqualTo("pt-BR");
    }

    @Test
    void parseLocale_singleTagPassesThrough() {
        assertThat(TurChatCohortResolver.parseLocale("en-US")).isEqualTo("en-US");
    }

    @Test
    void parseLocale_stripsWeightOnSingleTag() {
        assertThat(TurChatCohortResolver.parseLocale("de;q=0.7")).isEqualTo("de");
    }

    @Test
    void parseLocale_nullBlankOrWildcardIsNull() {
        assertThat(TurChatCohortResolver.parseLocale(null)).isNull();
        assertThat(TurChatCohortResolver.parseLocale("")).isNull();
        assertThat(TurChatCohortResolver.parseLocale("   ")).isNull();
        assertThat(TurChatCohortResolver.parseLocale("*")).isNull();
    }

    @Test
    void resolveFromCurrentRequest_offRequestThreadReturnsEmptyCohort() {
        // No ServletRequestAttributes bound to this test thread → empty cohort,
        // never throws (the agent.invoke child-session path relies on this).
        TurChatCohortResolver.Cohort cohort = new TurChatCohortResolver().resolveFromCurrentRequest();
        assertThat(cohort.locale()).isNull();
        assertThat(cohort.timezone()).isNull();
        assertThat(cohort.deviceType()).isNull();
    }
}
