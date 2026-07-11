/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.plugins.se.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.sn.synonym.TurSNSynonymType;
import com.viglet.turing.plugins.se.TurSESynonymRule;

/**
 * Unit tests for the pure Turing-rules -> Elasticsearch synonyms-set lines (T664).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurElasticsearchSynonymRulesBuilderTest {

    private static TurSESynonymRule rule(TurSNSynonymType type, String input, String... terms) {
        return new TurSESynonymRule(type, input, List.of(terms));
    }

    @Test
    void regularBecomesEquivalentLine() {
        TurElasticsearchSynonymRules r = TurElasticsearchSynonymRulesBuilder.build(
                List.of(rule(TurSNSynonymType.REGULAR, null, "tv", "television", "telly")));
        assertEquals(List.of("tv, television, telly"), r.ruleLines());
        assertEquals(1, r.appliedRules());
        assertTrue(r.unsupportedTypes().isEmpty());
    }

    @Test
    void oneWayBecomesExplicitMapping() {
        TurElasticsearchSynonymRules r = TurElasticsearchSynonymRulesBuilder.build(
                List.of(rule(TurSNSynonymType.ONE_WAY, "tablet", "ipad", "galaxy tab")));
        assertEquals(List.of("tablet => ipad, galaxy tab"), r.ruleLines());
    }

    @Test
    void correctionDegradesToOneWayWithWarning() {
        TurElasticsearchSynonymRules r = TurElasticsearchSynonymRulesBuilder.build(
                List.of(rule(TurSNSynonymType.ALTERNATIVE_CORRECTION_2, "tshirt", "t-shirt")));
        assertEquals(List.of("tshirt => t-shirt"), r.ruleLines());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("degraded")));
    }

    @Test
    void placeholderUnsupportedAndSkipped() {
        TurElasticsearchSynonymRules r = TurElasticsearchSynonymRulesBuilder.build(
                List.of(rule(TurSNSynonymType.PLACEHOLDER, "<number>", "1", "2")));
        assertTrue(r.ruleLines().isEmpty());
        assertEquals(0, r.appliedRules());
        assertTrue(r.unsupportedTypes().contains(TurSNSynonymType.PLACEHOLDER));
    }

    @Test
    void incompleteRulesAreDropped() {
        TurElasticsearchSynonymRules r = TurElasticsearchSynonymRulesBuilder.build(List.of(
                rule(TurSNSynonymType.REGULAR, null, "solo"),          // <2 terms
                rule(TurSNSynonymType.ONE_WAY, "  ", "x")));             // blank input
        assertTrue(r.ruleLines().isEmpty());
        assertFalse(r.appliedRules() > 0);
    }

    @Test
    void synonymSetIdNormalizes() {
        assertEquals("turing_pt_br",
                TurElasticsearchSynonymRulesBuilder.synonymSetId("turing", Locale.forLanguageTag("pt-BR")));
        assertEquals("turing_en",
                TurElasticsearchSynonymRulesBuilder.synonymSetId("turing", Locale.ENGLISH));
    }
}
