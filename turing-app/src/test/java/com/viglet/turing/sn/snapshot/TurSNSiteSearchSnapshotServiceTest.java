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
