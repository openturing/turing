/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.commons.sn.field;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * T809 / §LVI.2 (Block BH) — the last-resort facet label humanizer.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNFieldLabelTest {

    @ParameterizedTest
    @CsvSource({
            "vendor,Vendor",
            "contextWindow,Context Window",
            "publication_date,Publication Date",
            "source-apps,Source Apps",
            "pricing_inputPer1M,Pricing Input Per 1M",
            "defaultURLField,Default URL Field",
            "pricing.outputPer1M,Pricing Output Per 1M",
            "MODEL,MODEL",
            "kind,Kind"
    })
    void humanizeSplitsSeparatorsCamelCaseAndUnits(String fieldName, String expected) {
        assertThat(TurSNFieldLabel.humanize(fieldName)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "_", "-.-"})
    void humanizeReturnsNullWhenThereIsNothingToHumanize(String fieldName) {
        assertThat(TurSNFieldLabel.humanize(fieldName)).isNull();
    }

    @Test
    void orHumanizedKeepsAConfiguredLabel() {
        assertThat(TurSNFieldLabel.orHumanized("Model Type", "kind")).isEqualTo("Model Type");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void orHumanizedFallsBackWhenTheLabelIsBlank(String label) {
        assertThat(TurSNFieldLabel.orHumanized(label, "contextWindow"))
                .isEqualTo("Context Window");
    }

    @Test
    void orHumanizedReturnsNullWhenNeitherLabelNorFieldNameHasText() {
        assertThat(TurSNFieldLabel.orHumanized(null, null)).isNull();
    }
}
