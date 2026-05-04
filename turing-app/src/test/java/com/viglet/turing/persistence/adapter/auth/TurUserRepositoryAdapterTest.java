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
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.domain.auth.TurUserDomain;
import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;

/**
 * Unit tests for {@link TurUserRepositoryAdapter}, including the codified
 * invariant that the password hash is never projected onto the domain
 * record.
 */
@ExtendWith(MockitoExtension.class)
class TurUserRepositoryAdapterTest {

    @Mock
    private TurUserRepository turUserRepository;

    private TurUserRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurUserRepositoryAdapter(turUserRepository,
                Mappers.getMapper(TurUserDomainMapper.class));
    }

    @Test
    void findByUsernameProjectsScalarsAndGroupIds() {
        TurUser entity = TurUser.builder()
                .username("alice")
                .email("alice@example.com")
                .firstName("Alice")
                .lastName("Liddell")
                .enabled(1)
                .turGroups(setOfGroups("g-1", "g-2"))
                .build();
        when(turUserRepository.findByUsername("alice")).thenReturn(entity);

        TurUserDomain domain = adapter.findByUsername("alice").orElseThrow();

        assertThat(domain.username()).isEqualTo("alice");
        assertThat(domain.email()).isEqualTo("alice@example.com");
        assertThat(domain.groupIds()).containsExactlyInAnyOrder("g-1", "g-2");
        assertThat(domain.isEnabled()).isTrue();
    }

    @Test
    void domainOmitsPasswordEvenWhenSetOnEntity() {
        // Sensitive field — same policy as TurLLMInstanceDomain.apiKey.
        TurUser entity = TurUser.builder()
                .username("alice")
                .password("supersecret-bcrypt-hash")
                .enabled(1)
                .build();
        when(turUserRepository.findByUsername("alice")).thenReturn(entity);

        TurUserDomain domain = adapter.findByUsername("alice").orElseThrow();

        assertThat(domain.username()).isEqualTo("alice");
        assertThat(TurUserDomain.class.getRecordComponents())
                .extracting(rc -> rc.getName())
                .doesNotContain("password");
    }

    @Test
    void findByGroupIdsInBuildsStubsAndDelegates() {
        when(turUserRepository.findByTurGroupsIn(any())).thenReturn(Set.of());

        adapter.findByGroupIdsIn(List.of("g-1", "g-2"));

        verify(turUserRepository).findByTurGroupsIn(argThat(stubs -> {
            return stubs.size() == 2
                    && stubs.stream().map(TurGroup::getId).toList()
                            .containsAll(List.of("g-1", "g-2"));
        }));
    }

    private static Set<TurGroup> setOfGroups(String... ids) {
        Set<TurGroup> set = new HashSet<>();
        for (String id : ids) {
            TurGroup group = new TurGroup();
            group.setId(id);
            set.add(group);
        }
        return set;
    }
}
