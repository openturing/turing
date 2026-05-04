/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.persistence.adapter.auth;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.viglet.turing.domain.auth.TurUserDomain;
import com.viglet.turing.domain.auth.TurUserRepositoryPort;
import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;

/**
 * Infrastructure adapter implementing {@link TurUserRepositoryPort}.
 *
 * <p>{@link #findByGroupIdsIn} synthesises stub {@link TurGroup} entities
 * carrying just their IDs to call the underlying
 * {@code findByTurGroupsIn} query without leaking the JPA entity into the
 * port signature.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurUserRepositoryAdapter implements TurUserRepositoryPort {

    private final TurUserRepository turUserRepository;
    private final TurUserDomainMapper mapper;

    public TurUserRepositoryAdapter(TurUserRepository turUserRepository,
            TurUserDomainMapper mapper) {
        this.turUserRepository = turUserRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<TurUserDomain> findByUsername(String username) {
        return Optional.ofNullable(turUserRepository.findByUsername(username))
                .map(mapper::toDomain);
    }

    @Override
    public List<TurUserDomain> findAll() {
        return mapper.toDomainList(turUserRepository.findAll());
    }

    @Override
    public Set<TurUserDomain> findByGroupIdsIn(Collection<String> groupIds) {
        Collection<TurGroup> stubs = groupIds.stream()
                .map(TurUserRepositoryAdapter::stubGroup)
                .toList();
        return turUserRepository.findByTurGroupsIn(stubs).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toSet());
    }

    private static TurGroup stubGroup(String id) {
        TurGroup stub = new TurGroup();
        stub.setId(id);
        return stub;
    }
}
