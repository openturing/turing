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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.viglet.turing.observability.TurSearchPipelineObservation;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNRankingMode;
import com.viglet.turing.persistence.model.sn.genai.TurSNKnowledgeBaseMode;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSort;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.sn.sort.TurSNSiteCustomSortRepository;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshot.CustomFacetView;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshot.CustomSortView;

/**
 * T898 / §LXXI.1 — the snapshot's projection step.
 *
 * <p>There was no test for {@code build(...)} at all, which is how two components
 * could be projected into records with nothing checking the values survived: the
 * existing suites construct {@code TurSNSiteSearchSnapshot} directly and always
 * with empty collections, so mapping every name to {@code null} left 166 tests
 * green. This covers the projection itself.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurSNSiteSearchSnapshotServiceTest {

    private static final String SITE_NAME = "intranet";
    private static final String FIELD_ID = "field-1";

    @Mock private TurSNSiteRepository turSNSiteRepository;
    @Mock private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    @Mock private TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository;
    @Mock private TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository;
    @Mock private TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    @Mock private TurSNSiteCustomSortRepository turSNSiteCustomSortRepository;
    @Mock private TurSearchPipelineObservation pipelineObservation;

    private TurSNSiteSearchSnapshotService service;

    private static TurSNSite site() {
        TurSNSite site = new TurSNSite();
        site.setId("site-1");
        site.setName(SITE_NAME);
        return site;
    }

    private static TurSNSiteFieldExt field() {
        TurSNSiteFieldExt field = new TurSNSiteFieldExt();
        field.setId(FIELD_ID);
        field.setName("category");
        field.setEnabled(1);
        field.setHl(0);
        return field;
    }

    private static TurSNSiteCustomFacet customFacet(String id, String name) {
        TurSNSiteCustomFacet facet = new TurSNSiteCustomFacet();
        facet.setId(id);
        facet.setName(name);
        facet.setDefaultLabel("ignored by the search flow");
        facet.setTurSNSiteFieldExt(field());
        return facet;
    }

    private static TurSNSiteCustomSort customSort(String id, String name, String description) {
        TurSNSiteCustomSort sort = new TurSNSiteCustomSort();
        sort.setId(id);
        sort.setName(name);
        sort.setDescription(description);
        return sort;
    }

    @BeforeEach
    void wire() {
        service = new TurSNSiteSearchSnapshotService(turSNSiteRepository,
                turSNSiteFieldExtRepository, turSNSiteFieldExtFacetRepository,
                turSNSiteCustomFacetRepository, turSNSiteLocaleRepository,
                turSNSiteCustomSortRepository, pipelineObservation);

        // The observation wrapper is a pass-through here.
        when(pipelineObservation.record(anyString(), any(Supplier.class)))
                .thenAnswer(invocation -> invocation.getArgument(1, Supplier.class).get());

        when(turSNSiteRepository.findByNameIgnoreCaseWithGenAi(SITE_NAME))
                .thenReturn(Optional.of(site()));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(any(), anyInt()))
                .thenReturn(List.of(field()));
        when(turSNSiteLocaleRepository.findByTurSNSiteOrderByPositionAsc(any()))
                .thenReturn(List.of());
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(List.of());
        when(turSNSiteCustomSortRepository.findByTurSNSiteWithItems(any()))
                .thenReturn(List.of());
    }

    private TurSNSiteSearchSnapshot snapshot() {
        return service.getSnapshot(SITE_NAME, null).orElseThrow();
    }

    // ---- custom sorts ------------------------------------------------------

    @Test
    void projectsEveryCustomSortFieldTheConsumerReads() {
        when(turSNSiteCustomSortRepository.findByTurSNSiteWithItems(any()))
                .thenReturn(List.of(customSort("s1", "price-asc", "Cheapest first")));

        assertThat(snapshot().customSorts()).singleElement().satisfies(sort -> {
            assertThat(sort.id()).isEqualTo("s1");
            assertThat(sort.name()).isEqualTo("price-asc");
            assertThat(sort.description()).isEqualTo("Cheapest first");
        });
    }

    @Test
    void keepsANullCustomSortDescription() {
        // TurSNSiteSearchAPI#sortOptions falls back to the name when the description
        // is absent, so the projection must not invent one.
        when(turSNSiteCustomSortRepository.findByTurSNSiteWithItems(any()))
                .thenReturn(List.of(customSort("s1", "newest", null)));

        assertThat(snapshot().customSorts()).singleElement()
                .satisfies(sort -> assertThat(sort.description()).isNull());
    }

    @Test
    void projectsEveryCustomSortInOrder() {
        when(turSNSiteCustomSortRepository.findByTurSNSiteWithItems(any()))
                .thenReturn(List.of(customSort("s1", "a", "A"), customSort("s2", "b", "B")));

        assertThat(snapshot().customSorts()).extracting(CustomSortView::name)
                .containsExactly("a", "b");
    }

    // ---- custom facets -----------------------------------------------------

    @Test
    void projectsCustomFacetsGroupedByTheirFieldId() {
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(List.of(customFacet("cf1", "brand"), customFacet("cf2", "colour")));

        TurSNSiteSearchSnapshot snapshot = snapshot();

        assertThat(snapshot.customFacetsByFieldId()).containsOnlyKeys(FIELD_ID);
        assertThat(snapshot.customFacetsByFieldId().get(FIELD_ID))
                .extracting(CustomFacetView::name)
                .containsExactly("brand", "colour");
        assertThat(snapshot.customFacetsByFieldId().get(FIELD_ID))
                .extracting(CustomFacetView::id)
                .containsExactly("cf1", "cf2");
    }

    @Test
    void derivesFieldIdsWithCustomFacetsFromTheProjectedMap() {
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(List.of(customFacet("cf1", "brand")));

        TurSNSiteSearchSnapshot snapshot = snapshot();

        assertThat(snapshot.fieldIdsWithCustomFacets())
                .isEqualTo(snapshot.customFacetsByFieldId().keySet());
    }

    @Test
    void leavesBothProjectionsEmptyWhenThereIsNothingToProject() {
        TurSNSiteSearchSnapshot snapshot = snapshot();

        assertThat(snapshot.customFacetsByFieldId()).isEmpty();
        assertThat(snapshot.customSorts()).isEmpty();
        assertThat(snapshot.fieldIdsWithCustomFacets()).isEmpty();
    }

    // ---- facet labels ------------------------------------------------------

    @Test
    void projectsFacetLabelsPerFieldWithWhatTheRendererReads() {
        TurSNSiteFieldExtFacet label = TurSNSiteFieldExtFacet.builder()
                .id("fl-1").locale(Locale.US).label("Category").build();
        when(turSNSiteFieldExtFacetRepository
                .findByTurSNSiteFieldExtAndLocale(any(), any()))
                .thenReturn(java.util.Set.of(label));

        TurSNSiteSearchSnapshot snapshot =
                service.getSnapshot(SITE_NAME, Locale.US).orElseThrow();

        assertThat(snapshot.facetLabelsByFieldId()).containsOnlyKeys(FIELD_ID);
        assertThat(snapshot.facetLabelsByFieldId().get(FIELD_ID)).singleElement()
                .satisfies(view -> {
                    // TurSNFacetRenderer#groupLabel matches on the locale and reads the
                    // label; the entity's id is read by nothing, so it is not projected.
                    assertThat(view.locale()).isEqualTo(Locale.US);
                    assertThat(view.label()).isEqualTo("Category");
                });
    }

    @Test
    void readsNoFacetLabelsWhenTheRequestNamesNoLocale() {
        // The build skips the per-field label query entirely for a null locale, which
        // is what lets a site-level caller share the "*" cache slot.
        assertThat(snapshot().facetLabelsByFieldId()).isEmpty();
        org.mockito.Mockito.verify(turSNSiteFieldExtFacetRepository,
                org.mockito.Mockito.never())
                .findByTurSNSiteFieldExtAndLocale(any(), any());
    }

    // ---- locales -----------------------------------------------------------

    private static TurSNSiteLocale snLocale(Locale language, String core, int position) {
        TurSNSiteLocale locale = new TurSNSiteLocale();
        locale.setId("loc-" + position);
        locale.setLanguage(language);
        locale.setCore(core);
        locale.setPosition(position);
        return locale;
    }

    @Test
    void projectsEveryLocaleFieldThePluginBoundaryNeeds() {
        when(turSNSiteLocaleRepository.findByTurSNSiteOrderByPositionAsc(any()))
                .thenReturn(List.of(snLocale(Locale.US, "site_en", 0)));

        assertThat(snapshot().allLocales()).singleElement().satisfies(view -> {
            assertThat(view.language()).isEqualTo(Locale.US);
            // core is how every plugin addresses the index; the similar-documents and
            // clustering paths rebuild a TurSNSiteLocale from this record.
            assertThat(view.core()).isEqualTo("site_en");
            assertThat(view.position()).isZero();
        });
    }

    @Test
    void keepsTheLocaleOrderTheQueryReturned() {
        // TurSNSiteSearchService#resolveDefaultLocale takes findFirst(), so the
        // ORDER BY position of the query is load-bearing, not incidental.
        when(turSNSiteLocaleRepository.findByTurSNSiteOrderByPositionAsc(any()))
                .thenReturn(List.of(snLocale(Locale.US, "site_en", 0),
                        snLocale(Locale.forLanguageTag("pt-BR"), "site_pt", 1)));

        assertThat(snapshot().allLocales())
                .extracting(TurSNSiteSearchSnapshot.LocaleView::core)
                .containsExactly("site_en", "site_pt");
    }

    // ---- the field-ext copy ------------------------------------------------

    /**
     * T898 / §LXXI.1 — {@code FieldExtView} is the one projection that copies
     * rather than narrows, because its consumers hand the value on instead of
     * reading it. A copy is exactly where a forgotten field changes behaviour in
     * silence, so this walks every declared scalar by reflection: populate it,
     * project, rebuild, and compare. Adding a column to the entity without adding
     * it to the record fails here rather than in production.
     */
    @Test
    void everyScalarSurvivesTheProjectionAndTheRebuild() throws Exception {
        TurSNSiteFieldExt original = new TurSNSiteFieldExt();
        List<java.lang.reflect.Field> scalars = new java.util.ArrayList<>();
        java.util.Map<Class<?>, Integer> seenPerType = new java.util.HashMap<>();
        for (java.lang.reflect.Field declared : TurSNSiteFieldExt.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(declared.getModifiers())
                    || java.util.Collection.class.isAssignableFrom(declared.getType())
                    || declared.getType() == TurSNSite.class) {
                continue; // associations are deliberately not carried
            }
            declared.setAccessible(true);
            // Counted per type, not hashed: facetType and facetItemType are the same
            // enum, and a hash gave them the same constant — which made a swap of the
            // two invisible. Found by injecting exactly that swap.
            int nth = seenPerType.merge(declared.getType(), 1, Integer::sum) - 1;
            declared.set(original, sampleFor(declared.getType(), declared.getName(), nth));
            scalars.add(declared);
        }
        assertThat(scalars).as("the entity must still have scalars to copy").hasSize(21);

        TurSNSiteFieldExt rebuilt = TurSNSiteSearchSnapshotService.toEntity(
                TurSNSiteSearchSnapshotService.toView(original), site());

        for (java.lang.reflect.Field declared : scalars) {
            assertThat(declared.get(rebuilt))
                    .as("'%s' was lost between the entity, the record and the rebuild",
                            declared.getName())
                    .isEqualTo(declared.get(original));
        }
    }

    /**
     * A value distinct from every other field of the same type, so swapping two
     * same-typed fields is visible and not only losing one. {@code nth} is the
     * field's ordinal among those of its type — a hash is not enough, because two
     * fields can hash to the same enum constant, and two of these do.
     */
    private static Object sampleFor(Class<?> type, String name, int nth) {
        if (type == String.class) {
            return "value-of-" + name;
        }
        if (type == int.class) {
            return nth + 1;
        }
        if (type == Integer.class) {
            return nth + 101;
        }
        if (type == Boolean.class) {
            return nth % 2 == 0;
        }
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            if (nth >= constants.length) {
                throw new IllegalStateException(type.getSimpleName() + " has fewer constants ("
                        + constants.length + ") than fields using it, so this test cannot give "
                        + "each a distinct value — a swap between them would be invisible.");
            }
            return constants[nth];
        }
        throw new IllegalStateException(
                "TurSNSiteFieldExt gained a '" + type.getSimpleName() + "' field ('" + name
                        + "') this test does not know how to populate — extend sampleFor "
                        + "and check FieldExtView carries it.");
    }

    @Test
    void theRebuiltFieldCarriesTheSiteTheDtoCopies() {
        TurSNSiteFieldExt field = field();
        TurSNSite site = site();

        TurSNSiteFieldExt rebuilt = TurSNSiteSearchSnapshotService.toEntity(
                TurSNSiteSearchSnapshotService.toView(field), site);

        assertThat(rebuilt.getTurSNSite()).isSameAs(site);
    }

    @Test
    void projectsTheEnabledFieldsIntoRecords() {
        TurSNSiteSearchSnapshot snapshot = snapshot();

        assertThat(snapshot.enabledFields()).singleElement().satisfies(view -> {
            assertThat(view.id()).isEqualTo(FIELD_ID);
            assertThat(view.name()).isEqualTo("category");
            assertThat(view.enabled()).isEqualTo(1);
        });
    }

    // ---- the site copy (T953) ----------------------------------------------

    /**
     * T953 / §LXXI.7 — the same proof {@code FieldExtView} gets, for the bigger
     * copy. Every declared scalar is populated, projected into
     * {@code TurSNSiteSearchSiteView} and rebuilt, then compared. A column added
     * to {@code TurSNSite} without being carried fails here rather than becoming
     * a null on the hottest read in the product.
     */
    @Test
    void everySiteScalarSurvivesTheProjectionAndTheRebuild() throws Exception {
        TurSNSite original = new TurSNSite();
        List<java.lang.reflect.Field> scalars = new java.util.ArrayList<>();
        java.util.Map<Class<?>, Integer> seenPerType = new java.util.HashMap<>();
        for (java.lang.reflect.Field declared : TurSNSite.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(declared.getModifiers())
                    || java.util.Collection.class.isAssignableFrom(declared.getType())
                    || declared.getType() == TurSEInstance.class
                    || declared.getType() == TurSNSiteGenAi.class) {
                continue; // the two associations are asserted separately below
            }
            declared.setAccessible(true);
            int nth = seenPerType.merge(declared.getType(), 1, Integer::sum) - 1;
            declared.set(original, sampleFor(declared.getType(), declared.getName(), nth));
            scalars.add(declared);
        }
        assertThat(scalars).as("the site must still have scalars to copy").hasSize(32);

        TurSNSite rebuilt = TurSNSiteSearchSiteView.from(original, "solr").toEntity();

        for (java.lang.reflect.Field declared : scalars) {
            assertThat(declared.get(rebuilt))
                    .as("'%s' was lost between the site, the view and the rebuild",
                            declared.getName())
                    .isEqualTo(declared.get(original));
        }
    }

    @Test
    void theRebuiltSiteCarriesWhatTheRankingServiceReads() {
        TurSNSite original = site();
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setId("genai-1");
        genAi.setKnowledgeBaseMode(TurSNKnowledgeBaseMode.VECTOR);
        genAi.setEmbeddingModelId("emb-1");
        genAi.setSnRankingMode(TurSNRankingMode.HYBRID_RRF);
        original.setTurSNSiteGenAi(genAi);

        TurSNSite rebuilt = TurSNSiteSearchSiteView.from(original, "solr").toEntity();

        // TurSNHybridRankingService reads exactly these three off the site.
        assertThat(rebuilt.getTurSNSiteGenAi()).isNotNull();
        assertThat(rebuilt.getTurSNSiteGenAi().getKnowledgeBaseMode())
                .isEqualTo(TurSNKnowledgeBaseMode.VECTOR);
        assertThat(rebuilt.getTurSNSiteGenAi().getEmbeddingModelId()).isEqualTo("emb-1");
        assertThat(rebuilt.getTurSNSiteGenAi().getSnRankingMode())
                .isEqualTo(TurSNRankingMode.HYBRID_RRF);
    }

    @Test
    void theRebuiltSiteAnswersThePluginLookupIdentically() {
        // getPluginForSite resolves vendor.plugin, falling back to vendor.id. The
        // snapshot already resolved both into pluginType, so the rebuilt vendor
        // must answer with it or every search silently falls back to Solr.
        TurSNSite rebuilt = TurSNSiteSearchSiteView.from(site(), "lucene").toEntity();

        assertThat(rebuilt.getTurSEInstance()).isNotNull();
        assertThat(rebuilt.getTurSEInstance().getTurSEVendor().getPlugin()).isEqualTo("lucene");
    }

    @Test
    void theRebuiltSiteHasEmptyCollectionsRatherThanProxies() {
        // The reason this cannot regress anything: on the cached entity these were
        // uninitialised proxies that threw (TurPersonaSnDocResolver documents it).
        TurSNSite rebuilt = TurSNSiteSearchSiteView.from(site(), "solr").toEntity();

        assertThat(rebuilt.getTurSNSiteLocales()).isEmpty();
        assertThat(rebuilt.getTurSNSiteFieldExts()).isEmpty();
        assertThat(rebuilt.getTurSNSiteSpotlights()).isEmpty();
    }

    // ---- the invariant the projection exists for ---------------------------

    @Test
    void neitherProjectionCarriesAJpaEntity() {
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(List.of(customFacet("cf1", "brand")));
        when(turSNSiteCustomSortRepository.findByTurSNSiteWithItems(any()))
                .thenReturn(List.of(customSort("s1", "newest", null)));

        TurSNSiteSearchSnapshot snapshot = snapshot();

        // The whole point of T898: what the cache holds carries no lazy proxy. The
        // corresponding lint (TurCacheAnnotationConventionsTest) checks this
        // statically for every @Cacheable; this pins the two converted components.
        assertThat(snapshot.customSorts()).allSatisfy(sort -> assertThat(sort.getClass())
                .isEqualTo(CustomSortView.class));
        assertThat(snapshot.customFacetsByFieldId().values()).allSatisfy(
                views -> assertThat(views).allSatisfy(view -> assertThat(view.getClass())
                        .isEqualTo(CustomFacetView.class)));
    }

    @Test
    void anAbsentSiteYieldsNoSnapshot() {
        when(turSNSiteRepository.findByNameIgnoreCaseWithGenAi("missing"))
                .thenReturn(Optional.empty());

        assertThat(service.getSnapshot("missing", null)).isEmpty();
    }

    @Test
    void cacheKeyCollapsesANullLocaleAndLowercasesTheSiteName() {
        assertThat(TurSNSiteSearchSnapshotService.cacheKey("Intranet", null))
                .isEqualTo("intranet:*");
        assertThat(TurSNSiteSearchSnapshotService.cacheKey("Intranet", Locale.forLanguageTag("pt-BR")))
                .isEqualTo("intranet:pt_BR");
    }
}
