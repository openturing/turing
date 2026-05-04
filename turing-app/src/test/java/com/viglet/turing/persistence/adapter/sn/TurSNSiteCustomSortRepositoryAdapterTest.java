/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.domain.sn.TurSNSiteCustomSortDomain;
import com.viglet.turing.domain.sn.TurSNSiteCustomSortItemDomain;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSort;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSortItem;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSortOrderEnum;
import com.viglet.turing.persistence.repository.sn.sort.TurSNSiteCustomSortRepository;

/** Unit tests for {@link TurSNSiteCustomSortRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurSNSiteCustomSortRepositoryAdapterTest {

    @Mock
    private TurSNSiteCustomSortRepository turSNSiteCustomSortRepository;

    private TurSNSiteCustomSortRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurSNSiteCustomSortRepositoryAdapter(turSNSiteCustomSortRepository,
                Mappers.getMapper(TurSNSiteCustomSortDomainMapper.class));
    }

    @Test
    void findByIdProjectsScalarsAndItems() {
        TurSNSiteCustomSort entity = TurSNSiteCustomSort.builder()
                .id("cs-1")
                .name("relevance-and-date")
                .turSNSite(stub("site-1"))
                .items(Set.of(buildItem("i-1", "score", 0,
                        TurSNSiteCustomSortOrderEnum.DESC),
                        buildItem("i-2", "publishedAt", 1,
                                TurSNSiteCustomSortOrderEnum.DESC)))
                .build();
        when(turSNSiteCustomSortRepository.findById("cs-1")).thenReturn(Optional.of(entity));

        TurSNSiteCustomSortDomain domain = adapter.findById("cs-1").orElseThrow();

        assertThat(domain.name()).isEqualTo("relevance-and-date");
        assertThat(domain.snSiteId()).isEqualTo("site-1");
        assertThat(domain.items()).extracting(TurSNSiteCustomSortItemDomain::id)
                .containsExactlyInAnyOrder("i-1", "i-2");
        assertThat(domain.items()).extracting(TurSNSiteCustomSortItemDomain::sortOrder)
                .allMatch(order -> order == TurSNSiteCustomSortOrderEnum.DESC);
    }

    @Test
    void findBySnSiteIdAndNameQueriesByStubParentAndName() {
        when(turSNSiteCustomSortRepository.findByTurSNSiteAndName(any(), any()))
                .thenReturn(Optional.empty());

        adapter.findBySnSiteIdAndName("site-1", "relevance");

        verify(turSNSiteCustomSortRepository).findByTurSNSiteAndName(
                argThat(s -> "site-1".equals(s.getId())), eq("relevance"));
    }

    @Test
    void findBySnSiteIdWithItemsDelegatesToRepository() {
        when(turSNSiteCustomSortRepository.findByTurSNSiteWithItems(any())).thenReturn(List.of());

        adapter.findBySnSiteIdWithItems("site-1");

        verify(turSNSiteCustomSortRepository)
                .findByTurSNSiteWithItems(argThat(s -> "site-1".equals(s.getId())));
    }

    private static TurSNSiteCustomSortItem buildItem(String id, String fieldName, int position,
            TurSNSiteCustomSortOrderEnum sortOrder) {
        return TurSNSiteCustomSortItem.builder()
                .id(id)
                .fieldName(fieldName)
                .sortOrder(sortOrder)
                .position(position)
                .build();
    }

    private static TurSNSite stub(String id) {
        TurSNSite stub = new TurSNSite();
        stub.setId(id);
        return stub;
    }
}
