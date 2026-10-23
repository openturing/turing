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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.core.manifest.VigletFieldManifest;
import com.viglet.core.manifest.VigletFieldSpec;
import com.viglet.core.manifest.VigletFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSort;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSortItem;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSortMissingEnum;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSortOrderEnum;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.sort.TurSNSiteCustomSortRepository;

/**
 * T1104 — custom sorts declared in a manifest converge by name: created when
 * absent, left alone when equal, rewritten when their levels differ.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.5
 */
@ExtendWith(MockitoExtension.class)
class TurSNManifestCustomSortConvergerTest {

    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    @Mock
    private TurSNSiteCustomSortRepository turSNSiteCustomSortRepository;
    @InjectMocks
    private TurSNManifestCustomSortConverger converger;

    private TurSNSite site;

    @BeforeEach
    void setUp() {
        site = new TurSNSite();
        site.setId("site-1");
        site.setName("jobs");
    }

    private static TurSNManifestCustomSort closingSoonest() {
        return new TurSNManifestCustomSort("closingSoonest", "Closing soonest", List.of(
                new TurSNManifestCustomSortItem("expiresAt", TurSNSiteCustomSortOrderEnum.ASC,
                        TurSNSiteCustomSortMissingEnum.LAST),
                new TurSNManifestCustomSortItem("publishedAt", TurSNSiteCustomSortOrderEnum.DESC, null)));
    }

    private static VigletFieldManifest manifestWith(String... fields) {
        return new VigletFieldManifest("jobs", null, "se1", null, null,
                java.util.Arrays.stream(fields)
                        .map(name -> VigletFieldSpec.builder().name(name).type(VigletFieldType.DATE).build())
                        .toList(),
                null);
    }

    private static TurSNSiteCustomSort live(String description, TurSNSiteCustomSortItem... items) {
        TurSNSiteCustomSort sort = TurSNSiteCustomSort.builder().id("sort-1").name("closingSoonest")
                .description(description).build();
        sort.setItems(new HashSet<>(Set.of(items)));
        return sort;
    }

    private static TurSNSiteCustomSortItem item(String field, TurSNSiteCustomSortOrderEnum order,
            TurSNSiteCustomSortMissingEnum missing, int position) {
        return TurSNSiteCustomSortItem.builder().id("item-" + position).fieldName(field).sortOrder(order)
                .missing(missing).position(position).build();
    }

    @Test
    @DisplayName("An absent sort is created with its levels in order, missing placement included")
    void createsAnAbsentSort() {
        when(turSNSiteCustomSortRepository.findByTurSNSiteAndName(site, "closingSoonest"))
                .thenReturn(Optional.empty());

        assertThat(converger.converge(site, List.of(closingSoonest()))).containsExactly("closingSoonest");

        ArgumentCaptor<TurSNSiteCustomSort> saved = ArgumentCaptor.forClass(TurSNSiteCustomSort.class);
        verify(turSNSiteCustomSortRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("closingSoonest");
        assertThat(saved.getValue().getDescription()).isEqualTo("Closing soonest");
        assertThat(saved.getValue().getTurSNSite()).isSameAs(site);
        assertThat(saved.getValue().getItems())
                .extracting(TurSNSiteCustomSortItem::getFieldName, TurSNSiteCustomSortItem::getSortOrder,
                        TurSNSiteCustomSortItem::getMissing, TurSNSiteCustomSortItem::getPosition)
                .containsExactlyInAnyOrder(
                        tuple("expiresAt", TurSNSiteCustomSortOrderEnum.ASC, TurSNSiteCustomSortMissingEnum.LAST, 1),
                        tuple("publishedAt", TurSNSiteCustomSortOrderEnum.DESC, null, 2));
    }

    @Test
    @DisplayName("An equal sort is left alone, so re-posting the manifest is a no-op")
    void leavesAnEqualSortAlone() {
        when(turSNSiteCustomSortRepository.findByTurSNSiteAndName(site, "closingSoonest"))
                .thenReturn(Optional.of(live("Closing soonest",
                        item("expiresAt", TurSNSiteCustomSortOrderEnum.ASC, TurSNSiteCustomSortMissingEnum.LAST, 1),
                        item("publishedAt", TurSNSiteCustomSortOrderEnum.DESC, null, 2))));

        assertThat(converger.converge(site, List.of(closingSoonest()))).isEmpty();
        assertThat(converger.plan(site, List.of(closingSoonest()))).isEmpty();
        verify(turSNSiteCustomSortRepository, never()).save(any());
    }

    @Test
    @DisplayName("A sort whose missing placement differs is rewritten in place")
    void rewritesASortWhoseLevelsDiffer() {
        TurSNSiteCustomSort existing = live("Closing soonest",
                item("expiresAt", TurSNSiteCustomSortOrderEnum.ASC, null, 1),
                item("publishedAt", TurSNSiteCustomSortOrderEnum.DESC, null, 2));
        when(turSNSiteCustomSortRepository.findByTurSNSiteAndName(site, "closingSoonest"))
                .thenReturn(Optional.of(existing));

        assertThat(converger.plan(site, List.of(closingSoonest()))).containsExactly("closingSoonest");
        assertThat(converger.converge(site, List.of(closingSoonest()))).containsExactly("closingSoonest");

        verify(turSNSiteCustomSortRepository).save(existing);
        assertThat(existing.getItems()).extracting(TurSNSiteCustomSortItem::getMissing)
                .contains(TurSNSiteCustomSortMissingEnum.LAST);
    }

    @Test
    @DisplayName("A level on a field the manifest declares validates before the site exists")
    void validatesAgainstTheManifestFields() {
        converger.validate(null, manifestWith("expiresAt", "publishedAt"), List.of(closingSoonest()));
    }

    @Test
    @DisplayName("A level on a field neither the manifest nor the site declares is refused")
    void refusesAnUnknownField() {
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndName(site, "publishedAt")).thenReturn(List.of());

        assertThatThrownBy(() -> converger.validate(site, manifestWith("expiresAt"), List.of(closingSoonest())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("publishedAt");
    }

    @Test
    @DisplayName("A field already on the site satisfies a level the manifest does not redeclare")
    void acceptsALiveField() {
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndName(site, "publishedAt"))
                .thenReturn(List.of(new TurSNSiteFieldExt()));

        converger.validate(site, manifestWith("expiresAt"), List.of(closingSoonest()));
    }

    @Test
    @DisplayName("A sort named like a built-in sort is refused: a search would never reach it")
    void refusesAReservedName() {
        TurSNManifestCustomSort newest = new TurSNManifestCustomSort("Newest", null, List.of(
                new TurSNManifestCustomSortItem("expiresAt", null, null)));

        assertThatThrownBy(() -> converger.validate(null, manifestWith("expiresAt"), List.of(newest)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
    }

    @Test
    @DisplayName("A sort without levels, or declared twice, is refused")
    void refusesEmptyAndDuplicateSorts() {
        TurSNManifestCustomSort empty = new TurSNManifestCustomSort("closingSoonest", null, List.of());

        assertThatThrownBy(() -> converger.validate(null, manifestWith("expiresAt"), List.of(empty)))
                .hasMessageContaining("at least one level");
        assertThatThrownBy(() -> converger.validate(null, manifestWith("expiresAt", "publishedAt"),
                List.of(closingSoonest(), closingSoonest())))
                .hasMessageContaining("declared twice");
    }
}
