/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.domain.sn.TurSNSiteDomain;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;

/**
 * Unit tests for {@link TurSNSiteRepositoryAdapter}, exercising the lookup
 * methods and the entity-to-domain mapping (including the projected
 * neighbouring aggregate IDs).
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteRepositoryAdapterTest {

    @Mock
    private TurSNSiteRepository turSNSiteRepository;

    private TurSNSiteRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        // Use the MapStruct-generated implementation so the adapter exercises real mapping.
        adapter = new TurSNSiteRepositoryAdapter(turSNSiteRepository,
                Mappers.getMapper(TurSNSiteDomainMapper.class));
    }

    @Test
    void findByNameReturnsMappedDomain() {
        when(turSNSiteRepository.findByName("site")).thenReturn(Optional.of(buildSite()));

        Optional<TurSNSiteDomain> result = adapter.findByName("site");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("site-id");
        assertThat(result.get().name()).isEqualTo("site");
        assertThat(result.get().facetType()).isEqualTo(TurSNSiteFacetFieldEnum.AND);
        assertThat(result.get().searchTemplate()).isEqualTo("template.html");
    }

    @Test
    void findByNameIgnoreCaseDelegatesToRepository() {
        when(turSNSiteRepository.findByNameIgnoreCase("site")).thenReturn(Optional.of(buildSite()));

        assertThat(adapter.findByNameIgnoreCase("site")).isPresent();
    }

    @Test
    void findByIdDelegatesToRepository() {
        when(turSNSiteRepository.findById("site-id")).thenReturn(Optional.of(buildSite()));

        assertThat(adapter.findById("site-id")).isPresent();
    }

    @Test
    void findByNameReturnsEmptyWhenRepositoryEmpty() {
        when(turSNSiteRepository.findByName("missing")).thenReturn(Optional.empty());

        assertThat(adapter.findByName("missing")).isEmpty();
    }

    @Test
    void mappingProjectsSeInstanceIdFromAssociation() {
        TurSNSite entity = buildSite();
        TurSEInstance se = new TurSEInstance();
        se.setId("se-id");
        entity.setTurSEInstance(se);
        when(turSNSiteRepository.findByName("site")).thenReturn(Optional.of(entity));

        TurSNSiteDomain domain = adapter.findByName("site").orElseThrow();

        assertThat(domain.seInstanceId()).isEqualTo("se-id");
        assertThat(domain.genAiId()).isNull();
    }

    private static TurSNSite buildSite() {
        TurSNSite site = new TurSNSite();
        site.setId("site-id");
        site.setName("site");
        site.setSearchTemplate("template.html");
        return site;
    }
}
