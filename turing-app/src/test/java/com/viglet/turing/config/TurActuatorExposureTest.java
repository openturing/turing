/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * T649 / §XXXVII.11 — regression guard: the shipped actuator exposure must NOT
 * be the wildcard {@code "*"} (which published /actuator/env + /heapdump, leaking
 * the crypto key, datasource creds and provider keys). Only health/info/prometheus
 * should be exposed over HTTP.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurActuatorExposureTest {

    @Test
    void actuatorExposureIsNotWildcard() throws Exception {
        String yaml;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.yaml")) {
            assertThat(in).as("application.yaml on the classpath").isNotNull();
            yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // The active (non-comment) include line must be the restricted allowlist.
        assertThat(yaml).contains("include: \"health,info,prometheus\"");
        assertThat(yaml).doesNotContain("include: \"*\"");
    }
}
