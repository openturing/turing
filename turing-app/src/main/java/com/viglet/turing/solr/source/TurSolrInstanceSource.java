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

/**
 * Abstraction over the backing store that provides {@link TurSEInstance}
 * objects to Solr / indexing / search flows.
 *
 * Two implementations exist:
 * <ul>
 *   <li>{@link TurSolrPropertySource} — reads from
 *       {@code turing.solr.*} application properties. Active when
 *       {@code turing.solr.enabled=true}. Mutations are not allowed.</li>
 *   <li>{@link TurSolrJpaSource} — reads/writes {@code TurSEInstance}
 *       via JPA. The default mode.</li>
 * </ul>
 *
 * Switching implementations is a matter of flipping
 * {@code turing.solr.enabled} — {@link TurSolrInstanceSourceConfig}
 * selects the correct bean at startup.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public interface TurSolrInstanceSource {

    /**
     * @return {@code true} when the source is backed by external configuration
     *         (properties) and therefore the SE instance catalog is read-only.
     */
    boolean isReadOnly();

    /**
     * @return the endpoint configured when {@link #isReadOnly()} is
     *         {@code true}, or {@code null} otherwise.
     */
    String getConfiguredEndpoint();

    List<TurSEInstance> findAll();

    Optional<TurSEInstance> findById(String id);

    /**
     * Persist or update an instance. Property-backed sources throw
     * {@link UnsupportedOperationException}.
     */
    TurSEInstance save(TurSEInstance instance);

    /**
     * Remove an instance by id. Property-backed sources throw
     * {@link UnsupportedOperationException}.
     */
    void delete(String id);
}
