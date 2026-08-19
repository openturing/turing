package com.viglet.turing.system.security;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.core.env.Environment;

import com.viglet.turing.system.TurStartupFailure;

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
        if (isProductionProfileActive()) {
            String rejection = productionRejection(configuredKey);
            if (rejection != null) {
                throw TurStartupFailure.fatal(rejection);
            }
            return configuredKey;
        }
        if (configuredKey == null || configuredKey.isBlank()) {
            if (!warnedAboutFallback) {
                warnedAboutFallback = true;
                log.warn("Using insecure fallback key for AI secret encryption outside production");
            }
            return DEV_FALLBACK_KEY;
        }
        return configuredKey;
    }

    /**
     * Why this key cannot be used in production, or {@code null} when it can.
     *
     * <p>T986 / §XCV — extracted static so the check can also be made <em>before</em>
     * anything is provisioned. It used to fire only from {@link #get()}, which is
     * reached partway through startup: by then a seed import has already written to
     * the database, and the operator is left with a half-populated install and an
     * exit code. Same rule, one place, askable early.
     *
     * @param configuredKey the value of {@code turing.ai.crypto.key}, possibly null
     * @return the operator-facing reason, or {@code null} if the key is acceptable
     */
    public static String productionRejection(String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            return "turing.ai.crypto.key must be configured in production "
                    + "(set the TURING_AI_CRYPTO_KEY environment variable)";
        }
        if (REJECTED_SENTINELS.contains(configuredKey.trim().toLowerCase())) {
            return "turing.ai.crypto.key is set to a known sample/dev value and must not be used in "
                    + "production. Set TURING_AI_CRYPTO_KEY to a strong, secret value and rotate any "
                    + "secrets previously encrypted with the sample key "
                    + "(see docs/operations/crypto-key-rotation.md).";
        }
        return null;
    }

    private boolean isProductionProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch("production"::equalsIgnoreCase);
    }
}
