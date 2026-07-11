package com.viglet.turing.system.security;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.core.env.Environment;

import lombok.extern.slf4j.Slf4j;

/**
 * Supplies the master key material for {@link TurSecretCryptoService}, preserving
 * Turing's original policy: use {@code turing.ai.crypto.key} when set; otherwise
 * fail fast under the {@code production} profile, or fall back to an insecure
 * development key (warned once) outside production.
 *
 * <p>Hardening (T639 / §XXXVII.1): under the {@code production} profile the key
 * must arrive from the environment ({@code TURING_AI_CRYPTO_KEY}) and must not be
 * a known committed sample / dev sentinel. The historical fail-fast only fired on
 * a <em>blank</em> key, so the {@code sample-key-for-crypto} value that used to
 * ship in {@code application.yaml} silently encrypted every provider API key and
 * store credential — anyone with repo access + a DB copy could decrypt them. We
 * now reject the known sentinels outright in production. Rotation procedure:
 * see {@code docs/operations/crypto-key-rotation.md}.
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

    /**
     * Values that must never be accepted as a real master key in production.
     * Compared case-insensitively and after trimming. Extend this set if a new
     * placeholder is ever committed.
     */
    private static final Set<String> REJECTED_SENTINELS = Set.of(
            "sample-key-for-crypto",
            DEV_FALLBACK_KEY,
            "changeme",
            "change-me",
            "your-key-here",
            "secret",
            "password");

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
                throw new IllegalStateException(
                        "turing.ai.crypto.key must be configured in production "
                                + "(set the TURING_AI_CRYPTO_KEY environment variable)");
            }
            if (!warnedAboutFallback) {
                warnedAboutFallback = true;
                log.warn("Using insecure fallback key for AI secret encryption outside production");
            }
            material = DEV_FALLBACK_KEY;
            return material;
        }
        if (isProductionProfileActive() && isRejectedSentinel(material)) {
            throw new IllegalStateException(
                    "turing.ai.crypto.key is set to a known sample/dev value and must not be used in "
                            + "production. Set TURING_AI_CRYPTO_KEY to a strong, secret value and rotate any "
                            + "secrets previously encrypted with the sample key "
                            + "(see docs/operations/crypto-key-rotation.md).");
        }
        return material;
    }

    private boolean isRejectedSentinel(String material) {
        return REJECTED_SENTINELS.contains(material.trim().toLowerCase());
    }

    private boolean isProductionProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch("production"::equalsIgnoreCase);
    }
}
