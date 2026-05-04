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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.viglet.turing.domain.auth.TurRoleDomain;
import com.viglet.turing.domain.auth.TurRoleRepositoryPort;
import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;

/**
 * Infrastructure adapter implementing {@link TurRoleRepositoryPort}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurRoleRepositoryAdapter implements TurRoleRepositoryPort {

    private final TurRoleRepository turRoleRepository;
    private final TurRoleDomainMapper mapper;

    public TurRoleRepositoryAdapter(TurRoleRepository turRoleRepository,
            TurRoleDomainMapper mapper) {
        this.turRoleRepository = turRoleRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<TurRoleDomain> findById(String id) {
        return turRoleRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<TurRoleDomain> findByName(String name) {
        return Optional.ofNullable(turRoleRepository.findByName(name)).map(mapper::toDomain);
    }

    @Override
    public List<TurRoleDomain> findAll() {
        return mapper.toDomainList(turRoleRepository.findAll());
    }

    @Override
    public Set<TurRoleDomain> findByGroupId(String groupId) {
        TurGroup stub = new TurGroup();
        stub.setId(groupId);
        return turRoleRepository.findByTurGroupsContaining(stub).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toSet());
    }
}
