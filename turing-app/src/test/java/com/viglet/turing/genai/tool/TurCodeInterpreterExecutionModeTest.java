package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link TurCodeInterpreterExecutionMode#fromString(String)} — the
 * lenient parse must never throw and must fall back to NATIVE so a corrupt
 * config row can't brick the Code Interpreter.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurCodeInterpreterExecutionModeTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "nonsense", "dockerr", "123"})
    void fromStringShouldFallBackToDefaultForInvalid(String raw) {
        assertThat(TurCodeInterpreterExecutionMode.fromString(raw))
                .isEqualTo(TurCodeInterpreterExecutionMode.NATIVE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOCKER", "docker", "  Docker  "})
    void fromStringShouldParseDockerCaseInsensitively(String raw) {
        assertThat(TurCodeInterpreterExecutionMode.fromString(raw))
                .isEqualTo(TurCodeInterpreterExecutionMode.DOCKER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NATIVE", "native"})
    void fromStringShouldParseNative(String raw) {
        assertThat(TurCodeInterpreterExecutionMode.fromString(raw))
                .isEqualTo(TurCodeInterpreterExecutionMode.NATIVE);
    }

    @Test
    void defaultShouldBeNative() {
        assertThat(TurCodeInterpreterExecutionMode.DEFAULT)
                .isEqualTo(TurCodeInterpreterExecutionMode.NATIVE);
    }
}
