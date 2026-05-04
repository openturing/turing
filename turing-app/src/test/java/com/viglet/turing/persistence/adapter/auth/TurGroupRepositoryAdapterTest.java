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

import com.viglet.turing.domain.auth.TurGroupDomain;
import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurRole;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;

/** Unit tests for {@link TurGroupRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurGroupRepositoryAdapterTest {

    @Mock
    private TurGroupRepository turGroupRepository;

    private TurGroupRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurGroupRepositoryAdapter(turGroupRepository,
                Mappers.getMapper(TurGroupDomainMapper.class));
    }

    @Test
    void findByIdProjectsRoleIdsAndUserIds() {
        TurGroup entity = new TurGroup();
        entity.setId("g-1");
        entity.setName("admins");
        entity.setTurRoles(setOfRoles("r-1", "r-2"));
        entity.setTurUsers(setOfUsers("alice", "bob"));
        when(turGroupRepository.findById("g-1")).thenReturn(Optional.of(entity));

        TurGroupDomain domain = adapter.findById("g-1").orElseThrow();

        assertThat(domain.name()).isEqualTo("admins");
        assertThat(domain.roleIds()).containsExactlyInAnyOrder("r-1", "r-2");
        assertThat(domain.userIds()).containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void findByUsernameQueriesByStubUser() {
        when(turGroupRepository.findByTurUsersContaining(any())).thenReturn(Set.of());

        adapter.findByUsername("alice");

        verify(turGroupRepository)
                .findByTurUsersContaining(argThat(u -> "alice".equals(u.getUsername())));
    }

    private static Set<TurRole> setOfRoles(String... ids) {
        Set<TurRole> set = new HashSet<>();
        for (String id : ids) {
            TurRole role = new TurRole();
            role.setId(id);
            set.add(role);
        }
        return set;
    }

    private static Set<TurUser> setOfUsers(String... usernames) {
        Set<TurUser> set = new HashSet<>();
        for (String username : usernames) {
            TurUser user = new TurUser();
            user.setUsername(username);
            set.add(user);
        }
        return set;
    }
}
