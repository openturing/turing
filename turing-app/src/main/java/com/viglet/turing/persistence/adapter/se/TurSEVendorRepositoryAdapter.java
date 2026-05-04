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

import com.viglet.turing.domain.se.TurSEVendorDomain;
import com.viglet.turing.domain.se.TurSEVendorRepositoryPort;
import com.viglet.turing.persistence.repository.se.TurSEVendorRepository;

/**
 * Infrastructure adapter implementing {@link TurSEVendorRepositoryPort} on
 * top of the Spring Data JPA repository.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurSEVendorRepositoryAdapter implements TurSEVendorRepositoryPort {

    private final TurSEVendorRepository turSEVendorRepository;
    private final TurSEVendorDomainMapper mapper;

    public TurSEVendorRepositoryAdapter(TurSEVendorRepository turSEVendorRepository,
            TurSEVendorDomainMapper mapper) {
        this.turSEVendorRepository = turSEVendorRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<TurSEVendorDomain> findById(String id) {
        return turSEVendorRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<TurSEVendorDomain> findAll() {
        return mapper.toDomainList(turSEVendorRepository.findAll());
    }
}
