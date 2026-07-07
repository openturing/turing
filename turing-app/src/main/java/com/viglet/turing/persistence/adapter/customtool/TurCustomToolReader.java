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
package com.viglet.turing.persistence.adapter.customtool;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.viglet.turing.domain.customtool.TurCustomToolDomain;
import com.viglet.turing.persistence.repository.customtool.TurCustomToolRepository;

/**
 * Mapper-backed reader that returns immutable {@link TurCustomToolDomain}
 * records straight from the JPA {@link TurCustomToolRepository}.
 *
 * <p><b>T546 — no port interface.</b> Unlike the other domain aggregates, this
 * read has a single permanent JPA implementation and <em>no mocking need</em>
 * (no unit test mocks it), so the {@code *RepositoryPort} interface was pure
 * ceremony over its lone adapter. Per the ADR criterion (a port pays for itself
 * only with a second implementation or a mocking need), it collapses to this
 * concrete reader — keeping the immutable, lazy-proxy-free, cacheable record
 * without the interface boilerplate. The 7 ports that <em>are</em> mocked in
 * unit tests stay as ports. See
 * {@code docs/adr/0001-domain-layer-bounded-completion.md} (§T546).
 *
 * <p>Read-only by design, permanently — writes stay on the JPA repository
 * ({@code @Transactional} + {@code LEFT JOIN FETCH}, per T488).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurCustomToolReader {

    private final TurCustomToolRepository turCustomToolRepository;
    private final TurCustomToolDomainMapper mapper;

    public TurCustomToolReader(TurCustomToolRepository turCustomToolRepository,
            TurCustomToolDomainMapper mapper) {
        this.turCustomToolRepository = turCustomToolRepository;
        this.mapper = mapper;
    }

    public Optional<TurCustomToolDomain> findById(String id) {
        return turCustomToolRepository.findById(id).map(mapper::toDomain);
    }

    public List<TurCustomToolDomain> findAll() {
        return mapper.toDomainList(turCustomToolRepository.findAll());
    }

    /** Return only the enabled custom tools — useful for runtime selection. */
    public List<TurCustomToolDomain> findAllEnabled() {
        return mapper.toDomainList(turCustomToolRepository.findAll()).stream()
                .filter(TurCustomToolDomain::isEnabled)
                .toList();
    }
}
