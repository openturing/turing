/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurToolArgsSummaryTest {

    @Test
    void nullOrBlankYieldsEmpty() {
        assertThat(TurToolArgsSummary.summarize(null)).isEmpty();
        assertThat(TurToolArgsSummary.summarize("   ")).isEmpty();
    }

    @Test
    void collapsesWhitespaceAndKeepsPlainArgs() {
        String out = TurToolArgsSummary.summarize("{\n  \"query\":  \"red shoes\"\n}");
        assertThat(out).isEqualTo("{ \"query\": \"red shoes\" }");
    }

    @Test
    void masksSecretBearingKeys() {
        String out = TurToolArgsSummary.summarize(
                "{\"apiKey\":\"sk-supersecret\",\"query\":\"hi\",\"token\":\"abc123\"}");
        assertThat(out).doesNotContain("sk-supersecret").doesNotContain("abc123");
        assertThat(out).contains("***").contains("\"query\":\"hi\"");
    }

    @Test
    void masksSecretWrittenWithEquals() {
        String out = TurToolArgsSummary.summarize("password=hunter2 user=alex");
        assertThat(out).doesNotContain("hunter2");
        assertThat(out).contains("user=alex");
    }

    @Test
    void truncatesLongDigestWithEllipsis() {
        String longArg = "{\"q\":\"" + "x".repeat(1000) + "\"}";
        String out = TurToolArgsSummary.summarize(longArg);
        assertThat(out).hasSize(TurToolArgsSummary.MAX_LENGTH + 1); // + ellipsis char
        assertThat(out).endsWith("…");
    }
}
