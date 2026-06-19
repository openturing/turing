/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.domain.sn.TurSNSiteFieldExtDomain;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.sn.TurSNFieldType;

/** Unit tests for {@link TurSNSiteFieldExtRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurSNSiteFieldExtRepositoryAdapterTest {

    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    private TurSNSiteFieldExtRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurSNSiteFieldExtRepositoryAdapter(turSNSiteFieldExtRepository,
                Mappers.getMapper(TurSNSiteFieldExtDomainMapper.class));
    }

    @Test
    void findByIdProjectsScalarsAndChildIdSets() {
        TurSNSiteFieldExt entity = buildField("f-1", "title", "site-1");
        entity.setFacetLocales(setOfFacetLocales("fl-1", "fl-2"));
        entity.setCustomFacets(setOfCustomFacets("cf-1"));
        when(turSNSiteFieldExtRepository.findById("f-1")).thenReturn(Optional.of(entity));

        TurSNSiteFieldExtDomain domain = adapter.findById("f-1").orElseThrow();

        assertThat(domain.id()).isEqualTo("f-1");
        assertThat(domain.name()).isEqualTo("title");
        assertThat(domain.snSiteId()).isEqualTo("site-1");
        assertThat(domain.facetLocaleIds()).containsExactlyInAnyOrder("fl-1", "fl-2");
        assertThat(domain.customFacetIds()).containsExactly("cf-1");
        assertThat(domain.isEnabled()).isTrue();
    }

    @Test
    void behaviourMethodsReflectFlagFields() {
        TurSNSiteFieldExt entity = buildField("f-1", "title", "site-1");
        entity.setFacet(1);
        entity.setHl(1);
        entity.setMlt(1);
        entity.setMultiValued(1);
        entity.setRequired(1);
        when(turSNSiteFieldExtRepository.findById("f-1")).thenReturn(Optional.of(entity));

        TurSNSiteFieldExtDomain domain = adapter.findById("f-1").orElseThrow();

        assertThat(domain.isFacet()).isTrue();
        assertThat(domain.isHighlighted()).isTrue();
        assertThat(domain.isMltCandidate()).isTrue();
        assertThat(domain.isMultiValued()).isTrue();
        assertThat(domain.isRequired()).isTrue();
    }

    @Test
    void findBySnSiteIdAndEnabledQueriesByStubParent() {
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(any(), anyInt()))
                .thenReturn(List.of(buildField("f-1", "title", "site-1")));

        List<TurSNSiteFieldExtDomain> result = adapter.findBySnSiteIdAndEnabled("site-1", 1);

        assertThat(result).hasSize(1);
        verify(turSNSiteFieldExtRepository)
                .findByTurSNSiteAndEnabled(argThat(s -> "site-1".equals(s.getId())), eq(1));
    }

    @Test
    void existsBySnSiteIdAndNameDelegatesToRepository() {
        when(turSNSiteFieldExtRepository.existsByTurSNSiteAndName(any(), any())).thenReturn(true);

        assertThat(adapter.existsBySnSiteIdAndName("site-1", "title")).isTrue();
        verify(turSNSiteFieldExtRepository)
                .existsByTurSNSiteAndName(argThat(s -> "site-1".equals(s.getId())), eq("title"));
    }

    @Test
    void findBySnSiteIdSortsByNameAndDelegatesToRepository() {
        when(turSNSiteFieldExtRepository.findByTurSNSite(any(Sort.class), any()))
                .thenReturn(List.of(buildField("f-1", "title", "site-1")));

        List<TurSNSiteFieldExtDomain> result = adapter.findBySnSiteId("site-1");

        assertThat(result).hasSize(1);
    }

    @Test
    void existsBySnSiteIdAndHlAndEnabledIsTrueWhenAtLeastOneRow() {
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndHlAndEnabled(any(), anyInt(), anyInt()))
                .thenReturn(List.of(buildField("f-1", "title", "site-1")));

        assertThat(adapter.existsBySnSiteIdAndHlAndEnabled("site-1", 1, 1)).isTrue();
        verify(turSNSiteFieldExtRepository).findByTurSNSiteAndHlAndEnabled(
                argThat(s -> "site-1".equals(s.getId())), eq(1), eq(1));
    }

    @Test
    void existsBySnSiteIdAndHlAndEnabledIsFalseWhenEmpty() {
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndHlAndEnabled(any(), anyInt(), anyInt()))
                .thenReturn(List.of());

        assertThat(adapter.existsBySnSiteIdAndHlAndEnabled("site-1", 1, 1)).isFalse();
    }

    @Test
    void facetLocaleIdsAndCustomFacetIdsAreUnmodifiable() {
        TurSNSiteFieldExt entity = buildField("f-1", "title", "site-1");
        entity.setFacetLocales(setOfFacetLocales("fl-1"));
        entity.setCustomFacets(setOfCustomFacets("cf-1"));
        when(turSNSiteFieldExtRepository.findById("f-1")).thenReturn(Optional.of(entity));

        TurSNSiteFieldExtDomain domain = adapter.findById("f-1").orElseThrow();

        var facetLocaleIds = domain.facetLocaleIds();
        assertThatThrownBy(() -> facetLocaleIds.add("smuggled"))
                .isInstanceOf(UnsupportedOperationException.class);
        var customFacetIds = domain.customFacetIds();
        assertThatThrownBy(() -> customFacetIds.add("smuggled"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static TurSNSiteFieldExt buildField(String id, String name, String snSiteId) {
        TurSNSiteFieldExt entity = TurSNSiteFieldExt.builder()
                .id(id)
                .externalId("ext-" + id)
                .name(name)
                .snType(TurSNFieldType.SE)
                .type(TurSEFieldType.STRING)
                .enabled(1)
                .build();
        TurSNSite site = new TurSNSite();
        site.setId(snSiteId);
        entity.setTurSNSite(site);
        return entity;
    }

    private static Set<TurSNSiteFieldExtFacet> setOfFacetLocales(String... ids) {
        Set<TurSNSiteFieldExtFacet> set = new HashSet<>();
        for (String id : ids) {
            TurSNSiteFieldExtFacet locale = new TurSNSiteFieldExtFacet();
            locale.setId(id);
            set.add(locale);
        }
        return set;
    }

    private static Set<TurSNSiteCustomFacet> setOfCustomFacets(String... ids) {
        Set<TurSNSiteCustomFacet> set = new HashSet<>();
        for (String id : ids) {
            TurSNSiteCustomFacet cf = TurSNSiteCustomFacet.builder().id(id).name("cf-" + id)
                    .build();
            set.add(cf);
        }
        return set;
    }

}
