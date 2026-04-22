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
import com.viglet.turing.persistence.model.se.TurSEVendor;

/**
 * Property-backed implementation of {@link TurSolrInstanceSource}. Exposes a
 * single synthetic {@link TurSEInstance} built from
 * {@code turing.solr.endpoint}. Mutations throw
 * {@link UnsupportedOperationException} — the SE instance catalog is
 * read-only in this mode.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public class TurSolrPropertySource implements TurSolrInstanceSource {

    public static final String PROPERTY_INSTANCE_ID = "solr-properties";
    public static final String SOLR_VENDOR_ID = "SOLR";

    private final String endpoint;
    private final TurSEInstance cached;

    public TurSolrPropertySource(String endpoint) {
        this.endpoint = endpoint;
        this.cached = buildInstance(endpoint);
    }

    private static TurSEInstance buildInstance(String endpoint) {
        TurSEVendor vendor = new TurSEVendor();
        vendor.setId(SOLR_VENDOR_ID);
        vendor.setPlugin("solr");
        vendor.setTitle("Apache Solr");

        TurSEInstance instance = new TurSEInstance();
        instance.setId(PROPERTY_INSTANCE_ID);
        instance.setTitle("Apache Solr (configured)");
        instance.setDescription("Solr instance provided by turing.solr.endpoint.");
        instance.setEnabled(1);
        instance.setEndpointUrl(endpoint);
        instance.setTurSEVendor(vendor);
        return instance;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String getConfiguredEndpoint() {
        return endpoint;
    }

    @Override
    public List<TurSEInstance> findAll() {
        return List.of(cached);
    }

    @Override
    public Optional<TurSEInstance> findById(String id) {
        if (PROPERTY_INSTANCE_ID.equals(id)) {
            return Optional.of(cached);
        }
        return Optional.empty();
    }

    @Override
    public TurSEInstance save(TurSEInstance instance) {
        throw new UnsupportedOperationException(
                "Solr is configured via turing.solr.endpoint — SE instance catalog is read-only.");
    }

    @Override
    public void delete(String id) {
        throw new UnsupportedOperationException(
                "Solr is configured via turing.solr.endpoint — SE instance catalog is read-only.");
    }
}
