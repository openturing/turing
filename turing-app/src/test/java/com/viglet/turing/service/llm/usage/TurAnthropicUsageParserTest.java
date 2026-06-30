/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.llm.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.service.llm.usage.TurAnthropicUsageParser.CostRow;
import com.viglet.turing.service.llm.usage.TurAnthropicUsageParser.UsageRow;

/**
 * T183 / §X.14.c — parser coverage for the Anthropic usage + cost report JSON,
 * including the cache-token summing and the string-amount cost field.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurAnthropicUsageParserTest {

    private final TurAnthropicUsageParser parser = new TurAnthropicUsageParser();

    @Test
    void parsesUsageSummingCacheTokens() {
        String json = """
                {"data":[
                  {"starting_at":"2026-06-27T00:00:00Z","ending_at":"2026-06-28T00:00:00Z",
                   "results":[
                     {"model":"claude-opus-4","workspace_id":"ws_1","service_tier":"standard",
                      "uncached_input_tokens":1000,"cache_creation_input_tokens":200,
                      "cache_read_input_tokens":50,"output_tokens":300}
                   ]}
                ]}
                """;

        List<UsageRow> rows = parser.parseUsage(json);

        assertThat(rows).hasSize(1);
        UsageRow row = rows.get(0);
        assertThat(row.date()).isEqualTo(LocalDate.of(2026, 6, 27));
        assertThat(row.model()).isEqualTo("claude-opus-4");
        assertThat(row.workspaceId()).isEqualTo("ws_1");
        assertThat(row.serviceTier()).isEqualTo("standard");
        assertThat(row.inputTokens()).isEqualTo(1250L); // 1000 + 200 + 50
        assertThat(row.outputTokens()).isEqualTo(300L);
    }

    @Test
    void parsesFlatInputTokensVariant() {
        String json = """
                {"data":[{"starting_at":"2026-06-27T00:00:00Z","results":[
                  {"model":"claude-haiku","input_tokens":42,"output_tokens":7}]}]}
                """;
        List<UsageRow> rows = parser.parseUsage(json);
        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.inputTokens()).isEqualTo(42L);
            assertThat(r.outputTokens()).isEqualTo(7L);
        });
    }

    @Test
    void parsesCostWithStringAmount() {
        String json = """
                {"data":[
                  {"starting_at":"2026-06-27T00:00:00Z","ending_at":"2026-06-28T00:00:00Z",
                   "results":[{"workspace_id":"ws_1","amount":"12.34","currency":"USD"}]}
                ]}
                """;

        List<CostRow> rows = parser.parseCost(json);

        assertThat(rows).hasSize(1);
        CostRow row = rows.get(0);
        assertThat(row.date()).isEqualTo(LocalDate.of(2026, 6, 27));
        assertThat(row.workspaceId()).isEqualTo("ws_1");
        assertThat(row.model()).isNull();
        assertThat(row.costUsd()).isEqualTo(12.34);
    }

    @Test
    void emptyAndMalformedYieldEmptyLists() {
        assertThat(parser.parseUsage(null)).isEmpty();
        assertThat(parser.parseUsage("")).isEmpty();
        assertThat(parser.parseUsage("not json")).isEmpty();
        assertThat(parser.parseCost("{\"data\":[]}")).isEmpty();
        // zero-token / zero-amount rows are skipped
        assertThat(parser.parseUsage(
                "{\"data\":[{\"starting_at\":\"2026-06-27T00:00:00Z\",\"results\":["
                + "{\"model\":\"m\",\"input_tokens\":0,\"output_tokens\":0}]}]}")).isEmpty();
    }
}
