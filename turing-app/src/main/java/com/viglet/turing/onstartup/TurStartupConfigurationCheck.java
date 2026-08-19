/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.onstartup;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.system.TurStartupFailure;
import com.viglet.turing.system.security.TurCryptoKeyMaterial;

import jakarta.annotation.PostConstruct;

/**
 * Refuses a configuration that startup cannot finish, before startup starts.
 *
 * <p>T986 / §XCV — {@code TurLLMInstanceOnStartup} is {@code @Order(3)}: by the time
 * it discovers that a provider key is set with no {@code turing.ai.crypto.key} to
 * encrypt it with, the seed import at {@code @Order(2)} has already written to the
 * database. The operator gets exit 1 on a half-populated install, and the two
 * settings that contradict each other are never named together.
 *
 * <p>This runs at bean initialisation — before every {@code ApplicationRunner} — so
 * the refusal arrives with nothing written and both halves of the contradiction in
 * one sentence. It does not add a rule: the same configuration fails today, later
 * and less legibly.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurStartupConfigurationCheck {

    /** The keys {@code TurLLMInstanceOnStartup} provisions from, in its own order. */
    private static final List<String> PROVIDER_KEY_ENV = List.of("OPENAI_API_KEY", "GEMINI_API_KEY");

    private static final String DEFAULT_LLM_ENABLED_PROPERTY = "turing.startup.default-llm.enabled";

    private final Environment environment;
    private final String configuredCryptoKey;

    public TurStartupConfigurationCheck(Environment environment,
            @Value("${turing.ai.crypto.key:}") String configuredCryptoKey) {
        this.environment = environment;
        this.configuredCryptoKey = configuredCryptoKey;
    }

    @PostConstruct
    void refuseConfigurationStartupCannotFinish() {
        if (!isProductionProfileActive()
                || !environment.getProperty(DEFAULT_LLM_ENABLED_PROPERTY, Boolean.class, Boolean.TRUE)) {
            return;
        }
        String providerKeyEnv = firstProviderKeyPresent();
        if (providerKeyEnv == null) {
            return;
        }
        String rejection = TurCryptoKeyMaterial.productionRejection(configuredCryptoKey);
        if (rejection == null) {
            return;
        }
        // ASCII and one sentence per line on purpose: this is read off a console
        // whose codepage mangles anything else, and read once, in a hurry.
        throw TurStartupFailure.fatal(providerKeyEnv
                + " is set, so startup will provision an LLM instance and has to encrypt that key."
                + System.lineSeparator() + rejection
                + System.lineSeparator() + "Either configure the key, or unset " + providerKeyEnv
                + " (or set " + DEFAULT_LLM_ENABLED_PROPERTY + "=false) to skip provisioning.");
    }

    /** The name of the first provider key that is set, or {@code null} if none is. */
    private String firstProviderKeyPresent() {
        return PROVIDER_KEY_ENV.stream()
                .filter(name -> StringUtils.hasText(environment.getProperty(name)))
                .findFirst()
                .orElse(null);
    }

    private boolean isProductionProfileActive() {
        return List.of(environment.getActiveProfiles()).stream()
                .anyMatch("production"::equalsIgnoreCase);
    }
}
