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
package com.viglet.turing.sn.snapshot;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSiteFacetRangeEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldSortEnum;
import com.viglet.turing.sn.TurSNFieldType;

/**
 * Immutable, atomic snapshot of every persistent piece of configuration the SN
 * search flow needs for one (site, locale) pair. Built once on first access by
 * {@link TurSNSiteSearchSnapshotService} and cached so subsequent searches do
 * not touch the database. Eviction is centralized in
 * {@link TurSNSiteSnapshotEvictionListener}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public record TurSNSiteSearchSnapshot(
        TurSNSite site,
        Locale locale,
        String pluginType,
        boolean hasHlFields,
        List<FieldExtView> enabledFields,
        Set<String> fieldIdsWithCustomFacets,
        Map<String, List<CustomFacetView>> customFacetsByFieldId,
        Map<String, Set<FacetLabelView>> facetLabelsByFieldId,
        List<LocaleView> allLocales,
        List<CustomSortView> customSorts) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * T898 / §LXXI.1 — an enabled field, as every scalar the entity carries.
     *
     * <p>The other views here project only what is read. This one deliberately
     * carries <strong>all twenty-one</strong> scalars, because its consumers do
     * not read it — they hand it on. `TurSNFacetDefinition` exposes the entity,
     * `TurSNSiteFieldExtDto` copies twenty properties off it, and three finders
     * take it as a query parameter, so the shape that reaches them has to be the
     * shape they already get. `TurSNSiteSearchSnapshotService.toEntity` rebuilds a
     * transient entity from this at the boundary.
     *
     * <p>That makes this a copy, and a copy is exactly where a forgotten field
     * changes behaviour in silence. `TurSNSiteSearchSnapshotServiceTest` closes
     * that by reflection: it populates every declared scalar, round-trips it, and
     * compares every getter, so adding a column to the entity without adding it
     * here is a red test rather than a null in production.
     *
     * <p>The two lazy collections are <em>not</em> carried, and cannot regress
     * anything: on the cached detached entity they were uninitialised proxies
     * already, so any consumer reading them was throwing before this change.
     */
    public record FieldExtView(
            String id, String externalId, String name, String description, String facetName,
            TurSNSiteFacetRangeEnum facetRange, TurSNSiteFacetFieldEnum facetType,
            TurSNSiteFacetFieldEnum facetItemType, TurSNSiteFacetFieldSortEnum facetSort,
            Integer facetPosition, Boolean secondaryFacet, Boolean showAllFacetItems,
            TurSNFieldType snType, TurSEFieldType type,
            int multiValued, int facet, int hl, int mlt, int enabled, int required,
            String defaultValue) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * T898 / §LXXI.1 — a custom facet as the search flow reads it. The entity it
     * replaces carried a lazy {@code items} collection and a back-reference to its
     * field ext, neither of which the one consumer
     * ({@code TurSolrQueryBuilder#getFacetFieldsInFilterQuery}) touches: it reads
     * the name and nothing else.
     *
     * <p>Carrying only what is read is the point, not an economy. A component added
     * here later is a deliberate act with a caller to justify it, where a cached
     * entity silently offered every association and made the next
     * {@code LazyInitializationException} a matter of which getter someone reached
     * for.
     */
    public record CustomFacetView(String id, String name) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * T898 / §LXXI.1 — a custom sort as the search flow reads it. Its one consumer
     * ({@code TurSNSiteSearchAPI#sortOptions}) builds a {@code TurSNSortOptionBean}
     * from the name and the description; the entity's lazy {@code items} were
     * fetch-joined on every snapshot build and never read.
     */
    public record CustomSortView(String id, String name, String description)
            implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * T898 / §LXXI.1 — a per-locale facet label as the search flow reads it.
     *
     * <p>Locale and label only. The entity also carries an {@code id}, and the
     * {@code TurSNSiteFieldExtFacetDto} built downstream copies one — but that DTO
     * is an internal {@code facetMap} value, and its single consumer
     * ({@code TurSNFacetRenderer#groupLabel}) matches on the locale and reads the
     * label. Nothing reads the id, so nothing here carries it.
     *
     * <p>Unlike the other two views this one has a consumer that hands its value on
     * to {@code TurSNFacetDefinitionFactory}, whose contract
     * ({@code TurSNFacetDefinition}) still traffics in entities — and legitimately
     * so, since the template and provisioning paths that share it persist them.
     * {@code TurSNSearchProcess} therefore builds a transient
     * {@code TurSNSiteFieldExtFacet} from this record at that boundary, exactly as
     * its own no-label fallback already does. The distinction that matters is the
     * one Block AC draws: what is <em>cached</em> holds no entity, and a
     * per-request object built by a builder carries no lazy proxy to begin with.
     */
    public record FacetLabelView(Locale locale, String label) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * T898 / §LXXI.1 — one of the site's locales, in the order the build read them.
     *
     * <p>Six of the eight consumers want the {@code language} and nothing else. The
     * other two — the similar-documents and duplicate-clustering paths — resolve one
     * of these and hand it to {@link com.viglet.turing.plugins.se.TurSearchEnginePlugin},
     * whose surface is typed on the entity, so they rebuild a transient
     * {@code TurSNSiteLocale} at that boundary. The plugin interface does the same
     * thing itself in {@code createStandaloneIndex}, so this is its own idiom rather
     * than a new one.
     *
     * <p>That transient instance must carry the site: the Elasticsearch plugin uses
     * {@code siteLocale.getTurSNSite()} as a repository query parameter and Lucene
     * resolves its MLT fields from it, so leaving it null would quietly degrade both
     * rather than fail. {@code core} is what the plugins address the index by.
     */
    public record LocaleView(Locale language, String core, int position)
            implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    public String siteId() {
        return site != null ? site.getId() : null;
    }

    public String siteName() {
        return site != null ? site.getName() : null;
    }
}
