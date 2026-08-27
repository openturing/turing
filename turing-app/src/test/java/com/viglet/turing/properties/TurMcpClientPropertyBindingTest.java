/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;

/**
 * T1037 / §CVI.2 — the {@code turing.mcp-client} block in {@code application.yaml}
 * is example configuration, and example configuration that does not bind is
 * worse than none: an operator copies it, sees no effect, and concludes the knob
 * does not exist.
 *
 * <p>So this binds the real shipped {@code application.yaml} into
 * {@link TurMcpClientProperty} and asserts the values that arrive — which also
 * pins the relaxed-binding spelling ({@code request-timeout} →
 * {@code requestTimeout}) and the duration suffix ({@code 10s}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurMcpClientPropertyBindingTest {

    private TurMcpClientProperty boundFromApplicationYaml() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));
        MutablePropertySources propertySources = new MutablePropertySources();
        sources.forEach(propertySources::addLast);
        return new Binder(ConfigurationPropertySources.from(propertySources))
                .bind("turing.mcp-client", TurMcpClientProperty.class)
                .orElseThrow(() -> new AssertionError(
                        "application.yaml declares no turing.mcp-client block"));
    }

    @Test
    @DisplayName("The shipped application.yaml block binds, so an operator's copy of it works")
    void shippedBlockBinds() throws Exception {
        TurMcpClientProperty bound = boundFromApplicationYaml();

        assertThat(bound.getRequestTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(bound.getFailureBackoff()).isEqualTo(Duration.ofSeconds(60));
        assertThat(bound.isBlockPrivateUrls()).isFalse();
        assertThat(bound.getAllowedStdioCommands()).isEmpty();
    }

    @Test
    @DisplayName("The block states the code defaults, so writing it changes no behaviour")
    void blockMatchesTheCodeDefaults() throws Exception {
        TurMcpClientProperty bound = boundFromApplicationYaml();
        var defaults = new TurMcpClientProperty();

        assertThat(bound.getRequestTimeout()).isEqualTo(defaults.getRequestTimeout());
        assertThat(bound.getFailureBackoff()).isEqualTo(defaults.getFailureBackoff());
        assertThat(bound.isBlockPrivateUrls()).isEqualTo(defaults.isBlockPrivateUrls());
        assertThat(bound.getAllowedStdioCommands()).isEqualTo(defaults.getAllowedStdioCommands());
    }

    @Test
    @DisplayName("A deployment can turn the backoff off, and the stdio allowlist on")
    void overridesBindAsDocumented() {
        var source = new org.springframework.core.env.MapPropertySource("override", java.util.Map.of(
                "turing.mcp-client.request-timeout", "2500ms",
                "turing.mcp-client.failure-backoff", "0s",
                "turing.mcp-client.block-private-urls", "true",
                "turing.mcp-client.allowed-stdio-commands", "npx,uvx"));
        MutablePropertySources propertySources = new MutablePropertySources();
        propertySources.addLast(source);

        TurMcpClientProperty bound = new Binder(
                ConfigurationPropertySources.from(propertySources))
                .bind("turing.mcp-client", TurMcpClientProperty.class)
                .orElseThrow(() -> new AssertionError("the overrides did not bind"));

        assertThat(bound.getRequestTimeout()).isEqualTo(Duration.ofMillis(2500));
        assertThat(bound.getFailureBackoff()).isZero();
        assertThat(bound.isBlockPrivateUrls()).isTrue();
        assertThat(bound.getAllowedStdioCommands()).containsExactly("npx", "uvx");
    }
}
