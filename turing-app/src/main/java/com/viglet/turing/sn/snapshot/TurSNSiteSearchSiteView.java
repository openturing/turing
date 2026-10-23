/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn.snapshot;

import java.io.Serial;
import java.io.Serializable;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteApiAuthMode;
import com.viglet.turing.persistence.model.sn.TurSNSiteFacetSortEnum;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.sn.genai.TurSNKnowledgeBaseMode;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.genai.TurSNRankingMode;

/**
 * The SN site as the cache holds it (T953 / §LXXI.7) — every scalar, plus the
 * three GenAI fields and the resolved plugin type its collaborators reach for.
 *
 * <p>This is the last of the snapshot's six components to stop being an entity.
 * Like {@code FieldExtView} it is a <strong>copy</strong> rather than a
 * narrowing, and for the same reason: its consumers do not read it, they hand it
 * to sixty-five parameters typed on {@code TurSNSite} across nine files, many of
 * which also serve indexing and console callers holding a managed instance.
 * {@link #toEntity()} rebuilds a transient one at that boundary so not one of
 * those signatures changes.
 *
 * <p>Two associations ride along as transients rather than entities:
 * <ul>
 *   <li>{@code turSNSiteGenAi} — {@code TurSNHybridRankingService} reads exactly
 *       seven fields off it ({@code knowledgeBaseMode}, {@code embeddingModelId},
 *       {@code snRankingMode}, T1103's {@code snSemanticRecall} +
 *       {@code snSemanticRecallMinScore}, and T1105's
 *       {@code snHybridVectorWeight} + {@code snEmbeddedFields}), so those are
 *       carried and the rest is not.</li>
 *   <li>{@code turSEInstance} — reached only by
 *       {@code TurSearchEnginePluginFactory#getPluginForSite}, which resolves the
 *       vendor's {@code plugin} (falling back to its id). The snapshot already
 *       computes exactly that as {@code pluginType}, so the rebuilt vendor
 *       carries it and the lookup answers identically. Nothing else on this path
 *       touches the SE instance — {@code TurSolrInstanceProcess} loads its own
 *       site by name, so the Solr client is not built from here.</li>
 * </ul>
 *
 * <p>The copy is proved rather than asserted: the reflective round-trip test in
 * {@code TurSNSiteSearchSnapshotServiceTest} populates every declared scalar and
 * compares every getter after the trip, so a column added to the entity without
 * being added here fails the suite instead of a request.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNSiteSearchSiteView(
        String id, String tenantId, String name, String description, String icon,
        Integer rowsPerPage, Integer wildcardNoResults, Integer wildcardAlways,
        Integer exactMatch, Integer facet, Integer itemsPerFacet, Integer hl,
        String hlPre, String hlPost, Integer mlt,
        TurSNSiteFacetFieldEnum facetType, TurSNSiteFacetFieldEnum facetItemType,
        TurSNSiteFacetSortEnum facetSort, Integer thesaurus,
        String defaultField, String exactMatchField, String defaultTitleField,
        String defaultTextField, String defaultDescriptionField, String defaultDateField,
        String defaultImageField, String defaultURLField,
        Integer spellCheck, Integer spellCheckFixes, Integer spotlightWithResults,
        String searchTemplate, TurSNSiteApiAuthMode apiAuthMode,
        // ---- the two associations, denormalised to what is read ----
        String genAiId, TurSNKnowledgeBaseMode knowledgeBaseMode, String embeddingModelId,
        TurSNRankingMode snRankingMode, boolean snSemanticRecall, Double snSemanticRecallMinScore,
        Double snHybridVectorWeight, String snEmbeddedFields,
        String seInstanceId, String pluginType) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Projected in-session, while the two associations are still initialised. */
    public static TurSNSiteSearchSiteView from(TurSNSite site, String pluginType) {
        TurSNSiteGenAi genAi = site.getTurSNSiteGenAi();
        TurSEInstance seInstance = site.getTurSEInstance();
        return new TurSNSiteSearchSiteView(
                site.getId(), site.getTenantId(), site.getName(), site.getDescription(),
                site.getIcon(), site.getRowsPerPage(), site.getWildcardNoResults(),
                site.getWildcardAlways(), site.getExactMatch(), site.getFacet(),
                site.getItemsPerFacet(), site.getHl(), site.getHlPre(), site.getHlPost(),
                site.getMlt(), site.getFacetType(), site.getFacetItemType(),
                site.getFacetSort(), site.getThesaurus(), site.getDefaultField(),
                site.getExactMatchField(), site.getDefaultTitleField(),
                site.getDefaultTextField(), site.getDefaultDescriptionField(),
                site.getDefaultDateField(), site.getDefaultImageField(),
                site.getDefaultURLField(), site.getSpellCheck(), site.getSpellCheckFixes(),
                site.getSpotlightWithResults(), site.getSearchTemplate(),
                site.getApiAuthMode(),
                genAi == null ? null : genAi.getId(),
                genAi == null ? null : genAi.getKnowledgeBaseMode(),
                genAi == null ? null : genAi.getEmbeddingModelId(),
                genAi == null ? null : genAi.getSnRankingMode(),
                genAi != null && genAi.isSnSemanticRecall(),
                genAi == null ? null : genAi.getSnSemanticRecallMinScore(),
                genAi == null ? null : genAi.getSnHybridVectorWeight(),
                genAi == null ? null : genAi.getSnEmbeddedFields(),
                seInstance == null ? null : seInstance.getId(),
                pluginType);
    }

    /**
     * A transient {@code TurSNSite} for the collaborators typed on it. Its nine
     * lazy collections are empty rather than proxies, which is why this cannot
     * regress anything: on the cached instance they threw.
     */
    public TurSNSite toEntity() {
        TurSNSite site = new TurSNSite();
        site.setId(id);
        site.setTenantId(tenantId);
        site.setName(name);
        site.setDescription(description);
        site.setIcon(icon);
        site.setRowsPerPage(rowsPerPage);
        site.setWildcardNoResults(wildcardNoResults);
        site.setWildcardAlways(wildcardAlways);
        site.setExactMatch(exactMatch);
        site.setFacet(facet);
        site.setItemsPerFacet(itemsPerFacet);
        site.setHl(hl);
        site.setHlPre(hlPre);
        site.setHlPost(hlPost);
        site.setMlt(mlt);
        site.setFacetType(facetType);
        site.setFacetItemType(facetItemType);
        site.setFacetSort(facetSort);
        site.setThesaurus(thesaurus);
        site.setDefaultField(defaultField);
        site.setExactMatchField(exactMatchField);
        site.setDefaultTitleField(defaultTitleField);
        site.setDefaultTextField(defaultTextField);
        site.setDefaultDescriptionField(defaultDescriptionField);
        site.setDefaultDateField(defaultDateField);
        site.setDefaultImageField(defaultImageField);
        site.setDefaultURLField(defaultURLField);
        site.setSpellCheck(spellCheck);
        site.setSpellCheckFixes(spellCheckFixes);
        site.setSpotlightWithResults(spotlightWithResults);
        site.setSearchTemplate(searchTemplate);
        site.setApiAuthMode(apiAuthMode);
        site.setTurSNSiteGenAi(genAiOrNull());
        site.setTurSEInstance(seInstanceOrNull());
        return site;
    }

    private TurSNSiteGenAi genAiOrNull() {
        if (genAiId == null && knowledgeBaseMode == null && embeddingModelId == null
                && snRankingMode == null) {
            return null;
        }
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setId(genAiId);
        genAi.setKnowledgeBaseMode(knowledgeBaseMode);
        genAi.setEmbeddingModelId(embeddingModelId);
        genAi.setSnRankingMode(snRankingMode);
        genAi.setSnSemanticRecall(snSemanticRecall);
        genAi.setSnSemanticRecallMinScore(snSemanticRecallMinScore);
        genAi.setSnHybridVectorWeight(snHybridVectorWeight);
        genAi.setSnEmbeddedFields(snEmbeddedFields);
        return genAi;
    }

    private TurSEInstance seInstanceOrNull() {
        if (seInstanceId == null && pluginType == null) {
            return null;
        }
        TurSEVendor vendor = new TurSEVendor();
        // getPluginForSite reads plugin, falling back to the vendor id — the
        // snapshot already resolved both into pluginType, so setting it answers
        // the lookup identically without carrying the vendor row.
        vendor.setId(pluginType);
        vendor.setPlugin(pluginType);
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setId(seInstanceId);
        seInstance.setTurSEVendor(vendor);
        return seInstance;
    }
}
