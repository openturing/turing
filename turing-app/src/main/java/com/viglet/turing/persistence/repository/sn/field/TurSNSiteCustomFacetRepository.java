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
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.persistence.repository.sn.field;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;

/**
 * Repository for {@link TurSNSiteCustomFacet} with eager fetching of items and labels,
 * avoiding lazy-loading issues on {@link TurSNSiteFieldExt} entities.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
public interface TurSNSiteCustomFacetRepository extends JpaRepository<TurSNSiteCustomFacet, String> {

    @Query("SELECT DISTINCT cf FROM TurSNSiteCustomFacet cf " +
           "LEFT JOIN FETCH cf.items i " +
           "LEFT JOIN FETCH cf.label " +
           "LEFT JOIN FETCH i.labels " +
           "WHERE cf.turSNSiteFieldExt IN :fieldExts")
    List<TurSNSiteCustomFacet> findByFieldExtsWithDetails(
            @Param("fieldExts") Collection<TurSNSiteFieldExt> fieldExts);

    @Query("SELECT DISTINCT cf FROM TurSNSiteCustomFacet cf " +
           "LEFT JOIN FETCH cf.items i " +
           "LEFT JOIN FETCH cf.label " +
           "LEFT JOIN FETCH i.labels " +
           "WHERE cf.turSNSiteFieldExt = :fieldExt")
    List<TurSNSiteCustomFacet> findByFieldExtWithDetails(
            @Param("fieldExt") TurSNSiteFieldExt fieldExt);
}
