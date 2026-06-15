/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.solr.source;

import java.util.List;
import java.util.Optional;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;

/**
 * Default JPA-backed implementation of {@link TurSolrInstanceSource}. Delegates
 * every call to {@link TurSEInstanceRepository}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public class TurSolrJpaSource implements TurSolrInstanceSource {

    private final TurSEInstanceRepository repository;

    public TurSolrJpaSource(TurSEInstanceRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String getConfiguredEndpoint() {
        return null;
    }

    @Override
    public List<TurSEInstance> findAll() {
        return repository.findAll();
    }

    @Override
    public List<TurSEInstance> findVisibleToTenant(String tenantId) {
        return repository.findVisibleToTenant(tenantId);
    }

    @Override
    public Optional<TurSEInstance> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public TurSEInstance save(TurSEInstance instance) {
        return repository.save(instance);
    }

    @Override
    public void delete(String id) {
        repository.delete(id);
    }
}
