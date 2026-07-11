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

package com.viglet.turing.sn.synonym;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.sn.synonym.TurSNSynonymType;

/**
 * Unit tests for the pure synonym rule validation/normalization (T662).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNSynonymNormalizerTest {

    @Test
    void regularTrimsDedupesAndDropsInput() {
        TurSNSynonymNormalizer.Normalized n = TurSNSynonymNormalizer.normalize(
                TurSNSynonymType.REGULAR, "ignored",
                Arrays.asList(" tv ", "television", "TV", "  ", null, "telly"));
        // input dropped for REGULAR; blanks/nulls removed; "TV" is a
        // case-insensitive dup of "tv" (first casing wins).
        assertNull(n.input());
        assertEquals(List.of("tv", "television", "telly"), n.terms());
    }

    @Test
    void regularRequiresTwoDistinctTerms() {
        assertThrows(IllegalArgumentException.class, () -> TurSNSynonymNormalizer.normalize(
                TurSNSynonymType.REGULAR, null, List.of("tv", "TV", "  tv ")));
    }

    @Test
    void oneWayKeepsInputAndExpansions() {
        TurSNSynonymNormalizer.Normalized n = TurSNSynonymNormalizer.normalize(
                TurSNSynonymType.ONE_WAY, "  tablet ", List.of("ipad", "galaxy tab"));
        assertEquals("tablet", n.input());
        assertEquals(List.of("ipad", "galaxy tab"), n.terms());
    }

    @Test
    void directionalTypesRequireInput() {
        assertThrows(IllegalArgumentException.class, () -> TurSNSynonymNormalizer.normalize(
                TurSNSynonymType.ONE_WAY, "  ", List.of("ipad")));
        assertThrows(IllegalArgumentException.class, () -> TurSNSynonymNormalizer.normalize(
                TurSNSynonymType.PLACEHOLDER, null, List.of("1", "2")));
    }

    @Test
    void directionalTypesRequireAtLeastOneTerm() {
        assertThrows(IllegalArgumentException.class, () -> TurSNSynonymNormalizer.normalize(
                TurSNSynonymType.ALTERNATIVE_CORRECTION_1, "tshirt", List.of("   ", "")));
    }

    @Test
    void nullTypeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> TurSNSynonymNormalizer.normalize(null, "x", List.of("a", "b")));
    }
}
