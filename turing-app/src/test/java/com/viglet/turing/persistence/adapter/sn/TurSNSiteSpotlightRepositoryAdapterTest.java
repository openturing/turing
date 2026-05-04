/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.domain.sn.TurSNSiteSpotlightDomain;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightTerm;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;

/**
 * Unit tests for {@link TurSNSiteSpotlightRepositoryAdapter}, covering the
 * children-as-id-sets projection and the unmodifiable-set guarantee.
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteSpotlightRepositoryAdapterTest {

    @Mock
    private TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;

    private TurSNSiteSpotlightRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurSNSiteSpotlightRepositoryAdapter(turSNSiteSpotlightRepository,
                Mappers.getMapper(TurSNSiteSpotlightDomainMapper.class));
    }

    @Test
    void findByIdReturnsMappedDomainWithSnSiteIdAndChildIds() {
        TurSNSiteSpotlight entity = buildSpotlight("sp-1", "Promo");
        entity.setTurSNSiteSpotlightTerms(setOfTerms("t-1", "t-2"));
        entity.setTurSNSiteSpotlightDocuments(setOfDocuments("d-1"));
        when(turSNSiteSpotlightRepository.findById("sp-1")).thenReturn(Optional.of(entity));

        Optional<TurSNSiteSpotlightDomain> result = adapter.findById("sp-1");

        assertThat(result).isPresent();
        assertThat(result.get().snSiteId()).isEqualTo("site-1");
        assertThat(result.get().termIds()).containsExactlyInAnyOrder("t-1", "t-2");
        assertThat(result.get().documentIds()).containsExactly("d-1");
        assertThat(result.get().isManaged()).isTrue();
    }

    @Test
    void findByIdEmpty() {
        when(turSNSiteSpotlightRepository.findById("missing")).thenReturn(Optional.empty());
        assertThat(adapter.findById("missing")).isEmpty();
    }

    @Test
    void findByProviderReturnsMappedDomainSet() {
        TurSNSiteSpotlight entity = buildSpotlight("sp-1", "Promo");
        when(turSNSiteSpotlightRepository.findByProvider("TURING")).thenReturn(Set.of(entity));

        Set<TurSNSiteSpotlightDomain> result = adapter.findByProvider("TURING");

        assertThat(result).hasSize(1);
        assertThat(result.iterator().next().id()).isEqualTo("sp-1");
    }

    @Test
    void termIdsAndDocumentIdsAreUnmodifiable() {
        TurSNSiteSpotlight entity = buildSpotlight("sp-1", "Promo");
        entity.setTurSNSiteSpotlightTerms(setOfTerms("t-1"));
        entity.setTurSNSiteSpotlightDocuments(setOfDocuments("d-1"));
        when(turSNSiteSpotlightRepository.findById("sp-1")).thenReturn(Optional.of(entity));

        TurSNSiteSpotlightDomain domain = adapter.findById("sp-1").orElseThrow();

        assertThatThrownBy(() -> domain.termIds().add("smuggled"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> domain.documentIds().add("smuggled"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static TurSNSiteSpotlight buildSpotlight(String id, String name) {
        TurSNSiteSpotlight entity = new TurSNSiteSpotlight();
        entity.setId(id);
        entity.setName(name);
        TurSNSite site = new TurSNSite();
        site.setId("site-1");
        entity.setTurSNSite(site);
        return entity;
    }

    private static Set<TurSNSiteSpotlightTerm> setOfTerms(String... ids) {
        Set<TurSNSiteSpotlightTerm> set = new HashSet<>();
        for (String id : ids) {
            TurSNSiteSpotlightTerm term = new TurSNSiteSpotlightTerm();
            term.setId(id);
            set.add(term);
        }
        return set;
    }

    private static Set<TurSNSiteSpotlightDocument> setOfDocuments(String... ids) {
        Set<TurSNSiteSpotlightDocument> set = new HashSet<>();
        for (String id : ids) {
            TurSNSiteSpotlightDocument doc = new TurSNSiteSpotlightDocument();
            doc.setId(id);
            set.add(doc);
        }
        return set;
    }
}
