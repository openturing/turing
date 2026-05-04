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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.domain.sn.TurSNSiteFieldDomain;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldRepository;

/** Unit tests for {@link TurSNSiteFieldRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurSNSiteFieldRepositoryAdapterTest {

    @Mock
    private TurSNSiteFieldRepository turSNSiteFieldRepository;

    private TurSNSiteFieldRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurSNSiteFieldRepositoryAdapter(turSNSiteFieldRepository,
                Mappers.getMapper(TurSNSiteFieldDomainMapper.class));
    }

    @Test
    void findByIdProjectsScalarsAndSnSiteId() {
        TurSNSiteField entity = buildField("f-1", "title", TurSEFieldType.STRING, 0, "site-1");
        when(turSNSiteFieldRepository.findById("f-1")).thenReturn(Optional.of(entity));

        TurSNSiteFieldDomain domain = adapter.findById("f-1").orElseThrow();

        assertThat(domain.name()).isEqualTo("title");
        assertThat(domain.type()).isEqualTo(TurSEFieldType.STRING);
        assertThat(domain.snSiteId()).isEqualTo("site-1");
        assertThat(domain.isMultiValued()).isFalse();
    }

    @Test
    void isMultiValuedReflectsFlag() {
        TurSNSiteField entity = buildField("f-1", "tags", TurSEFieldType.STRING, 1, "site-1");
        when(turSNSiteFieldRepository.findById("f-1")).thenReturn(Optional.of(entity));

        TurSNSiteFieldDomain domain = adapter.findById("f-1").orElseThrow();

        assertThat(domain.isMultiValued()).isTrue();
    }

    @Test
    void findBySnSiteIdQueriesByStubParent() {
        when(turSNSiteFieldRepository.findByTurSNSite(any())).thenReturn(List.of());

        adapter.findBySnSiteId("site-1");

        verify(turSNSiteFieldRepository)
                .findByTurSNSite(argThat(s -> "site-1".equals(s.getId())));
    }

    @Test
    void existsBySnSiteIdAndNameDelegatesWithName() {
        when(turSNSiteFieldRepository.existsByTurSNSiteAndName(any(), any())).thenReturn(true);

        assertThat(adapter.existsBySnSiteIdAndName("site-1", "title")).isTrue();
        verify(turSNSiteFieldRepository).existsByTurSNSiteAndName(
                argThat(s -> "site-1".equals(s.getId())), eq("title"));
    }

    private static TurSNSiteField buildField(String id, String name, TurSEFieldType type,
            int multiValued, String snSiteId) {
        TurSNSiteField entity = TurSNSiteField.builder()
                .id(id)
                .name(name)
                .type(type)
                .multiValued(multiValued)
                .build();
        TurSNSite site = new TurSNSite();
        site.setId(snSiteId);
        entity.setTurSNSite(site);
        return entity;
    }
}
