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
package com.viglet.turing.persistence.repository.sn.sort;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSort;

/**
 * Repository for {@link TurSNSiteCustomSort}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public interface TurSNSiteCustomSortRepository extends JpaRepository<TurSNSiteCustomSort, String> {

    @Query("SELECT DISTINCT cs FROM TurSNSiteCustomSort cs " +
           "LEFT JOIN FETCH cs.items " +
           "WHERE cs.turSNSite = :site " +
           "ORDER BY cs.name")
    List<TurSNSiteCustomSort> findByTurSNSiteWithItems(@Param("site") TurSNSite site);

    @Query("SELECT DISTINCT cs FROM TurSNSiteCustomSort cs " +
           "LEFT JOIN FETCH cs.items " +
           "WHERE cs.turSNSite = :site AND cs.name = :name")
    Optional<TurSNSiteCustomSort> findByTurSNSiteAndName(@Param("site") TurSNSite site,
                                                          @Param("name") String name);

    @Query("SELECT DISTINCT cs FROM TurSNSiteCustomSort cs " +
           "LEFT JOIN FETCH cs.items " +
           "WHERE cs.turSNSite = :site AND cs.id = :id")
    Optional<TurSNSiteCustomSort> findByTurSNSiteAndId(@Param("site") TurSNSite site,
                                                        @Param("id") String id);
}
