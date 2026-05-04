/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.domain.auth.TurPrivilegeDomain;
import com.viglet.turing.persistence.model.auth.TurPrivilege;
import com.viglet.turing.persistence.repository.auth.TurPrivilegeRepository;

/** Unit tests for {@link TurPrivilegeRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurPrivilegeRepositoryAdapterTest {

    @Mock
    private TurPrivilegeRepository turPrivilegeRepository;

    private TurPrivilegeRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurPrivilegeRepositoryAdapter(turPrivilegeRepository,
                Mappers.getMapper(TurPrivilegeDomainMapper.class));
    }

    @Test
    void findByIdProjectsScalars() {
        TurPrivilege entity = new TurPrivilege("READ");
        entity.setId("p-1");
        entity.setCategory("DOCUMENT");
        entity.setDescription("Read documents");
        when(turPrivilegeRepository.findById("p-1")).thenReturn(Optional.of(entity));

        TurPrivilegeDomain domain = adapter.findById("p-1").orElseThrow();

        assertThat(domain.id()).isEqualTo("p-1");
        assertThat(domain.name()).isEqualTo("READ");
        assertThat(domain.category()).isEqualTo("DOCUMENT");
    }

    @Test
    void findByNameDelegatesAndMaps() {
        TurPrivilege entity = new TurPrivilege("READ");
        entity.setId("p-1");
        when(turPrivilegeRepository.findByName("READ")).thenReturn(entity);

        assertThat(adapter.findByName("READ")).map(TurPrivilegeDomain::id).contains("p-1");
    }

    @Test
    void findAllReturnsMappedDomainList() {
        TurPrivilege a = new TurPrivilege("READ");
        a.setId("p-1");
        TurPrivilege b = new TurPrivilege("WRITE");
        b.setId("p-2");
        when(turPrivilegeRepository.findAll()).thenReturn(List.of(a, b));

        assertThat(adapter.findAll()).extracting(TurPrivilegeDomain::id).containsExactly("p-1",
                "p-2");
    }
}
