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

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.domain.sn.TurSNSiteSpotlightDocumentDomain;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightDocumentRepository;

/** Unit tests for {@link TurSNSiteSpotlightDocumentRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurSNSiteSpotlightDocumentRepositoryAdapterTest {

    @Mock
    private TurSNSiteSpotlightDocumentRepository turSNSiteSpotlightDocumentRepository;

    private TurSNSiteSpotlightDocumentRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurSNSiteSpotlightDocumentRepositoryAdapter(
                turSNSiteSpotlightDocumentRepository,
                Mappers.getMapper(TurSNSiteSpotlightDocumentDomainMapper.class));
    }

    @Test
    void findByIdReturnsMappedDomainWithSpotlightIdProjected() {
        TurSNSiteSpotlightDocument doc = buildDocument("doc-1", 1, "Title", "sp-1");
        doc.setLink("https://example.com");
        doc.setContent("snippet");
        when(turSNSiteSpotlightDocumentRepository.findById("doc-1"))
                .thenReturn(Optional.of(doc));

        Optional<TurSNSiteSpotlightDocumentDomain> result = adapter.findById("doc-1");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("doc-1");
        assertThat(result.get().title()).isEqualTo("Title");
        assertThat(result.get().link()).isEqualTo("https://example.com");
        assertThat(result.get().snSiteSpotlightId()).isEqualTo("sp-1");
    }

    @Test
    void findBySnSiteSpotlightIdQueriesByStubParent() {
        when(turSNSiteSpotlightDocumentRepository.findByTurSNSiteSpotlight(any()))
                .thenReturn(Set.of(buildDocument("doc-1", 1, "Title", "sp-1")));

        Set<TurSNSiteSpotlightDocumentDomain> result = adapter.findBySnSiteSpotlightId("sp-1");

        assertThat(result).hasSize(1);
        verify(turSNSiteSpotlightDocumentRepository)
                .findByTurSNSiteSpotlight(argThat(spotlight -> "sp-1".equals(spotlight.getId())));
    }

    private static TurSNSiteSpotlightDocument buildDocument(String id, int position, String title,
            String spotlightId) {
        TurSNSiteSpotlightDocument doc = new TurSNSiteSpotlightDocument();
        doc.setId(id);
        doc.setPosition(position);
        doc.setTitle(title);
        TurSNSiteSpotlight parent = new TurSNSiteSpotlight();
        parent.setId(spotlightId);
        doc.setTurSNSiteSpotlight(parent);
        return doc;
    }
}
