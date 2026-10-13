/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.catalog.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.genai.catalog.planning.TurCopilotQueryPlanner.PlanRequest;
import com.viglet.turing.sn.dsl.TurDslQuery;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.eval.TurNLFacetField;
import com.viglet.turing.sn.nl.TurSNNLFilterService;
import com.viglet.turing.sn.nl.TurSNNLFilters;

import tools.jackson.databind.json.JsonMapper;

/**
 * T1085 — the copilot's parse goes through the shared extractor: its filters come
 * back spelled as the site holds them, its sort and size are kept, and a failure
 * leaves the parse as it was.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurCopilotParseGroundingTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final List<TurNLFacetField> SCHEMA = List.of(
            new TurNLFacetField("city", TurSEFieldType.STRING, true, null));
    private static final PlanRequest REQUEST = new PlanRequest("vagas", "pt_BR", "vagas em sao paulo", SCHEMA, 1);

    @Mock
    private TurSNNLFilterService nlFilterService;
    @InjectMocks
    private TurCopilotParseGrounding grounding;

    private static TurDslQueryRequest parsed() {
        return MAPPER.readValue("""
                {"query":{"bool":{"filter":[{"term":{"city":"sao paulo"}}]}},
                 "sort":[{"salary":"desc"}],"size":5}""", TurDslQueryRequest.class);
    }

    @Test
    void groundedValuesReplaceTheParsedOnesAndTheSortAndSizeStay() {
        TurDslQueryRequest parsed = parsed();
        when(nlFilterService.ground(eq("vagas"), eq("pt_BR"), eq("vagas em sao paulo"), eq(SCHEMA), eq(parsed)))
                .thenReturn(new TurSNNLFilters(true, "vagas em sao paulo", "", List.of(
                        new TurSNNLFilters.Filter("city", List.of("São Paulo"), null, null, List.of("sao paulo"), 1,
                                true)),
                        List.of("city:São Paulo"), List.of()));

        TurDslQueryRequest result = grounding.ground(REQUEST, "vagas em sao paulo", parsed);

        assertThat(((TurDslQuery.Bool) result.query()).filter())
                .containsExactly(new TurDslQuery.Term("city", "São Paulo"));
        assertThat(result.sort()).containsExactly(Map.of("salary", "desc"));
        assertThat(result.size()).isEqualTo(5);
    }

    @Test
    void aFailureKeepsTheParseAsItWas() {
        TurDslQueryRequest parsed = parsed();
        when(nlFilterService.ground(any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("down"));

        assertThat(grounding.ground(REQUEST, "vagas em sao paulo", parsed)).isSameAs(parsed);
    }
}
