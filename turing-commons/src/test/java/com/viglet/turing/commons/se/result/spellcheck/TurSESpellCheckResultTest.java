package com.viglet.turing.commons.se.result.spellcheck;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurSESpellCheckResultTest {

    @Test
    void shouldExposeDefaultConstructorValues() {
        TurSESpellCheckResult result = new TurSESpellCheckResult();

        assertThat(result.isCorrected()).isFalse();
        assertThat(result.getCorrectedText()).isEmpty();
    }

    @Test
    void shouldExposeParameterizedValuesAndToString() {
        TurSESpellCheckResult result = new TurSESpellCheckResult(true, "machine");
        result.setUsingCorrected(true);

        assertThat(result.isCorrected()).isTrue();
        assertThat(result.getCorrectedText()).isEqualTo("machine");
        assertThat(result.isUsingCorrected()).isTrue();
        assertThat(result.toString()).contains("machine").contains("usingCorrected=true");
    }
}
