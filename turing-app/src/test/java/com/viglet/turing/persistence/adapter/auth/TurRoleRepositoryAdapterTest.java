/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
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

import com.viglet.turing.domain.auth.TurRoleDomain;
import com.viglet.turing.persistence.model.auth.TurPrivilege;
import com.viglet.turing.persistence.model.auth.TurRole;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;

/** Unit tests for {@link TurRoleRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurRoleRepositoryAdapterTest {

    @Mock
    private TurRoleRepository turRoleRepository;

    private TurRoleRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurRoleRepositoryAdapter(turRoleRepository,
                Mappers.getMapper(TurRoleDomainMapper.class));
    }

    @Test
    void findByIdProjectsPrivilegeIds() {
        TurRole entity = new TurRole("admin");
        entity.setId("r-1");
        Set<TurPrivilege> privileges = new HashSet<>();
        TurPrivilege p1 = new TurPrivilege("READ");
        p1.setId("p-1");
        privileges.add(p1);
        TurPrivilege p2 = new TurPrivilege("WRITE");
        p2.setId("p-2");
        privileges.add(p2);
        entity.setTurPrivileges(privileges);
        when(turRoleRepository.findById("r-1")).thenReturn(Optional.of(entity));

        TurRoleDomain domain = adapter.findById("r-1").orElseThrow();

        assertThat(domain.name()).isEqualTo("admin");
        assertThat(domain.privilegeIds()).containsExactlyInAnyOrder("p-1", "p-2");
    }

    @Test
    void findByNameDelegatesAndMaps() {
        TurRole entity = new TurRole("admin");
        entity.setId("r-1");
        when(turRoleRepository.findByName("admin")).thenReturn(entity);

        assertThat(adapter.findByName("admin")).map(TurRoleDomain::id).contains("r-1");
    }

    @Test
    void findByGroupIdQueriesByStubGroup() {
        when(turRoleRepository.findByTurGroupsContaining(any())).thenReturn(Set.of());

        adapter.findByGroupId("g-1");

        verify(turRoleRepository)
                .findByTurGroupsContaining(argThat(g -> "g-1".equals(g.getId())));
    }
}
