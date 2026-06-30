/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.persistence.repository.llm;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.turing.persistence.model.llm.TurVendorFile;

/**
 * T175 / §X.12.a — repository for the vendor Files-API upload cache. T177
 * broadens the dedup lookup from per-instance to per-account.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurVendorFileRepository extends JpaRepository<TurVendorFile, String> {

    /**
     * T177 — the cross-instance dedup lookup: any instance sharing the vendor
     * account ({@code accountKey}) reuses a file already uploaded under it.
     */
    Optional<TurVendorFile> findByPluginTypeAndAccountKeyAndContentHash(String pluginType,
            String accountKey, String contentHash);
}
