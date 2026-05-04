/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.domain.sn.TurSNSiteCustomFacetDomain;
import com.viglet.turing.domain.sn.TurSNSiteCustomFacetItemDomain;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetItem;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetOperatorEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository;

/** Unit tests for {@link TurSNSiteCustomFacetRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurSNSiteCustomFacetRepositoryAdapterTest {

    @Mock
    private TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository;

    private TurSNSiteCustomFacetRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurSNSiteCustomFacetRepositoryAdapter(turSNSiteCustomFacetRepository,
                Mappers.getMapper(TurSNSiteCustomFacetDomainMapper.class));
    }

    @Test
    void findByIdProjectsLabelMapAndFullItemDomains() {
        Map<String, String> labels = Map.of("en_US", "Color", "pt_BR", "Cor");
        TurSNSiteCustomFacet entity = TurSNSiteCustomFacet.builder()
                .id("cf-1")
                .name("color")
                .defaultLabel("Color")
                .label(new java.util.HashMap<>(labels))
                .items(setOfItems("i-1", "i-2"))
                .turSNSiteFieldExt(stubFieldExt("f-1"))
                .build();
        when(turSNSiteCustomFacetRepository.findById("cf-1")).thenReturn(Optional.of(entity));

        TurSNSiteCustomFacetDomain domain = adapter.findById("cf-1").orElseThrow();

        assertThat(domain.id()).isEqualTo("cf-1");
        assertThat(domain.name()).isEqualTo("color");
        assertThat(domain.fieldExtId()).isEqualTo("f-1");
        assertThat(domain.label()).containsAllEntriesOf(labels);
        assertThat(domain.items()).extracting(TurSNSiteCustomFacetItemDomain::id)
                .containsExactlyInAnyOrder("i-1", "i-2");
        assertThat(domain.items()).extracting(TurSNSiteCustomFacetItemDomain::label)
                .containsExactlyInAnyOrder("label-i-1", "label-i-2");
    }

    @Test
    void itemPayloadIncludesRangesAndOperator() {
        TurSNSiteCustomFacetItem item = new TurSNSiteCustomFacetItem();
        item.setId("i-1");
        item.setLabel("0-100");
        item.setPosition(0);
        item.setRangeStart(java.math.BigDecimal.ZERO);
        item.setRangeEnd(java.math.BigDecimal.valueOf(100));
        item.setOperator(TurSNSiteCustomFacetOperatorEnum.values()[0]);
        TurSNSiteCustomFacet entity = TurSNSiteCustomFacet.builder()
                .id("cf-1")
                .name("price")
                .label(new java.util.HashMap<>())
                .items(java.util.Set.of(item))
                .turSNSiteFieldExt(stubFieldExt("f-1"))
                .build();
        when(turSNSiteCustomFacetRepository.findById("cf-1")).thenReturn(Optional.of(entity));

        TurSNSiteCustomFacetItemDomain projected = adapter.findById("cf-1").orElseThrow().items()
                .iterator().next();

        assertThat(projected.label()).isEqualTo("0-100");
        assertThat(projected.rangeStart()).isEqualByComparingTo(java.math.BigDecimal.ZERO);
        assertThat(projected.rangeEnd()).isEqualByComparingTo(java.math.BigDecimal.valueOf(100));
        assertThat(projected.operator()).isEqualTo(TurSNSiteCustomFacetOperatorEnum.values()[0]);
    }

    @Test
    void labelMapAndItemSetAreUnmodifiable() {
        TurSNSiteCustomFacet entity = TurSNSiteCustomFacet.builder()
                .id("cf-1")
                .name("color")
                .label(new java.util.HashMap<>(Map.of("en_US", "Color")))
                .items(setOfItems("i-1"))
                .turSNSiteFieldExt(stubFieldExt("f-1"))
                .build();
        when(turSNSiteCustomFacetRepository.findById("cf-1")).thenReturn(Optional.of(entity));

        TurSNSiteCustomFacetDomain domain = adapter.findById("cf-1").orElseThrow();

        assertThatThrownBy(() -> domain.items().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> domain.label().put("xx", "smuggled"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void findByFieldExtIdsWithDetailsBuildsStubsAndDelegates() {
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(List.of());

        adapter.findByFieldExtIdsWithDetails(List.of("f-1", "f-2"));

        verify(turSNSiteCustomFacetRepository).findByFieldExtsWithDetails(argThat(stubs -> {
            List<TurSNSiteFieldExt> list = (List<TurSNSiteFieldExt>) stubs;
            return list.size() == 2 && list.stream()
                    .map(TurSNSiteFieldExt::getId)
                    .toList()
                    .containsAll(List.of("f-1", "f-2"));
        }));
    }

    @Test
    void findByFieldExtIdWithDetailsBuildsStubAndDelegates() {
        when(turSNSiteCustomFacetRepository.findByFieldExtWithDetails(any()))
                .thenReturn(List.of());

        adapter.findByFieldExtIdWithDetails("f-1");

        verify(turSNSiteCustomFacetRepository)
                .findByFieldExtWithDetails(argThat(stub -> "f-1".equals(stub.getId())));
    }

    private static Set<TurSNSiteCustomFacetItem> setOfItems(String... ids) {
        Set<TurSNSiteCustomFacetItem> set = new HashSet<>();
        for (String id : ids) {
            TurSNSiteCustomFacetItem item = new TurSNSiteCustomFacetItem();
            item.setId(id);
            item.setLabel("label-" + id);
            set.add(item);
        }
        return set;
    }

    private static TurSNSiteFieldExt stubFieldExt(String id) {
        TurSNSiteFieldExt stub = new TurSNSiteFieldExt();
        stub.setId(id);
        return stub;
    }
}
