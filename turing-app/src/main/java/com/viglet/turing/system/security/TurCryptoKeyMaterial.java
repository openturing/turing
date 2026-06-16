package com.viglet.turing.system.security;

import java.util.Arrays;
import java.util.function.Supplier;

import org.springframework.core.env.Environment;

import lombok.extern.slf4j.Slf4j;

/**
 * Supplies the master key material for {@link TurSecretCryptoService}, preserving
 * Turing's original policy: use {@code turing.ai.crypto.key} when set; otherwise
 * fail fast under the {@code production} profile, or fall back to an insecure
 * development key (warned once) outside production.
 *
 * <p>Extracted as a {@link Supplier} so the {@code viglet-core}
 * {@code VigletSecretCryptoService} stays free of {@code Environment} / property
 * concerns (Block Q / T368).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
public class TurCryptoKeyMaterial implements Supplier<String> {

    private static final String DEV_FALLBACK_KEY = "turing-dev-insecure-default-key";

    private final Environment environment;
    private final String configuredKey;
    private boolean warnedAboutFallback = false;

    public TurCryptoKeyMaterial(Environment environment, String configuredKey) {
        this.environment = environment;
        this.configuredKey = configuredKey;
    }

    @Override
    public String get() {
        String material = configuredKey;
        if (material == null || material.isBlank()) {
            if (isProductionProfileActive()) {
                throw new IllegalStateException("turing.ai.crypto.key must be configured in production");
            }
            if (!warnedAboutFallback) {
                warnedAboutFallback = true;
                log.warn("Using insecure fallback key for AI secret encryption outside production");
            }
            material = DEV_FALLBACK_KEY;
        }
        return material;
    }

    private boolean isProductionProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch("production"::equalsIgnoreCase);
    }
}
