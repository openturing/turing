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

package com.viglet.turing.plugins.se.lucene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.sn.synonym.TurSNSynonymType;
import com.viglet.turing.plugins.se.TurSESynonymRule;

/**
 * Unit tests for the Turing-rules -> Lucene SynonymMap mapping (T665), verified
 * end-to-end by running the built map through the query-time analyzer and
 * asserting the token expansions it produces.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurLuceneSynonymMapBuilderTest {

    private static TurSESynonymRule rule(TurSNSynonymType type, String input, String... terms) {
        return new TurSESynonymRule(type, input, List.of(terms));
    }

    /** Runs {@code text} through the synonym analyzer and returns emitted tokens. */
    private static List<String> analyze(TurLuceneSynonymMap built, String text) throws IOException {
        List<String> tokens = new ArrayList<>();
        try (Analyzer analyzer = new TurLuceneSynonymAnalyzer(built.map());
             TokenStream ts = analyzer.tokenStream("f", text)) {
            CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                tokens.add(term.toString());
            }
            ts.end();
        }
        return tokens;
    }

    @Test
    void regularExpandsBothWays() throws IOException {
        TurLuceneSynonymMap built = TurLuceneSynonymMapBuilder.build(
                List.of(rule(TurSNSynonymType.REGULAR, null, "tv", "television", "telly")));
        assertEquals(1, built.appliedRules());
        // querying any term surfaces the whole equivalent set
        assertTrue(analyze(built, "tv").containsAll(List.of("tv", "television", "telly")));
        assertTrue(analyze(built, "telly").containsAll(List.of("tv", "television", "telly")));
    }

    @Test
    void oneWayExpandsOnlyFromInput() throws IOException {
        TurLuceneSynonymMap built = TurLuceneSynonymMapBuilder.build(
                List.of(rule(TurSNSynonymType.ONE_WAY, "tablet", "ipad")));
        assertTrue(analyze(built, "tablet").containsAll(List.of("tablet", "ipad")));
        // reverse direction does not expand
        assertFalse(analyze(built, "ipad").contains("tablet"));
    }

    @Test
    void correctionDegradesToOneWayWithWarning() throws IOException {
        TurLuceneSynonymMap built = TurLuceneSynonymMapBuilder.build(
                List.of(rule(TurSNSynonymType.ALTERNATIVE_CORRECTION_1, "tshirt", "t-shirt")));
        assertEquals(1, built.appliedRules());
        assertTrue(built.warnings().stream().anyMatch(w -> w.contains("degraded")));
    }

    @Test
    void placeholderUnsupportedYieldsNoMap() {
        TurLuceneSynonymMap built = TurLuceneSynonymMapBuilder.build(
                List.of(rule(TurSNSynonymType.PLACEHOLDER, "<number>", "1", "2")));
        assertEquals(0, built.appliedRules());
        assertNull(built.map());
        assertTrue(built.unsupportedTypes().contains(TurSNSynonymType.PLACEHOLDER));
    }

    @Test
    void emptyRulesYieldNoMap() {
        TurLuceneSynonymMap built = TurLuceneSynonymMapBuilder.build(List.of());
        assertNull(built.map());
        assertEquals(0, built.appliedRules());
    }
}
