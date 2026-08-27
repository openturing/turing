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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import tools.jackson.databind.json.JsonMapper;

/**
 * T1038 / §CVI.3 — the guard that keeps the configuration file honest.
 *
 * <p>Six of the ten {@code turing.*} {@code @ConfigurationProperties} prefixes had
 * never appeared in {@code application.yaml} — 52 knobs, including 15 of
 * guardrail configuration and 13 of abuse control, discoverable only by reading
 * Java. A knob nobody can find is a knob that does not exist, so this fails when
 * a declared prefix is missing from the file, catching the next one at the commit
 * that adds it.
 *
 * <p>The second half matters as much: it proves the blocks are <em>inert</em>. What
 * the file says must bind to exactly what a fresh property object already holds,
 * or documenting the defaults would have quietly changed them.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurConfigurationSurfaceTest {

    private static final String SCAN_BASE = "com.viglet.turing";
    private static final String PREFIX_ROOT = "turing.";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** Every {@code turing.*} prefix the codebase declares, mapped to its class. */
    private static Map<String, Class<?>> declaredPrefixes() throws Exception {
        var scanner = new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(
                false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ConfigurationProperties.class));
        Map<String, Class<?>> prefixes = new LinkedHashMap<>();
        for (var candidate : scanner.findCandidateComponents(SCAN_BASE)) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            ConfigurationProperties annotation = type.getAnnotation(ConfigurationProperties.class);
            if (annotation == null) {
                continue;
            }
            String prefix = annotation.prefix().isEmpty() ? annotation.value() : annotation.prefix();
            if (prefix.startsWith(PREFIX_ROOT)) {
                prefixes.put(prefix, type);
            }
        }
        return prefixes;
    }

    private static MutablePropertySources applicationYaml() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));
        MutablePropertySources propertySources = new MutablePropertySources();
        sources.forEach(propertySources::addLast);
        return propertySources;
    }

    /** Every property name the shipped yaml declares. */
    private static List<String> declaredKeys(MutablePropertySources propertySources) {
        List<String> keys = new ArrayList<>();
        for (PropertySource<?> source : propertySources) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                keys.addAll(List.of(enumerable.getPropertyNames()));
            }
        }
        return keys;
    }

    @Test
    @DisplayName("Every declared turing.* prefix appears in application.yaml")
    void everyPrefixIsDocumented() throws Exception {
        Map<String, Class<?>> prefixes = declaredPrefixes();
        assertThat(prefixes).as("the scan found no @ConfigurationProperties at all")
                .isNotEmpty();
        List<String> keys = declaredKeys(applicationYaml());

        var undocumented = new TreeSet<String>();
        prefixes.forEach((prefix, type) -> {
            boolean present = keys.stream().anyMatch(key -> key.startsWith(prefix + "."));
            if (!present) {
                undocumented.add("%s (%s)".formatted(prefix, type.getSimpleName()));
            }
        });

        assertThat(undocumented)
                .as("these prefixes are declared in Java but absent from application.yaml, "
                        + "so an operator cannot discover what they tune: %s", undocumented)
                .isEmpty();
    }

    /**
     * The blocks T1037 and T1038 added to document a prefix, as opposed to the
     * ones that carry real policy.
     *
     * <p>{@code turing.resilience} is deliberately not here: its yaml block is
     * where the circuit-breaker, retry and time-limiter policy actually lives, and
     * the class defaults are nulls the file fills in. A prefix configuring rather
     * than documenting is legitimate — asserting inertness across every prefix was
     * simply the wrong rule, and this list is the honest scope of the claim.
     */
    private static final java.util.Set<String> DOCUMENTED_PREFIXES = java.util.Set.of(
            "turing.mcp-client", "turing.abuse", "turing.safety.guardrail", "turing.gateway",
            "turing.retrieval", "turing.migration", "turing.viglet-ai", "turing.prompt.capture");

    @Test
    @DisplayName("What the documented blocks state binds to exactly the code defaults — they are inert")
    void documentedBlocksAreInert() throws Exception {
        Map<String, Class<?>> prefixes = declaredPrefixes();
        var binder = new Binder(ConfigurationPropertySources.from(applicationYaml()));

        var drifted = new ArrayList<String>();
        for (var entry : prefixes.entrySet()) {
            if (!DOCUMENTED_PREFIXES.contains(entry.getKey())) {
                continue;
            }
            var bound = binder.bind(entry.getKey(), entry.getValue());
            if (bound.isBound()) {
                String fromFile = MAPPER.writeValueAsString(bound.get());
                String fromCode = MAPPER.writeValueAsString(
                        entry.getValue().getDeclaredConstructor().newInstance());
                if (!fromFile.equals(fromCode)) {
                    drifted.add("%s%n  yaml: %s%n  code: %s"
                            .formatted(entry.getKey(), fromFile, fromCode));
                }
            }
        }

        assertThat(drifted)
                .as("application.yaml states a value that differs from the code default, so "
                        + "documenting it changed behaviour: %s", drifted)
                .isEmpty();
    }

    @Test
    @DisplayName("Every documented prefix is one the codebase actually declares")
    void documentedListHasNoStalePrefix() throws Exception {
        // The list above is hand-kept, so it can outlive a renamed or deleted
        // prefix and then silently assert nothing about it.
        assertThat(declaredPrefixes().keySet()).containsAll(DOCUMENTED_PREFIXES);
    }

    @Test
    @DisplayName("The safety-relevant flags say what they mean, in the file")
    void safetyFlagsAreVisibleAndOff() throws Exception {
        var binder = new Binder(ConfigurationPropertySources.from(applicationYaml()));

        var guardrail = binder.bind("turing.safety.guardrail", TurGuardrailProperty.class)
                .orElseThrow(() -> new AssertionError("no turing.safety.guardrail block"));
        var gateway = binder.bind("turing.gateway", TurGatewayProperty.class)
                .orElseThrow(() -> new AssertionError("no turing.gateway block"));
        var abuse = binder.bind("turing.abuse", TurAbuseControlProperty.class)
                .orElseThrow(() -> new AssertionError("no turing.abuse block"));

        // The two opt-in surfaces stay off, and the one protective default stays on.
        assertThat(guardrail.isEnabled()).isFalse();
        assertThat(gateway.isEnabled()).isFalse();
        assertThat(gateway.isRequireVirtualKey()).isTrue();
        assertThat(abuse.getChat().isRateLimitEnabled()).isTrue();
        // A spend ceiling is deployment-specific and must not arrive from the file.
        assertThat(abuse.getChat().getHardMonthlyCapUsd()).isZero();
    }

    @Test
    @DisplayName("No credential is written into the file, only named in a comment")
    void noCredentialsAreCommitted() throws Exception {
        var binder = new Binder(ConfigurationPropertySources.from(applicationYaml()));

        var guardrail = binder.bind("turing.safety.guardrail", TurGuardrailProperty.class)
                .orElseThrow(() -> new AssertionError("no turing.safety.guardrail block"));
        var retrieval = binder.bind("turing.retrieval", TurRetrievalProperty.class)
                .orElseThrow(() -> new AssertionError("no turing.retrieval block"));

        assertThat(guardrail.getBedrock().getAccessKeyId()).isNull();
        assertThat(guardrail.getBedrock().getSecretAccessKey()).isNull();
        assertThat(guardrail.getMistral().getApiKey()).isNull();
        assertThat(retrieval.getBedrockKb().getAccessKeyId()).isNull();
        assertThat(retrieval.getBedrockKb().getSecretAccessKey()).isNull();
        // Blank ids are what leave the adapters inert; an empty string written by
        // the file would not be null, which is why they are comments.
        assertThat(guardrail.getBedrock().getGuardrailId()).isNull();
        assertThat(retrieval.getBedrockKb().getKnowledgeBaseId()).isNull();
    }
}
