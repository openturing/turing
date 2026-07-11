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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.dto.sn.synonym.TurSNSynonymDto;
import com.viglet.turing.persistence.model.sn.synonym.TurSNSynonymType;

/**
 * Unit tests for the Algolia-synonym -> Turing-DTO mapping (T666).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurAlgoliaSynonymMapperTest {

    @Test
    void mapsAllFiveAlgoliaTypes() {
        List<Map<String, Object>> algolia = List.of(
                Map.of("objectID", "s1", "type", "synonym",
                        "synonyms", List.of("tv", "television", "telly")),
                Map.of("objectID", "s2", "type", "oneWaySynonym", "input", "tablet",
                        "synonyms", List.of("ipad", "galaxy tab")),
                Map.of("objectID", "s3", "type", "altCorrection1", "word", "tshirt",
                        "corrections", List.of("t-shirt")),
                Map.of("objectID", "s4", "type", "altCorrection2", "word", "colour",
                        "corrections", List.of("color")),
                Map.of("objectID", "s5", "type", "placeholder", "placeholder", "<number>",
                        "replacements", List.of("1", "2")));

        List<TurSNSynonymDto> dtos = TurAlgoliaSynonymMapper.toDtos(algolia, Locale.ENGLISH);
        assertEquals(5, dtos.size());
        assertEquals(TurSNSynonymType.REGULAR, dtos.get(0).type());
        assertEquals(List.of("tv", "television", "telly"), dtos.get(0).terms());
        assertEquals(TurSNSynonymType.ONE_WAY, dtos.get(1).type());
        assertEquals("tablet", dtos.get(1).input());
        assertEquals(TurSNSynonymType.ALTERNATIVE_CORRECTION_1, dtos.get(2).type());
        assertEquals(TurSNSynonymType.ALTERNATIVE_CORRECTION_2, dtos.get(3).type());
        assertEquals(TurSNSynonymType.PLACEHOLDER, dtos.get(4).type());
        assertEquals("<number>", dtos.get(4).input());
        assertTrue(dtos.stream().allMatch(d -> d.language() == Locale.ENGLISH && d.enabled()));
    }

    @Test
    void missingTypeDefaultsToRegular() {
        List<TurSNSynonymDto> dtos = TurAlgoliaSynonymMapper.toDtos(
                List.of(Map.of("objectID", "x", "synonyms", List.of("a", "b"))), Locale.ENGLISH);
        assertEquals(1, dtos.size());
        assertEquals(TurSNSynonymType.REGULAR, dtos.get(0).type());
    }

    @Test
    void skipsMalformedEntries() {
        List<Map<String, Object>> algolia = List.of(
                Map.of("objectID", "r1", "type", "synonym", "synonyms", List.of("only")), // <2 terms
                Map.of("objectID", "o1", "type", "oneWaySynonym", "synonyms", List.of("x")), // no input
                Map.of("objectID", "u1", "type", "unknownType", "synonyms", List.of("a", "b")));
        assertTrue(TurAlgoliaSynonymMapper.toDtos(algolia, Locale.ENGLISH).isEmpty());
    }
}
