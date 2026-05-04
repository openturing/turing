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

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.domain.sn.TurSNSiteMergeProvidersDomain;
import com.viglet.turing.domain.sn.TurSNSiteMergeProvidersFieldDomain;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProviders;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProvidersField;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersRepository;

/** Unit tests for {@link TurSNSiteMergeProvidersRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurSNSiteMergeProvidersRepositoryAdapterTest {

    @Mock
    private TurSNSiteMergeProvidersRepository turSNSiteMergeProvidersRepository;

    private TurSNSiteMergeProvidersRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurSNSiteMergeProvidersRepositoryAdapter(
                turSNSiteMergeProvidersRepository,
                Mappers.getMapper(TurSNSiteMergeProvidersDomainMapper.class));
    }

    @Test
    void findByIdProjectsScalarsAndOverwrittenFields() {
        TurSNSiteMergeProviders entity = buildMerge("m-1", "site-1");
        Set<TurSNSiteMergeProvidersField> fields = new HashSet<>();
        TurSNSiteMergeProvidersField f = new TurSNSiteMergeProvidersField();
        f.setId("mf-1");
        f.setName("title");
        fields.add(f);
        entity.setOverwrittenFields(fields);
        when(turSNSiteMergeProvidersRepository.findById("m-1")).thenReturn(Optional.of(entity));

        TurSNSiteMergeProvidersDomain domain = adapter.findById("m-1").orElseThrow();

        assertThat(domain.snSiteId()).isEqualTo("site-1");
        assertThat(domain.providerFrom()).isEqualTo("aem");
        assertThat(domain.providerTo()).isEqualTo("solr");
        assertThat(domain.overwrittenFields())
                .extracting(TurSNSiteMergeProvidersFieldDomain::name)
                .containsExactly("title");
    }

    @Test
    void findBySnSiteIdQueriesByStubParent() {
        when(turSNSiteMergeProvidersRepository.findByTurSNSite(any())).thenReturn(List.of());

        adapter.findBySnSiteId("site-1");

        verify(turSNSiteMergeProvidersRepository)
                .findByTurSNSite(argThat(s -> "site-1".equals(s.getId())));
    }

    private static TurSNSiteMergeProviders buildMerge(String id, String siteId) {
        TurSNSiteMergeProviders entity = new TurSNSiteMergeProviders();
        entity.setId(id);
        entity.setLocale(Locale.US);
        entity.setProviderFrom("aem");
        entity.setProviderTo("solr");
        entity.setRelationFrom("page");
        entity.setRelationTo("doc");
        TurSNSite site = new TurSNSite();
        site.setId(siteId);
        entity.setTurSNSite(site);
        return entity;
    }
}
