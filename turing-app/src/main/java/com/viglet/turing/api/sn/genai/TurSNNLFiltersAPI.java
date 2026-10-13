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

package com.viglet.turing.api.sn.genai;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.sn.nl.TurSNNLFilterService;
import com.viglet.turing.sn.nl.TurSNNLFilters;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T1085 — the filters a visitor's description names, as data:
 * {@code POST /api/sn/{site}/nl-filters} answers the {@code q} and {@code fq} a
 * site passes to its own search to show the ranked, faceted page for "part-time
 * near the subway in Sao Paulo". Anonymous like the chat and gated the same way:
 * it spends an LLM call.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/sn/{siteName}/nl-filters")
@Tag(name = "Semantic Navigation NL Filters", description = "Natural-language request to search filters (T1085)")
public class TurSNNLFiltersAPI {

    private final TurSNNLFilterService nlFilterService;

    public TurSNNLFiltersAPI(TurSNNLFilterService nlFilterService) {
        this.nlFilterService = nlFilterService;
    }

    @Operation(summary = "Parse a natural-language request into the q and fq a search takes")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public TurSNNLFilters parse(@PathVariable String siteName, @RequestBody NLFiltersRequest request) {
        return nlFilterService.parse(siteName, request.locale(), request.text());
    }

    /**
     * @param text   the visitor's request
     * @param locale the search locale; omitted, the site's first locale
     */
    public record NLFiltersRequest(String text, String locale) {
    }
}
