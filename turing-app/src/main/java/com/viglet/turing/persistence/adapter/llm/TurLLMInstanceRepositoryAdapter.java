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
package com.viglet.turing.persistence.adapter.llm;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.viglet.turing.domain.llm.TurLLMInstanceDomain;
import com.viglet.turing.domain.llm.TurLLMInstanceRepositoryPort;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;

/**
 * Infrastructure adapter implementing {@link TurLLMInstanceRepositoryPort}
 * on top of the Spring Data JPA repository.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurLLMInstanceRepositoryAdapter implements TurLLMInstanceRepositoryPort {

    private final TurLLMInstanceRepository turLLMInstanceRepository;
    private final TurLLMInstanceDomainMapper mapper;

    public TurLLMInstanceRepositoryAdapter(TurLLMInstanceRepository turLLMInstanceRepository,
            TurLLMInstanceDomainMapper mapper) {
        this.turLLMInstanceRepository = turLLMInstanceRepository;
        this.mapper = mapper;
    }

    @Override
    public List<TurLLMInstanceDomain> findAll() {
        return mapper.toDomainList(turLLMInstanceRepository.findAll());
    }

    @Override
    public List<TurLLMInstanceDomain> findAllEnabled() {
        return mapper.toDomainList(turLLMInstanceRepository.findAll()).stream()
                .filter(TurLLMInstanceDomain::isEnabled)
                .toList();
    }

    @Override
    public Optional<TurLLMInstanceDomain> findById(String id) {
        return turLLMInstanceRepository.findById(id).map(mapper::toDomain);
    }
}
