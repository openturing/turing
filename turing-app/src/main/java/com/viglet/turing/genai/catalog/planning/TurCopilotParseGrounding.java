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

package com.viglet.turing.genai.catalog.planning;

import org.springframework.stereotype.Component;

import com.viglet.turing.genai.catalog.planning.TurCopilotQueryPlanner.PlanRequest;
import com.viglet.turing.sn.dsl.TurDslQuery;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.nl.TurSNNLFilterService;
import com.viglet.turing.sn.nl.TurSNNLFilters;

import lombok.extern.slf4j.Slf4j;

/**
 * T1085 — sends a planner's NL facet parse through the shared extractor, so the
 * copilot filters by values the site holds, spelled as the site indexes them,
 * instead of by whatever the model wrote ("sao paulo" for "São Paulo" matched
 * nothing). The parse's sort and size are kept; a failure keeps the parse as it
 * was, which is what the planner sent before.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurCopilotParseGrounding {

    private final TurSNNLFilterService nlFilterService;

    public TurCopilotParseGrounding(TurSNNLFilterService nlFilterService) {
        this.nlFilterService = nlFilterService;
    }

    /**
     * @param text the text the parse was made from
     * @return the parse with its filters grounded, or {@code parsed} itself when
     *         grounding cannot run
     */
    public TurDslQueryRequest ground(PlanRequest request, String text, TurDslQueryRequest parsed) {
        if (parsed == null || parsed.query() == null) {
            return parsed;
        }
        try {
            TurSNNLFilters filters = nlFilterService.ground(request.siteName(), request.locale(), text,
                    request.schema(), parsed);
            TurDslQuery query = TurSNNLFilterService.toQuery(filters, request.schema(), parsed.query());
            int size = parsed.size() != null ? parsed.size() : TurCopilotQueryPlanner.DEFAULT_TOP_K;
            return TurCopilotQueryBodies.of(query, parsed.sort(), size);
        } catch (RuntimeException e) {
            log.warn("[CopilotPlanner] Could not ground the parse for '{}', keeping it as parsed: {}",
                    request.siteName(), e.getMessage());
            return parsed;
        }
    }
}
