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
package com.viglet.turing.persistence.adapter.se;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.viglet.turing.domain.se.TurSEInstanceDomain;
import com.viglet.turing.domain.se.TurSEInstanceRepositoryPort;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;

/**
 * Infrastructure adapter implementing {@link TurSEInstanceRepositoryPort} on
 * top of the Spring Data JPA repository.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurSEInstanceRepositoryAdapter implements TurSEInstanceRepositoryPort {

    private final TurSEInstanceRepository turSEInstanceRepository;
    private final TurSEInstanceDomainMapper mapper;

    public TurSEInstanceRepositoryAdapter(TurSEInstanceRepository turSEInstanceRepository,
            TurSEInstanceDomainMapper mapper) {
        this.turSEInstanceRepository = turSEInstanceRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<TurSEInstanceDomain> findById(String id) {
        return turSEInstanceRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<TurSEInstanceDomain> findAll() {
        return mapper.toDomainList(turSEInstanceRepository.findAll());
    }

    @Override
    public List<TurSEInstanceDomain> findAllEnabled() {
        return mapper.toDomainList(turSEInstanceRepository.findAll()).stream()
                .filter(TurSEInstanceDomain::isEnabled)
                .toList();
    }
}
