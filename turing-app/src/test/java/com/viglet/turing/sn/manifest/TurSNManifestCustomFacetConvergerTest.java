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

package com.viglet.turing.sn.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import com.viglet.core.manifest.VigletFieldManifest;
import com.viglet.core.manifest.VigletFieldSpec;
import com.viglet.core.manifest.VigletFieldType;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetItem;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.sn.facet.TurSNCustomFacetItems;

/**
 * T1084 — custom facets declared in a manifest converge by field and name: created
 * when absent, left alone when equal, rewritten when their buckets differ.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurSNManifestCustomFacetConvergerTest {

    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    @Mock
    private TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository;
    @InjectMocks
    private TurSNManifestCustomFacetConverger converger;

    private TurSNSite site;
    private TurSNSiteFieldExt publishedAt;

    @BeforeEach
    void setUp() {
        site = new TurSNSite();
        site.setId("site-1");
        site.setName("jobs");
        publishedAt = new TurSNSiteFieldExt();
        publishedAt.setId("field-1");
        publishedAt.setName("publishedAt");
        publishedAt.setType(TurSEFieldType.DATE);
    }

    private static TurSNManifestCustomFacetItem since(String label, String start) {
        return new TurSNManifestCustomFacetItem(label, null, null, null, null, null, start, null);
    }

    private static TurSNManifestCustomFacet postedSince(TurSNManifestCustomFacetItem... items) {
        return new TurSNManifestCustomFacet("publishedAt", "postedSince", "Posted",
                Map.of("pt", "Publicada"), null, List.of(items));
    }

    private static VigletFieldManifest manifestWithDateField() {
        return new VigletFieldManifest("jobs", null, "se1", null, null,
                List.of(VigletFieldSpec.builder().name("publishedAt").type(VigletFieldType.DATE).build()), null);
    }

    private void fieldIsLive() {
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndName(site, "publishedAt"))
                .thenReturn(List.of(publishedAt));
    }

    private TurSNSiteCustomFacet convergeOnce(TurSNManifestCustomFacet facet) {
        fieldIsLive();
        when(turSNSiteCustomFacetRepository.findByFieldExtWithDetails(publishedAt)).thenReturn(List.of());
        when(turSNSiteFieldExtRepository.findByTurSNSite(Sort.unsorted(), site)).thenReturn(List.of(publishedAt));
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(List.of(publishedAt))).thenReturn(List.of());

        assertThat(converger.converge(site, List.of(facet))).containsExactly("postedSince");

        ArgumentCaptor<TurSNSiteCustomFacet> saved = ArgumentCaptor.forClass(TurSNSiteCustomFacet.class);
        verify(turSNSiteCustomFacetRepository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    @DisplayName("An absent facet is created on its field, with relative date bounds stored as date math")
    void createsAnAbsentFacet() {
        TurSNSiteCustomFacet created = convergeOnce(postedSince(since("Last 24 hours", "NOW-1DAY"),
                since("Last 7 days", "now-7days")));

        assertThat(created.getName()).isEqualTo("postedSince");
        assertThat(created.getTurSNSiteFieldExt()).isSameAs(publishedAt);
        assertThat(created.getDefaultLabel()).isEqualTo("Posted");
        assertThat(created.getLabel()).containsEntry("pt", "Publicada");
        assertThat(created.getFacetPosition()).isEqualTo(1);
        assertThat(created.getItems())
                .extracting(TurSNSiteCustomFacetItem::getLabel, TurSNSiteCustomFacetItem::getPosition,
                        TurSNSiteCustomFacetItem::getRangeStartExpression,
                        TurSNSiteCustomFacetItem::getRangeStartDate)
                .containsExactlyInAnyOrder(
                        tuple("Last 24 hours", 1, "NOW-1DAY", null),
                        tuple("Last 7 days", 2, "NOW-7DAYS", null));
        assertThat(created.getItems()).allSatisfy(item -> assertThat(item.getTurSNSiteCustomFacet())
                .isSameAs(created));
    }

    @Test
    @DisplayName("Re-posting the same facet changes nothing")
    void equalFacetIsANoOp() {
        TurSNManifestCustomFacet facet = postedSince(since("Last 7 days", "NOW-7DAYS"));
        TurSNSiteCustomFacet live = convergeOnce(facet);
        when(turSNSiteCustomFacetRepository.findByFieldExtWithDetails(publishedAt)).thenReturn(List.of(live));

        assertThat(converger.converge(site, List.of(facet))).isEmpty();
        assertThat(converger.plan(site, manifestWithDateField(), List.of(facet))).isEmpty();
        verify(turSNSiteCustomFacetRepository).save(any());
    }

    @Test
    @DisplayName("A changed bucket rewrites the live facet's buckets in place")
    void changedBucketRewritesTheLiveFacet() {
        TurSNSiteCustomFacet live = convergeOnce(postedSince(since("Last 7 days", "NOW-7DAYS")));
        live.setId("facet-1");
        live.setFacetPosition(4);
        when(turSNSiteCustomFacetRepository.findByFieldExtWithDetails(publishedAt)).thenReturn(List.of(live));

        TurSNManifestCustomFacet changed = postedSince(since("Last 30 days", "NOW-30DAYS"));
        assertThat(converger.plan(site, manifestWithDateField(), List.of(changed)))
                .containsExactly("postedSince");
        assertThat(converger.converge(site, List.of(changed))).containsExactly("postedSince");

        assertThat(live.getId()).isEqualTo("facet-1");
        assertThat(live.getFacetPosition()).isEqualTo(4);
        assertThat(live.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getLabel()).isEqualTo("Last 30 days");
            assertThat(item.getRangeStartExpression()).isEqualTo("NOW-30DAYS");
        });
    }

    @Test
    @DisplayName("A numeric field keeps numeric bounds and fixed ISO dates parse on a DATE field")
    void numericAndFixedBounds() {
        TurSNSiteFieldExt salary = new TurSNSiteFieldExt();
        salary.setName("salary");
        salary.setType(TurSEFieldType.FLOAT);
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndName(site, "salary")).thenReturn(List.of(salary));
        when(turSNSiteCustomFacetRepository.findByFieldExtWithDetails(salary)).thenReturn(List.of());
        when(turSNSiteFieldExtRepository.findByTurSNSite(Sort.unsorted(), site)).thenReturn(List.of(salary));
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(List.of(salary))).thenReturn(List.of());

        converger.converge(site, List.of(new TurSNManifestCustomFacet("salary", "salaryBand", null, null, 7,
                List.of(new TurSNManifestCustomFacetItem("Up to 3k", null, null, null, null,
                        new BigDecimal("3000"), null, null)))));

        ArgumentCaptor<TurSNSiteCustomFacet> saved = ArgumentCaptor.forClass(TurSNSiteCustomFacet.class);
        verify(turSNSiteCustomFacetRepository).save(saved.capture());
        assertThat(saved.getValue().getFacetPosition()).isEqualTo(7);
        assertThat(saved.getValue().getItems()).singleElement()
                .satisfies(item -> assertThat(item.getRangeEnd()).isEqualByComparingTo("3000"));

        assertThat(TurSNCustomFacetItems.toEntity(null, "Since", null, 1, null, null, null, "2026-01-01",
                null, TurSEFieldType.DATE).getRangeStartDate())
                .isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("A facet on a field the site does not have is refused")
    void unknownFieldIsRefused() {
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndName(site, "publishedAt")).thenReturn(List.of());
        List<TurSNManifestCustomFacet> facets = List.of(postedSince(since("Week", "NOW-7DAYS")));

        assertThatThrownBy(() -> converger.converge(site, facets))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("publishedAt");
        verify(turSNSiteCustomFacetRepository, never()).save(any());
    }

    @Test
    @DisplayName("Validation refuses malformed date math, a missing item label and a duplicate facet")
    void validationRefusesWhatCannotBeStored() {
        VigletFieldManifest manifest = manifestWithDateField();
        List<TurSNManifestCustomFacet> badMath = List.of(postedSince(since("Week", "NOW-7BANANAS")));
        List<TurSNManifestCustomFacet> noLabel = List.of(postedSince(since(" ", "NOW-7DAYS")));
        TurSNManifestCustomFacet ok = postedSince(since("Week", "NOW-7DAYS"));
        List<TurSNManifestCustomFacet> duplicate = List.of(ok, ok);
        List<TurSNManifestCustomFacet> undeclaredField = List.of(new TurSNManifestCustomFacet(
                "salary", "band", null, null, null, List.of()));

        assertThatThrownBy(() -> converger.validate(null, manifest, badMath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("postedSince").hasMessageContaining("NOW-7BANANAS");
        assertThatThrownBy(() -> converger.validate(null, manifest, noLabel))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("label");
        assertThatThrownBy(() -> converger.validate(null, manifest, duplicate))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("twice");
        assertThatThrownBy(() -> converger.validate(null, manifest, undeclaredField))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("neither");
    }

    @Test
    @DisplayName("Planning a site that does not exist yet lists every declared facet, typed by the manifest")
    void planForANewSiteListsEveryFacet() {
        TurSNManifestCustomFacet facet = postedSince(since("Week", "NOW-7DAYS"));
        converger.validate(null, manifestWithDateField(), List.of(facet));

        assertThat(converger.plan(null, manifestWithDateField(), List.of(facet))).containsExactly("postedSince");
    }
}
