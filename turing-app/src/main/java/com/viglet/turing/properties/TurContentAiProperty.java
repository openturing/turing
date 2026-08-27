/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.properties;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * T1031 / §XX.18 — configuration for federating retrieval to an Adobe AEM
 * Content AI index ({@code turing.content-ai.*}).
 *
 * <p>The credential lives here rather than on {@code TurSEInstance} because that
 * entity has no secret column: it carries a title, an endpoint and a vendor, and
 * every engine it was written for authenticates at the network layer or not at
 * all. Content AI is the first search engine Turing talks to that needs a bearer
 * token, and adding an encrypted column to the shared instance table is a
 * migration this task does not need — the endpoint still comes from the SE
 * instance, so a per-tenant BYO-infra endpoint keeps working.
 *
 * <p>Every field is inert by default. A deployment that never sets
 * {@link #token} or {@link #apiKey} gets a plugin that registers, reports its
 * capabilities honestly and refuses to search, rather than one that silently
 * sends unauthenticated requests to Adobe.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "turing.content-ai")
public class TurContentAiProperty {

    /** Master switch. {@code false} → the plugin refuses every call by name. */
    private boolean enabled = false;

    /**
     * Bearer token for the Content AI APIs (Adobe Developer Console, "AEM
     * Content AI" card). Blank → {@link #apiKey} is tried instead.
     */
    private String token;

    /** {@code x-api-key} credential, the alternative Content AI accepts. */
    private String apiKey;

    /**
     * Content source name per SN site ({@code turing.content-ai.sources.<site>}).
     * Unmapped sites fall back to the (site, locale) core name, which is what a
     * source created to mirror a Turing site would be called.
     */
    private Map<String, String> sources = new LinkedHashMap<>();

    /**
     * {@code contentSource.type}: {@code ACQUISITION} (the API default),
     * {@code AEM_AUTHOR}, {@code AEM_PUBLISH} or {@code CUSTOM}.
     */
    private String sourceType = "ACQUISITION";

    /**
     * Name of the vector space to query, from the source's IndexConfig. Blank →
     * Adobe's default space.
     */
    private String vectorSpace;

    /** Name of the lexical space to query. Blank → Adobe's default space. */
    private String lexicalSpace;

    /**
     * Compose a {@code vector OR fulltext} query (Adobe's own hybrid shape).
     * {@code false} sends fulltext alone, which is the comparison a deployment
     * benchmarking the semantic half against BM25 wants.
     */
    private boolean hybrid = true;

    /** Relevance boost for the semantic arm of the hybrid query. */
    private float vectorBoost = 1.0f;

    /** Relevance boost for the lexical arm of the hybrid query. */
    private float fulltextBoost = 1.0f;

    /** {@code size} on each term-bucket aggregate, i.e. facet values per field. */
    private int facetSize = 10;

    /**
     * How many pages of cursor-walking a deep page request may cost. Content AI
     * paginates by opaque cursor with no offset, so page N costs N round trips;
     * beyond this, the plugin returns an empty page with the true total rather
     * than hammering Adobe. See {@code TurContentAiSearchEnginePlugin}.
     */
    private int maxPageWalk = 10;

    /** Per-request timeout in seconds. */
    private int timeoutSeconds = 10;

    /**
     * T1044 — ask Adobe for the matched chunks alongside each document, and carry
     * them as {@code TurSEResult.passages}. An agent grounding through
     * {@code turingSearch.sn(...)} then reads the matched passage as the hit's
     * {@code content} instead of a whole AEM page.
     *
     * <p>Off by default because it multiplies response size, and because the
     * passage becomes the {@code content} an existing script may already read —
     * a behaviour change, which this project ships behind a flag.
     */
    private boolean includeChunks = false;
}
