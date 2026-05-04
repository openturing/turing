/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

import com.viglet.turing.domain.sn.TurSNSiteSpotlightTermDomain;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightTerm;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightTermRepository;

/** Unit tests for {@link TurSNSiteSpotlightTermRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurSNSiteSpotlightTermRepositoryAdapterTest {

    @Mock
    private TurSNSiteSpotlightTermRepository turSNSiteSpotlightTermRepository;

    private TurSNSiteSpotlightTermRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurSNSiteSpotlightTermRepositoryAdapter(turSNSiteSpotlightTermRepository,
                Mappers.getMapper(TurSNSiteSpotlightTermDomainMapper.class));
    }

    @Test
    void findByIdReturnsMappedDomainWithSpotlightIdProjected() {
        when(turSNSiteSpotlightTermRepository.findById("term-1"))
                .thenReturn(Optional.of(buildTerm("term-1", "promo", "sp-1")));

        Optional<TurSNSiteSpotlightTermDomain> result = adapter.findById("term-1");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("term-1");
        assertThat(result.get().name()).isEqualTo("promo");
        assertThat(result.get().snSiteSpotlightId()).isEqualTo("sp-1");
    }

    @Test
    void findByNameInDelegatesAndMaps() {
        when(turSNSiteSpotlightTermRepository.findByNameIn(List.of("a", "b")))
                .thenReturn(List.of(buildTerm("term-1", "a", "sp-1"),
                        buildTerm("term-2", "b", "sp-2")));

        List<TurSNSiteSpotlightTermDomain> result = adapter.findByNameIn(List.of("a", "b"));

        assertThat(result).extracting(TurSNSiteSpotlightTermDomain::id).containsExactly("term-1",
                "term-2");
    }

    @Test
    void findBySnSiteSpotlightIdQueriesByStubParent() {
        when(turSNSiteSpotlightTermRepository.findByTurSNSiteSpotlight(any()))
                .thenReturn(Set.of(buildTerm("term-1", "promo", "sp-1")));

        Set<TurSNSiteSpotlightTermDomain> result = adapter.findBySnSiteSpotlightId("sp-1");

        assertThat(result).hasSize(1);
        verify(turSNSiteSpotlightTermRepository)
                .findByTurSNSiteSpotlight(argThat(spotlight -> "sp-1".equals(spotlight.getId())));
    }

    private static TurSNSiteSpotlightTerm buildTerm(String id, String name, String spotlightId) {
        TurSNSiteSpotlightTerm term = new TurSNSiteSpotlightTerm();
        term.setId(id);
        term.setName(name);
        TurSNSiteSpotlight parent = new TurSNSiteSpotlight();
        parent.setId(spotlightId);
        term.setTurSNSiteSpotlight(parent);
        return term;
    }
}
