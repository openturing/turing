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
package com.viglet.turing.sn.facet;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.bean.TurSNFilterParams;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchFacetBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchFacetItemBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchFacetLabelBean;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtDto;
import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtFacetDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.sn.TurSNUtils;
import com.viglet.turing.solr.TurSolrQueryBuilder;

/**
 * Builds the facet beans returned in a search response — main facets, secondary
 * facets and the "facets to remove" widget. Extracted from {@code
 * TurSNSearchProcess} (which had grown past 1k lines) so the rendering logic
 * lives next to the rest of the {@link com.viglet.turing.sn.facet} package.
 *
 * <p>Behaviour is intentionally identical to the previous inline
 * implementation; the only deduplication is between the main and secondary
 * facet builders, which differed solely in the visibility predicate.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurSNFacetRenderer {

    private static final String FACET_ITEM_AND = "-AND";
    private static final String AND_OR = "AND-OR";
    private static final String FACETS_TO_REMOVE = "Facets To Remove";

    private final TurSearchEnginePluginFactory searchEnginePluginFactory;
    private final TurSolrQueryBuilder turSolrQueryBuilder;

    public TurSNFacetRenderer(TurSearchEnginePluginFactory searchEnginePluginFactory,
            TurSolrQueryBuilder turSolrQueryBuilder) {
        this.searchEnginePluginFactory = searchEnginePluginFactory;
        this.turSolrQueryBuilder = turSolrQueryBuilder;
    }

    /** Visibility classes that gate which facets show up in which list. */
    private enum Visibility {
        MAIN, SECONDARY
    }

    public List<TurSNSiteSearchFacetBean> responseFacet(TurSNSiteSearchContext context,
            TurSNSite turSNSite,
            List<String> facetsInFilterQueries,
            Map<String, TurSNSiteFieldExtDto> facetMap,
            TurSEResults turSEResults) {
        return responseFacets(context, turSNSite, facetsInFilterQueries, facetMap, turSEResults,
                Visibility.MAIN);
    }

    public List<TurSNSiteSearchFacetBean> responseSecondaryFacet(TurSNSiteSearchContext context,
            TurSNSite turSNSite,
            List<String> facetsInFilterQueries,
            Map<String, TurSNSiteFieldExtDto> facetMap,
            TurSEResults turSEResults) {
        return responseFacets(context, turSNSite, facetsInFilterQueries, facetMap, turSEResults,
                Visibility.SECONDARY);
    }

    /**
     * Internal de-duplicated implementation. The two public entry points only
     * differ in the visibility predicate applied to facet results.
     */
    private List<TurSNSiteSearchFacetBean> responseFacets(TurSNSiteSearchContext context,
            TurSNSite turSNSite,
            List<String> facetsInFilterQueries,
            Map<String, TurSNSiteFieldExtDto> facetMap,
            TurSEResults turSEResults,
            Visibility visibility) {
        if (!facetIsEnabled(turSNSite, turSEResults)) {
            return Collections.emptyList();
        }
        Predicate<TurSEFacetResult> predicate = visibility == Visibility.MAIN
                ? f -> showMainFacet(facetsInFilterQueries, facetMap, f, turSNSite)
                : f -> showSecondaryFacet(facetsInFilterQueries, facetMap, f, turSNSite);

        List<TurSNSiteSearchFacetBean> beans = new ArrayList<>();
        turSEResults.getFacetResults().stream()
                .filter(predicate)
                .forEach(facet -> {
                    if (facetMap.containsKey(facet.getFacet())) {
                        TurSNFacetTypeContext typeContext = new TurSNFacetTypeContext(
                                facetMap.get(facet.getFacet()),
                                turSNSite,
                                context.getTurSEParameters().getTurSNFilterParams());
                        appendFacetItems(context, facetMap,
                                resolveFacetResult(context, turSEResults, typeContext),
                                beans);
                    }
                });
        return beans;
    }

    public TurSNSiteSearchFacetBean responseFacetToRemove(TurSNSiteSearchContext context,
            TurSNSite turSNSite) {
        if (CollectionUtils.isEmpty(context.getTurSEParameters().getTurSNFilterParams()
                .getDefaultValues())) {
            return new TurSNSiteSearchFacetBean();
        }
        TurSNFacetTypeContext typeContext = new TurSNFacetTypeContext(null, turSNSite,
                context.getTurSEParameters().getTurSNFilterParams());
        List<String> facetFieldsInFilterQuery = turSolrQueryBuilder
                .getFacetFieldsInFilterQuery(typeContext);
        List<TurSNSiteSearchFacetItemBean> removeItems = new ArrayList<>();
        context.getTurSEParameters().getTurSNFilterParams().getDefaultValues()
                .forEach(facetToRemove -> TurCommonsUtils.getKeyValueFromColon(facetToRemove)
                        .ifPresent(f -> {
                            if (facetFieldsInFilterQuery.contains(f.getKey())) {
                                removeItems.add(new TurSNSiteSearchFacetItemBean()
                                        .setLabel(f.getValue().replace("\"", ""))
                                        .setLink(TurSNUtils.removeFilterQuery(context.getUri(),
                                                facetToRemove).toString())
                                        .setFilterQuery(facetToRemove)
                                        .setSelected(true));
                            }
                        }));
        if (removeItems.isEmpty()) {
            return new TurSNSiteSearchFacetBean();
        }
        return facetsToRemoveBean(removeItems);
    }

    // ---- visibility helpers ---------------------------------------------

    private static boolean facetIsEnabled(TurSNSite turSNSite, TurSEResults turSEResults) {
        return TurSNUtils.isTrue(turSNSite.getFacet())
                && turSEResults.getFacetResults() != null
                && !turSEResults.getFacetResults().isEmpty();
    }

    private static boolean showFacet(List<String> facetsInFilterQueries,
            Map<String, TurSNSiteFieldExtDto> facetMap, TurSEFacetResult facet,
            TurSNSite turSNSite) {
        return facetMap.containsKey(facet.getFacet())
                && (!facetsInFilterQueries.contains(facet.getFacet())
                        || showFacetByFacetItemType(turSNSite))
                && !facet.getTurSEFacetResultAttr().isEmpty();
    }

    private static boolean showMainFacet(List<String> facetsInFilterQueries,
            Map<String, TurSNSiteFieldExtDto> facetMap, TurSEFacetResult facet,
            TurSNSite turSNSite) {
        Boolean secondary = facetMap.get(facet.getFacet()).getSecondaryFacet();
        return showFacet(facetsInFilterQueries, facetMap, facet, turSNSite)
                && (secondary == null || !secondary);
    }

    private static boolean showSecondaryFacet(List<String> facetsInFilterQueries,
            Map<String, TurSNSiteFieldExtDto> facetMap, TurSEFacetResult facet,
            TurSNSite turSNSite) {
        Boolean secondary = facetMap.get(facet.getFacet()).getSecondaryFacet();
        return showFacet(facetsInFilterQueries, facetMap, facet, turSNSite)
                && secondary != null && secondary;
    }

    private static boolean showFacetByFacetItemType(TurSNSite turSNSite) {
        if (turSNSite.getFacetItemType() == null) {
            return false;
        }
        return switch (turSNSite.getFacetItemType()) {
            case OR -> true;
            case AND, DEFAULT -> false;
        };
    }

    // ---- facet result resolution ----------------------------------------

    private FacetResult resolveFacetResult(TurSNSiteSearchContext context, TurSEResults turSEResults,
            TurSNFacetTypeContext typeContext) {
        String facetName = typeContext.getTurSNSiteFacetFieldExtDto().getName();
        String facetTypeAndFacetItemTypeValues = TurSolrQueryBuilder
                .getFacetTypeAndFacetItemTypeValues(typeContext);
        List<String> usedFacetItems = getUsedFacetItems(context);

        if (facetTypeAndFacetItemTypeValues.equals(AND_OR)
                && turSolrQueryBuilder.getFqFields(typeContext.getTurSNFilterParams())
                        .contains(facetName)) {
            TurSearchEnginePlugin plugin = searchEnginePluginFactory
                    .getPluginForSite(typeContext.getTurSNSite());
            TurSEFacetResult turSEFacetResult = plugin
                    .retrieveFacetResults(getContextSearchFacet(context, facetName), facetName)
                    .map(turSEFacetResults -> turSEFacetResults.getFacetResults().stream()
                            .filter(ff -> ff.getFacet().equals(facetName))
                            .findFirst()
                            .orElseGet(() -> getTurSEFacetResultDefault(turSEResults, facetName)))
                    .orElseGet(() -> getTurSEFacetResultDefault(turSEResults, facetName));
            return new FacetResult(usedFacetItems, facetTypeAndFacetItemTypeValues, turSEFacetResult);
        }
        TurSEFacetResult turSEFacetResult = getTurSEFacetResultDefault(turSEResults, facetName);
        return new FacetResult(usedFacetItems, facetTypeAndFacetItemTypeValues, turSEFacetResult);
    }

    @NotNull
    private static TurSNSiteSearchContext getContextSearchFacet(TurSNSiteSearchContext context,
            String facetName) {
        TurSNSiteSearchContext clone = SerializationUtils.clone(context);
        clone.getTurSEParameters().getTurSNFilterParams()
                .setDefaultValues(clone.getTurSEParameters().getTurSNFilterParams()
                        .getDefaultValues().stream()
                        .filter(fq -> !fq.startsWith(facetName)).toList());
        clone.getTurSEParameters().setRows(-1);
        return clone;
    }

    @NotNull
    private static List<String> getUsedFacetItems(TurSNSiteSearchContext context) {
        return Optional.ofNullable(context.getTurSEParameters())
                .map(TurSEParameters::getTurSNFilterParams)
                .map(TurSNFilterParams::getDefaultValues)
                .orElse(Collections.emptyList());
    }

    @NotNull
    private static TurSEFacetResult getTurSEFacetResultDefault(TurSEResults turSEResults,
            String facet) {
        return turSEResults.getFacetResults().stream()
                .filter(ff -> ff.getFacet().equals(facet)).findFirst()
                .orElse(new TurSEFacetResult());
    }

    // ---- bean assembly --------------------------------------------------

    private static void appendFacetItems(TurSNSiteSearchContext context,
            Map<String, TurSNSiteFieldExtDto> facetMap, FacetResult facetResult,
            List<TurSNSiteSearchFacetBean> beans) {
        if (!facetMap.containsKey(facetResult.turSEFacetResult.getFacet())) {
            return;
        }
        TurSNSiteFieldExtDto fieldExtDto = facetMap.get(facetResult.turSEFacetResult.getFacet());
        boolean showAllFacetItems = fieldExtDto.getShowAllFacetItems() != null
                && fieldExtDto.getShowAllFacetItems();
        List<TurSNSiteSearchFacetItemBean> items = new ArrayList<>();
        facetResult.turSEFacetResult.getTurSEFacetResultAttr().values().forEach(facetItem -> {
            String fq = facetResult.turSEFacetResult.getFacet() + ":" + facetItem.getAttribute();
            boolean inUseAndOr = facetResult.usedFacetItems.contains(fq)
                    && facetResult.facetTypeAndFacetItemTypeValues.equals(AND_OR);
            if (showAllFacetItems || facetItem.getCount() > 0 || inUseAndOr) {
                boolean selected = facetResult.usedFacetItems.contains(fq);
                String displayLabel = facetItem.getDisplayLabel() != null
                        && !facetItem.getDisplayLabel().isBlank()
                                ? facetItem.getDisplayLabel()
                                : facetItem.getAttribute();
                items.add(new TurSNSiteSearchFacetItemBean()
                        .setCount(facetItem.getCount())
                        .setLabel(displayLabel)
                        .setSelected(selected)
                        .setFilterQuery(fq)
                        .setLink(buildLink(context, selected, fq,
                                facetResult.facetTypeAndFacetItemTypeValues, fieldExtDto)));
            }
        });
        if (!items.isEmpty()) {
            beans.add(searchFacetBean(context, fieldExtDto, items));
        }
    }

    private static String buildLink(TurSNSiteSearchContext context, boolean selected, String fq,
            String facetTypeAndFacetItemTypeValues, TurSNSiteFieldExtDto fieldExtDto) {
        if (facetTypeAndFacetItemTypeValues.endsWith(FACET_ITEM_AND)) {
            if (selected) {
                return TurSNUtils.removeFilterQuery(context.getUri(), fq).toString();
            }
            URI uri = TurSNUtils.removeFilterQueryByFieldName(context.getUri(),
                    fieldExtDto.getName());
            return TurSNUtils.addFilterQuery(uri, fq).toString();
        }
        return selected
                ? TurSNUtils.removeFilterQuery(context.getUri(), fq).toString()
                : TurSNUtils.addFilterQuery(context.getUri(), fq).toString();
    }

    @NotNull
    private static TurSNSiteSearchFacetBean searchFacetBean(TurSNSiteSearchContext context,
            TurSNSiteFieldExtDto fieldExtDto,
            List<TurSNSiteSearchFacetItemBean> items) {
        TurSNSiteFieldExtFacetDto facetDto = fieldExtDto.getFacetLocales().stream()
                .filter(o -> o.getLocale().toString().equals(context.getLocale().toString()))
                .findFirst()
                .orElse(TurSNSiteFieldExtFacetDto.builder()
                        .locale(context.getLocale())
                        .label(fieldExtDto.getFacetName())
                        .build());
        return new TurSNSiteSearchFacetBean()
                .setLabel(new TurSNSiteSearchFacetLabelBean()
                        .setLang(context.getLocale().toString())
                        .setText(facetDto.getLabel()))
                .setName(fieldExtDto.getName())
                .setDescription(fieldExtDto.getDescription())
                .setType(fieldExtDto.getType())
                .setFacets(items)
                .setMultiValued(TurSNUtils.isTrue(fieldExtDto.getMultiValued()))
                .setCleanUpLink(TurSNUtils.removeFilterQueryByFieldName(context.getUri(),
                        fieldExtDto.getName()).toString())
                .setSelectedFilterQueries(TurSNUtils.filterQueryByFieldName(context.getUri(),
                        fieldExtDto.getName()));
    }

    @NotNull
    private static TurSNSiteSearchFacetBean facetsToRemoveBean(
            List<TurSNSiteSearchFacetItemBean> items) {
        return new TurSNSiteSearchFacetBean()
                .setLabel(new TurSNSiteSearchFacetLabelBean()
                        .setLang(TurSNUtils.DEFAULT_LANGUAGE)
                        .setText(FACETS_TO_REMOVE))
                .setFacets(items);
    }

    private record FacetResult(List<String> usedFacetItems,
            String facetTypeAndFacetItemTypeValues,
            TurSEFacetResult turSEFacetResult) {
    }
}
