package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link TurCodeInterpreterResourceLimiter#fromString(String)} — the
 * lenient parse must never throw, must accept the hyphenated
 * {@code systemd-run} alias, and must fall back to AUTO for anything invalid.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurCodeInterpreterResourceLimiterTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "nonsense", "prlimitt", "cgroups"})
    void fromStringShouldFallBackToDefaultForInvalid(String raw) {
        assertThat(TurCodeInterpreterResourceLimiter.fromString(raw))
                .isEqualTo(TurCodeInterpreterResourceLimiter.AUTO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRLIMIT", "prlimit", "  Prlimit  "})
    void fromStringShouldParsePrlimit(String raw) {
        assertThat(TurCodeInterpreterResourceLimiter.fromString(raw))
                .isEqualTo(TurCodeInterpreterResourceLimiter.PRLIMIT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"systemd-run", "SYSTEMD_RUN", "Systemd-Run"})
    void fromStringShouldParseSystemdRunWithHyphenAlias(String raw) {
        assertThat(TurCodeInterpreterResourceLimiter.fromString(raw))
                .isEqualTo(TurCodeInterpreterResourceLimiter.SYSTEMD_RUN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "NONE"})
    void fromStringShouldParseNone(String raw) {
        assertThat(TurCodeInterpreterResourceLimiter.fromString(raw))
                .isEqualTo(TurCodeInterpreterResourceLimiter.NONE);
    }

    @Test
    void defaultShouldBeAuto() {
        assertThat(TurCodeInterpreterResourceLimiter.DEFAULT)
                .isEqualTo(TurCodeInterpreterResourceLimiter.AUTO);
    }
}
