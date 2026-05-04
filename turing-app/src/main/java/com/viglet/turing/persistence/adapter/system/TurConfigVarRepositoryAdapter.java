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
package com.viglet.turing.persistence.adapter.system;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.viglet.turing.domain.system.TurConfigVarDomain;
import com.viglet.turing.domain.system.TurConfigVarRepositoryPort;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;

/**
 * Infrastructure adapter implementing {@link TurConfigVarRepositoryPort}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurConfigVarRepositoryAdapter implements TurConfigVarRepositoryPort {

    private final TurConfigVarRepository turConfigVarRepository;
    private final TurConfigVarDomainMapper mapper;

    public TurConfigVarRepositoryAdapter(TurConfigVarRepository turConfigVarRepository,
            TurConfigVarDomainMapper mapper) {
        this.turConfigVarRepository = turConfigVarRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<TurConfigVarDomain> findById(String id) {
        return turConfigVarRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<TurConfigVarDomain> findAll() {
        return mapper.toDomainList(turConfigVarRepository.findAll());
    }
}
