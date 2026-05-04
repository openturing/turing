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

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.domain.sn.TurSNSiteFieldExtFacetDomain;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtFacetRepository;

/** Unit tests for {@link TurSNSiteFieldExtFacetRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurSNSiteFieldExtFacetRepositoryAdapterTest {

    @Mock
    private TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository;

    private TurSNSiteFieldExtFacetRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurSNSiteFieldExtFacetRepositoryAdapter(turSNSiteFieldExtFacetRepository,
                Mappers.getMapper(TurSNSiteFieldExtFacetDomainMapper.class));
    }

    @Test
    void findByIdProjectsLocaleLabelAndFieldExtId() {
        TurSNSiteFieldExtFacet entity = TurSNSiteFieldExtFacet.builder()
                .id("fl-1")
                .locale(Locale.US)
                .label("Color")
                .turSNSiteFieldExt(stubField("f-1"))
                .build();
        when(turSNSiteFieldExtFacetRepository.findById("fl-1")).thenReturn(Optional.of(entity));

        TurSNSiteFieldExtFacetDomain domain = adapter.findById("fl-1").orElseThrow();

        assertThat(domain.id()).isEqualTo("fl-1");
        assertThat(domain.locale()).isEqualTo(Locale.US);
        assertThat(domain.label()).isEqualTo("Color");
        assertThat(domain.fieldExtId()).isEqualTo("f-1");
    }

    @Test
    void findByFieldExtIdQueriesByStubParent() {
        when(turSNSiteFieldExtFacetRepository.findByTurSNSiteFieldExt(any())).thenReturn(Set.of(
                TurSNSiteFieldExtFacet.builder().id("fl-1").locale(Locale.US).label("Color")
                        .turSNSiteFieldExt(stubField("f-1")).build()));

        Set<TurSNSiteFieldExtFacetDomain> result = adapter.findByFieldExtId("f-1");

        assertThat(result).hasSize(1);
        verify(turSNSiteFieldExtFacetRepository)
                .findByTurSNSiteFieldExt(argThat(stub -> "f-1".equals(stub.getId())));
    }

    @Test
    void findByFieldExtIdAndLocaleDelegatesWithLocaleArgument() {
        when(turSNSiteFieldExtFacetRepository.findByTurSNSiteFieldExtAndLocale(any(), any()))
                .thenReturn(Set.of());

        adapter.findByFieldExtIdAndLocale("f-1", Locale.US);

        verify(turSNSiteFieldExtFacetRepository).findByTurSNSiteFieldExtAndLocale(
                argThat(stub -> "f-1".equals(stub.getId())), eq(Locale.US));
    }

    private static TurSNSiteFieldExt stubField(String id) {
        TurSNSiteFieldExt stub = new TurSNSiteFieldExt();
        stub.setId(id);
        return stub;
    }
}
