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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.DefaultToolMetadata;
import org.springframework.ai.tool.metadata.ToolMetadata;

import com.viglet.turing.observability.TurChatPipelineObservation;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurGenAiProperty;
import com.viglet.turing.properties.TurGenAiProperty.TurGenAiToolPreFilterProperty;

/**
 * Tests for §IV.2 / T29 BM25 tool pre-filter.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurToolPreFilterServiceTest {

    private TurToolDescriptionService descriptionService;
    private TurConfigProperties configProperties;
    private TurGenAiToolPreFilterProperty props;
    private TurToolPreFilterService service;

    @BeforeEach
    void setUp() {
        descriptionService = mock(TurToolDescriptionService.class);
        // Default: no .md overrides — service falls back to the callback's
        // own description, which we control in each test.
        when(descriptionService.getDescription(anyString())).thenReturn(null);

        TurGenAiProperty genai = new TurGenAiProperty();
        props = genai.getToolPrefilter();
        configProperties = new TurConfigProperties();
        configProperties.setGenai(genai);
        // T32 observation — null MeterRegistry falls back to
        // SimpleMeterRegistry inside TurChatPipelineObservation, so unit
        // tests don't need the full Micrometer + Actuator wiring.
        service = new TurToolPreFilterService(descriptionService, configProperties,
                new TurChatPipelineObservation(null));
    }

    @Test
    void returnsInputUnchangedWhenDisabled() {
        props.setEnabled(false);
        props.setMinToolsThreshold(0); // would otherwise filter
        ToolCallback[] callbacks = makeCallbacks(30);
        ToolCallback[] out = service.filter(callbacks, "anything", Set.of());
        assertThat(out).isSameAs(callbacks);
    }

    @Test
    void returnsInputUnchangedWhenBelowMinThreshold() {
        props.setMinToolsThreshold(20);
        props.setTopK(5);
        ToolCallback[] callbacks = makeCallbacks(10);
        ToolCallback[] out = service.filter(callbacks, "stock prices today", Set.of());
        assertThat(out).isSameAs(callbacks);
    }

    @Test
    void returnsInputUnchangedWhenUserMessageBlank() {
        props.setMinToolsThreshold(0);
        props.setTopK(3);
        ToolCallback[] callbacks = makeCallbacks(10);
        assertThat(service.filter(callbacks, null, Set.of())).isSameAs(callbacks);
        assertThat(service.filter(callbacks, "   ", Set.of())).isSameAs(callbacks);
    }

    @Test
    void returnsInputUnchangedWhenPoolFitsTopK() {
        props.setMinToolsThreshold(0);
        props.setTopK(50);
        ToolCallback[] callbacks = makeCallbacks(8);
        ToolCallback[] out = service.filter(callbacks, "something", Set.of());
        assertThat(out).isSameAs(callbacks);
    }

    @Test
    void filtersToTopKByBm25Score() {
        props.setMinToolsThreshold(0);
        props.setTopK(3);
        ToolCallback[] callbacks = Stream.of(
                cb("get_stock_quote", "Fetch a real-time stock quote for a ticker symbol"),
                cb("search_ticker", "Search company tickers by name on global exchanges"),
                cb("get_weather", "Get current weather conditions in a city"),
                cb("get_log_stats", "Aggregate application log statistics by level and source"),
                cb("send_email", "Send an outbound transactional email message"),
                cb("create_calendar_event", "Schedule a calendar invitation with attendees"),
                cb("translate_text", "Translate text between two languages")
        ).toArray(ToolCallback[]::new);

        ToolCallback[] out = service.filter(callbacks,
                "what is the current stock quote for AAPL ticker today?", Set.of());

        assertThat(out).hasSize(3);
        List<String> names = Arrays.stream(out)
                .map(c -> c.getToolDefinition().name())
                .toList();
        // The two finance tools should both survive — they are the only
        // ones whose description tokens (stock, quote, ticker) overlap
        // with the query. The third slot is filled by whichever the
        // BM25 ranking + fallback picks; we don't pin it.
        assertThat(names).contains("get_stock_quote", "search_ticker");
    }

    @Test
    void alwaysKeptToolsBypassRankingEvenWhenIrrelevant() {
        props.setMinToolsThreshold(0);
        props.setTopK(2);
        ToolCallback[] callbacks = Stream.of(
                cb("get_stock_quote", "Fetch a real-time stock quote for a ticker symbol"),
                cb("search_ticker", "Search company tickers by name on global exchanges"),
                cb("get_weather", "Get current weather conditions in a city"),
                cb("send_email", "Send an outbound transactional email message"),
                cb("translate_text", "Translate text between two languages")
        ).toArray(ToolCallback[]::new);

        ToolCallback[] out = service.filter(callbacks,
                "stock quote for AAPL", Set.of("send_email"));

        List<String> names = Arrays.stream(out)
                .map(c -> c.getToolDefinition().name())
                .toList();
        // send_email is protected even though the query has no overlap
        // with its description — proves the always-keep set wins.
        assertThat(names).contains("send_email");
        // Should still trim down (we asked top-K=2, and the protected
        // count is 1, so at most 1 BM25-ranked tool joins).
        assertThat(out).hasSizeLessThanOrEqualTo(2 + 1); // ranked quota + protected
    }

    @Test
    void emptyInputReturnsEmpty() {
        ToolCallback[] out = service.filter(new ToolCallback[0], "anything", Set.of());
        assertThat(out).isEmpty();
    }

    @Test
    void nullInputReturnsEmpty() {
        ToolCallback[] out = service.filter(null, "anything", Set.of());
        assertThat(out).isNotNull().isEmpty();
    }

    @Test
    void usesMdOverrideDescriptionWhenAvailable() {
        when(descriptionService.getDescription("get_stock_quote"))
                .thenReturn("Detailed market data: quote, ticker, AAPL, NASDAQ, real-time price");
        props.setMinToolsThreshold(0);
        props.setTopK(2);
        ToolCallback[] callbacks = Stream.of(
                cb("get_stock_quote", "."), // placeholder — the .md override should win
                cb("get_weather", "Get current weather conditions in a city"),
                cb("send_email", "Send transactional emails"),
                cb("translate_text", "Translate text between languages")
        ).toArray(ToolCallback[]::new);

        ToolCallback[] out = service.filter(callbacks, "AAPL ticker quote", Set.of());

        List<String> names = Arrays.stream(out)
                .map(c -> c.getToolDefinition().name())
                .toList();
        assertThat(names).contains("get_stock_quote");
    }

    private static ToolCallback[] makeCallbacks(int n) {
        ToolCallback[] result = new ToolCallback[n];
        for (int i = 0; i < n; i++) {
            result[i] = cb("tool_" + i, "Tool number " + i + " does something specific to slot " + i);
        }
        return result;
    }

    private static ToolCallback cb(String name, String description) {
        ToolDefinition def = DefaultToolDefinition.builder()
                .name(name)
                .description(description)
                .inputSchema("{}")
                .build();
        ToolMetadata metadata = DefaultToolMetadata.builder().build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return def;
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return metadata;
            }

            @Override
            public String call(String toolInput) {
                return "";
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return "";
            }
        };
    }
}
