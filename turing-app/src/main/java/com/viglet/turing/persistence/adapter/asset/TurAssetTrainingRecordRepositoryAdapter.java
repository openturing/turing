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
package com.viglet.turing.persistence.adapter.asset;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.viglet.turing.domain.asset.TurAssetTrainingRecordDomain;
import com.viglet.turing.domain.asset.TurAssetTrainingRecordRepositoryPort;
import com.viglet.turing.persistence.repository.asset.TurAssetTrainingRecordRepository;

/**
 * Infrastructure adapter implementing
 * {@link TurAssetTrainingRecordRepositoryPort}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurAssetTrainingRecordRepositoryAdapter
        implements TurAssetTrainingRecordRepositoryPort {

    private final TurAssetTrainingRecordRepository turAssetTrainingRecordRepository;
    private final TurAssetTrainingRecordDomainMapper mapper;

    public TurAssetTrainingRecordRepositoryAdapter(
            TurAssetTrainingRecordRepository turAssetTrainingRecordRepository,
            TurAssetTrainingRecordDomainMapper mapper) {
        this.turAssetTrainingRecordRepository = turAssetTrainingRecordRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<TurAssetTrainingRecordDomain> findById(String objectName) {
        return turAssetTrainingRecordRepository.findById(objectName).map(mapper::toDomain);
    }

    @Override
    public List<TurAssetTrainingRecordDomain> findAll() {
        return mapper.toDomainList(turAssetTrainingRecordRepository.findAll());
    }

    @Override
    public List<TurAssetTrainingRecordDomain> findByObjectNameIn(Collection<String> objectNames) {
        return mapper.toDomainList(
                turAssetTrainingRecordRepository.findByObjectNameIn(objectNames));
    }

    @Override
    public List<TurAssetTrainingRecordDomain> findByFileNameContainingIgnoreCase(String keyword) {
        return mapper.toDomainList(
                turAssetTrainingRecordRepository.findByFileNameContainingIgnoreCase(keyword));
    }

    @Override
    public List<TurAssetTrainingRecordDomain> findByContentType(String contentType) {
        return mapper.toDomainList(
                turAssetTrainingRecordRepository.findByContentType(contentType));
    }

    @Override
    public long countByContentType(String contentType) {
        return turAssetTrainingRecordRepository.countByContentType(contentType);
    }
}
