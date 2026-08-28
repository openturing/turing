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
package com.viglet.turing.contentai;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.viglet.turing.properties.TurContentAiProperty;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * T1045 / §XX.22 — what content sources the Adobe tenant actually has, so a
 * configuration can be checked against it instead of inferred from an empty
 * result set.
 *
 * <p>The problem this exists for: Adobe answers a query naming a source it does
 * not have with {@code totalResults: 0}. That is the same observation as a source
 * that exists and matched nothing, so a typo in
 * {@code turing.content-ai.sources.<site>} — or no mapping at all against a
 * source never named after the Turing core — is indistinguishable from an empty
 * index, and the plugin had nothing that could tell them apart.
 *
 * <p>The listing is cached per endpoint, because it answers a question about
 * configuration rather than about content: the set of sources changes when
 * somebody creates one, not when a document is published. The TTL bounds how long
 * a stale answer can mislead, and a miss costs one request. This is a plain map
 * rather than a Spring cache deliberately — the value is a small immutable
 * snapshot with no eviction event to hook, and a cache needs a paired eviction to
 * be worth declaring.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurContentAiSourceCatalog {

    /** Cursor pages to walk before giving up on a tenant with an implausible number of sources. */
    private static final int MAX_PAGES = 20;

    private static final int PAGE_SIZE = 50;

    private static final String NODE_ITEMS = "items";
    private static final String NODE_CURSOR = "cursor";
    /** {@code ContentSource.config}, the {@code SourceConfig} the bundle declares. */
    private static final String NODE_CONFIG = "config";

    /** What T1045 read instead — kept as a fallback, see {@link #capabilities}. */
    private static final String NODE_SEARCH = "search";
    private static final String NODE_VECTOR_SPACES = "vectorSpaces";
    private static final String NODE_LEXICAL_SPACES = "lexicalSpaces";
    private static final String NODE_FIELDS = "fields";
    private static final String NODE_FIELD = "field";
    private static final String NODE_FILTER = "filter";
    private static final String NODE_FULLTEXT = "fulltext";
    private static final String NODE_ENABLED = "enabled";
    private static final String NODE_NAME = "name";
    private static final String NODE_TYPE = "type";

    private final TurContentAiClient client;
    private final Map<String, Snapshot> byEndpoint = new ConcurrentHashMap<>();

    public TurContentAiSourceCatalog(TurContentAiClient client) {
        this.client = client;
    }

    private record Snapshot(List<TurContentAiSource> sources, Instant fetchedAt) {
    }

    /**
     * Every source the tenant at {@code baseUrl} has, from cache when fresh.
     *
     * @throws TurContentAiClient.TurContentAiException on a transport, auth or
     *                                                  parse failure — the caller
     *                                                  decides whether a failed
     *                                                  diagnostic is fatal, and in
     *                                                  every current caller it is
     *                                                  not
     */
    public List<TurContentAiSource> sources(String baseUrl, TurContentAiProperty property) {
        Duration ttl = Duration.ofSeconds(Math.max(1, property.getSourceCacheSeconds()));
        Snapshot cached = byEndpoint.get(baseUrl);
        if (cached != null && cached.fetchedAt().plus(ttl).isAfter(Instant.now())) {
            return cached.sources();
        }
        List<TurContentAiSource> fetched = fetchAll(baseUrl, property);
        byEndpoint.put(baseUrl, new Snapshot(fetched, Instant.now()));
        return fetched;
    }

    /** The named source, or empty when the tenant has no source by that name. */
    public Optional<TurContentAiSource> find(String baseUrl, String sourceName,
            TurContentAiProperty property) {
        if (StringUtils.isBlank(sourceName)) {
            return Optional.empty();
        }
        return sources(baseUrl, property).stream()
                .filter(source -> sourceName.equals(source.name()))
                .findFirst();
    }

    /** Drops the cached listing for one endpoint, so the next read is live. */
    public void invalidate(String baseUrl) {
        byEndpoint.remove(baseUrl);
    }

    // ---- fetch -----------------------------------------------------------

    private List<TurContentAiSource> fetchAll(String baseUrl, TurContentAiProperty property) {
        List<TurContentAiSource> all = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            JsonNode response = client.listSources(baseUrl, cursor, PAGE_SIZE, property);
            JsonNode items = response != null ? response.get(NODE_ITEMS) : null;
            if (items != null && items.isArray()) {
                items.forEach(item -> all.add(toSource(item)));
            }
            cursor = response != null && response.has(NODE_CURSOR)
                    ? StringUtils.trimToNull(response.get(NODE_CURSOR).asString())
                    : null;
            if (cursor == null) {
                return all;
            }
        }
        log.warn("Content AI returned more than {} pages of content sources at {}; the listing "
                + "used for configuration checks is truncated", MAX_PAGES, baseUrl);
        return all;
    }

    /**
     * The capability block Turing reads to decide what a source supports.
     *
     * <p>T1052 — Adobe's bundle declares it as {@code config}, a {@code SourceConfig}
     * carrying {@code vectorSpaces} and {@code lexicalSpaces}. T1045 read it as
     * {@code search}, and nothing caught that: {@code ContentSource} permits extra
     * properties, so the hand-written fixture and this reader agreed with each other
     * and neither agreed with Adobe. The failure it produced does not throw — a
     * source parses with no spaces and no filterable fields, so facets quietly stop
     * working on a federated site.
     *
     * <p>{@code search} is still read as a fallback rather than deleted. It is one
     * {@code get} against a live third-party endpoint this project cannot query to
     * prove which key the deployed version sends, and reading both is strictly
     * better than being wrong in either direction — the same bargain
     * {@code TurContentAiResponseMapper} takes over {@code buckets}/{@code values}.
     */
    private JsonNode capabilities(JsonNode item) {
        JsonNode config = item.get(NODE_CONFIG);
        return config != null ? config : item.get(NODE_SEARCH);
    }

    private TurContentAiSource toSource(JsonNode item) {
        JsonNode search = capabilities(item);
        Set<String> filterFields = new LinkedHashSet<>();
        Set<String> fulltextFields = new LinkedHashSet<>();
        List<String> lexicalSpaces = spaceNames(search, NODE_LEXICAL_SPACES);
        collectFields(search, filterFields, fulltextFields);
        return new TurContentAiSource(
                text(item, NODE_NAME),
                text(item, NODE_TYPE),
                spaceNames(search, NODE_VECTOR_SPACES),
                lexicalSpaces,
                filterFields,
                fulltextFields);
    }

    private List<String> spaceNames(JsonNode search, String node) {
        List<String> names = new ArrayList<>();
        if (search == null) {
            return names;
        }
        JsonNode spaces = search.get(node);
        if (spaces != null && spaces.isObject()) {
            spaces.propertyNames().forEach(names::add);
        }
        return names;
    }

    /**
     * The fields a lexical space declares, split by what the index will do with
     * them. A field is only filterable if {@code filter.enabled} is true — Adobe's
     * own example carries a {@code description} field with
     * {@code filter: {enabled: false}}, so presence in the list is not the answer.
     */
    private void collectFields(JsonNode search, Set<String> filterFields,
            Set<String> fulltextFields) {
        if (search == null) {
            return;
        }
        JsonNode spaces = search.get(NODE_LEXICAL_SPACES);
        if (spaces == null || !spaces.isObject()) {
            return;
        }
        for (Map.Entry<String, JsonNode> space : spaces.properties()) {
            JsonNode fields = space.getValue().get(NODE_FIELDS);
            if (fields == null || !fields.isArray()) {
                continue;
            }
            for (JsonNode field : fields) {
                String name = text(field, NODE_FIELD);
                if (StringUtils.isBlank(name)) {
                    continue;
                }
                if (flag(field, NODE_FILTER)) {
                    filterFields.add(name);
                }
                if (flag(field, NODE_FULLTEXT)) {
                    fulltextFields.add(name);
                }
            }
        }
    }

    /**
     * Whether {@code <node>.enabled} is true. Absent is <em>false</em>: Adobe
     * states the flag per field where it matters, and reading an absent block as
     * "enabled" would report a field as filterable that a facet request then finds
     * empty — the same silent shape this class exists to remove.
     */
    private boolean flag(JsonNode field, String node) {
        JsonNode block = field.get(node);
        return block != null && block.has(NODE_ENABLED) && block.get(NODE_ENABLED).asBoolean();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node != null ? node.get(field) : null;
        return value != null && !value.isNull() ? value.asString() : null;
    }
}
