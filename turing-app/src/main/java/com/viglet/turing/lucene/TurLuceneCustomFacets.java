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
package com.viglet.turing.lucene;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.springframework.stereotype.Component;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetItem;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.facet.TurSEFacetResultAttr;
import com.viglet.turing.sn.facet.TurSNCustomFacetFilterQuery;

import lombok.extern.slf4j.Slf4j;

/**
 * T1083 — custom facets (a field's labelled range items) on the Lucene engine,
 * mirroring what {@code TurSolrQueryBuilder} and {@code TurSolrResultProcessor}
 * do on Solr:
 * <ul>
 * <li>{@link #count} answers one {@link TurSEFacetResult} per custom facet,
 * named after it, with one item per label and the number of matching
 * documents in that item's range;</li>
 * <li>{@link #resolveFilter} turns the {@code <customFacet>:<label>} filter
 * query a clicked item sends back into the range it stands for.</li>
 * </ul>
 * The range is built by {@link TurSNCustomFacetFilterQuery}, the same text Solr
 * receives, and read by {@link TurLuceneFilterQueries}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurLuceneCustomFacets {

    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository;

    public TurLuceneCustomFacets(TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
            TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository) {
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.turSNSiteCustomFacetRepository = turSNSiteCustomFacetRepository;
    }

    /** A custom facet item resolved to its field and the filter value of its range. */
    public record ItemFilter(String field, TurSEFieldType type, String value) {
    }

    /** One custom facet: its field, and its items in display order. */
    private record Definition(TurSNSiteCustomFacet facet, TurSNSiteFieldExt field,
            List<TurSNSiteCustomFacetItem> items) {
    }

    /**
     * The range a {@code <customFacet>:<label>} selection stands for, or empty when
     * {@code facetName} names no custom facet of the site (an ordinary field).
     */
    public Optional<ItemFilter> resolveFilter(TurSNSite turSNSite, String facetName, String label) {
        Definition definition = definitions(turSNSite).get(facetName);
        if (definition == null) {
            return Optional.empty();
        }
        return definition.items().stream()
                .filter(item -> label.equals(item.getLabel()))
                .findFirst()
                .map(item -> itemFilter(definition.field(), item));
    }

    /**
     * Counts every custom facet's items over the documents {@code query} matches.
     * With {@code onlyFacet} set, counts that custom facet alone (the facet
     * drill-down), and nothing when it names no custom facet.
     */
    public List<TurSEFacetResult> count(IndexSearcher searcher, Query query, TurSNSite turSNSite,
            Locale locale, String onlyFacet) {
        List<TurSEFacetResult> results = new ArrayList<>();
        for (Definition definition : definitions(turSNSite).values()) {
            if (onlyFacet != null && !onlyFacet.equals(definition.facet().getName())) {
                continue;
            }
            countFacet(searcher, query, definition, locale).ifPresent(results::add);
        }
        return results;
    }

    private Optional<TurSEFacetResult> countFacet(IndexSearcher searcher, Query query,
            Definition definition, Locale locale) {
        TurSEFacetResult result = new TurSEFacetResult();
        result.setFacet(definition.facet().getName());
        Instant now = Instant.now();
        for (TurSNSiteCustomFacetItem item : definition.items()) {
            try {
                ItemFilter filter = itemFilter(definition.field(), item);
                Query itemQuery = new BooleanQuery.Builder()
                        .add(query, BooleanClause.Occur.MUST)
                        .add(TurLuceneFilterQueries.build(filter.field(), filter.value(), filter.type(), now),
                                BooleanClause.Occur.FILTER)
                        .build();
                result.add(item.getLabel(), new TurSEFacetResultAttr(item.getLabel(),
                        searcher.count(itemQuery), displayLabel(item, locale)));
            } catch (IOException | IllegalArgumentException e) {
                log.warn("[Lucene] custom facet item '{}' of '{}' not counted: {}", item.getLabel(),
                        definition.facet().getName(), e.getMessage());
            }
        }
        return result.getTurSEFacetResultAttr().isEmpty() ? Optional.empty() : Optional.of(result);
    }

    private static ItemFilter itemFilter(TurSNSiteFieldExt field, TurSNSiteCustomFacetItem item) {
        String fq = TurSNCustomFacetFilterQuery.of(field.getName(), field.getType(), item);
        return new ItemFilter(field.getName(), field.getType(), fq.substring(fq.indexOf(':') + 1));
    }

    /** The site's custom facets by name, each with the enabled field it belongs to. */
    private Map<String, Definition> definitions(TurSNSite turSNSite) {
        List<TurSNSiteFieldExt> enabledFields = turSNSiteFieldExtRepository
                .findByTurSNSiteAndEnabled(turSNSite, 1);
        if (enabledFields == null || enabledFields.isEmpty()) {
            return Map.of();
        }
        Map<String, TurSNSiteFieldExt> fieldById = enabledFields.stream()
                .filter(field -> field.getId() != null)
                .collect(Collectors.toMap(TurSNSiteFieldExt::getId, Function.identity(), (a, b) -> a));
        return turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(enabledFields).stream()
                .filter(facet -> facet.getName() != null && facet.getTurSNSiteFieldExt() != null)
                .filter(facet -> fieldById.containsKey(facet.getTurSNSiteFieldExt().getId()))
                .sorted(Comparator.comparing(TurLuceneCustomFacets::position)
                        .thenComparing(TurSNSiteCustomFacet::getName, String::compareToIgnoreCase))
                .collect(Collectors.toMap(TurSNSiteCustomFacet::getName,
                        facet -> new Definition(facet, fieldById.get(facet.getTurSNSiteFieldExt().getId()),
                                orderedItems(facet)),
                        (a, b) -> a, java.util.LinkedHashMap::new));
    }

    private static int position(TurSNSiteCustomFacet facet) {
        Integer position = facet.getFacetPosition();
        return position != null && position > 0 ? position : Integer.MAX_VALUE;
    }

    /** Items with a label, by position then label, as Solr lists them. */
    private static List<TurSNSiteCustomFacetItem> orderedItems(TurSNSiteCustomFacet facet) {
        return facet.getItems() == null ? List.of() : facet.getItems().stream()
                .filter(item -> item.getLabel() != null && !item.getLabel().isBlank())
                .sorted(Comparator.comparing((TurSNSiteCustomFacetItem item) ->
                        item.getPosition() != null && item.getPosition() > 0 ? item.getPosition()
                                : Integer.MAX_VALUE)
                        .thenComparing(TurSNSiteCustomFacetItem::getLabel, String::compareToIgnoreCase))
                .toList();
    }

    /** The item's label for the request locale, as {@code TurSolrResultProcessor} resolves it. */
    private static String displayLabel(TurSNSiteCustomFacetItem item, Locale locale) {
        Map<String, String> labels = item.getLabels();
        if (labels == null || labels.isEmpty() || locale == null) {
            return item.getLabel();
        }
        for (String key : List.of(locale.toString(), locale.toLanguageTag(), locale.getLanguage())) {
            String translated = labels.get(key);
            if (translated != null && !translated.isBlank()) {
                return translated;
            }
        }
        return item.getLabel();
    }
}
