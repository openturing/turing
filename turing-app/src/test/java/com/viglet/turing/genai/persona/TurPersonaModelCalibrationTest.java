/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.viglet.turing.genai.persona.TurPersonaModelCalibration.Params;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaTone;

/**
 * Unit tests for the opt-in style→model calibration mapper (T605).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurPersonaModelCalibrationTest {

    private TurPersona persona(int calibrate, TurPersonaTone tone, int verbosity) {
        TurPersona p = new TurPersona();
        p.setCalibrateModelParams(calibrate);
        p.setTone(tone);
        p.setVerbosity(verbosity);
        return p;
    }

    @Test
    void returnsNoneWhenPersonaNull() {
        assertThat(TurPersonaModelCalibration.forPersona(null)).isSameAs(Params.NONE);
    }

    @Test
    void returnsNoneWhenNotOptedIn() {
        Params params = TurPersonaModelCalibration.forPersona(persona(0, TurPersonaTone.CASUAL, 5));
        assertThat(params).isSameAs(Params.NONE);
        assertThat(params.hasAny()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "TECHNICAL,0.2",
            "FORMAL,0.3",
            "EXECUTIVE,0.4",
            "CASUAL,0.8",
    })
    void mapsToneToTemperatureWhenOptedIn(TurPersonaTone tone, double expected) {
        Params params = TurPersonaModelCalibration.forPersona(persona(1, tone, 3));
        assertThat(params.temperature()).isEqualTo(expected);
    }

    @Test
    void leavesTemperatureNullWhenToneAbsent() {
        Params params = TurPersonaModelCalibration.forPersona(persona(1, null, 3));
        assertThat(params.temperature()).isNull();
        // verbosity still drives maxTokens.
        assertThat(params.maxTokens()).isEqualTo(1024);
    }

    @ParameterizedTest
    @CsvSource({
            "1,256",
            "2,512",
            "3,1024",
            "4,2048",
            "5,4096",
    })
    void mapsVerbosityToMaxTokens(int verbosity, int expected) {
        Params params = TurPersonaModelCalibration.forPersona(persona(1, TurPersonaTone.FORMAL, verbosity));
        assertThat(params.maxTokens()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"0,256", "-3,256", "9,4096"})
    void clampsVerbosityOutOfRange(int verbosity, int expected) {
        Params params = TurPersonaModelCalibration.forPersona(persona(1, TurPersonaTone.FORMAL, verbosity));
        assertThat(params.maxTokens()).isEqualTo(expected);
    }
}
