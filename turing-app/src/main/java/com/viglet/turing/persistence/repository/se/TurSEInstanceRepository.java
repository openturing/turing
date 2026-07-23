/*
 * Copyright (C) 2016-2022 the original author or authors. 
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

package com.viglet.turing.persistence.repository.se;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.viglet.turing.persistence.model.se.TurSEInstance;

public interface TurSEInstanceRepository extends JpaRepository<TurSEInstance, String> {

	@Modifying
	@Query("delete from  TurSEInstance si where si.id = ?1")
	void delete(String id);

    /**
     * T275 / §XIV.5.1 — instances visible to a tenant: its own ({@code tenantId = :tenantId})
     * plus the platform-provided global pool ({@code tenantId IS NULL}).
     */
    @Query("select e from TurSEInstance e where e.tenantId = :tenantId or e.tenantId is null")
    java.util.List<TurSEInstance> findVisibleToTenant(@Param("tenantId") String tenantId);

    /**
     * T796 / §LIV.8 — SE instances backed by a given vendor (e.g. {@code LUCENE}),
     * used to resolve/reuse a default embedded search engine when provisioning a
     * vectorless KB without an explicit {@code seInstanceId}.
     */
    java.util.List<TurSEInstance> findByTurSEVendor_Id(String vendorId);
}
