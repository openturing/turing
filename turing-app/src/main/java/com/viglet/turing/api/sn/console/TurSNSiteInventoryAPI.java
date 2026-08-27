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
package com.viglet.turing.api.sn.console;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.sn.inventory.TurSNIndexInventoryResponse;
import com.viglet.turing.sn.inventory.TurSNIndexInventoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * T1029 / §XX.16 — the half of the content-audit loop only Turing can answer:
 * what the Semantic Navigation index currently holds for a source, as
 * {@code (id, version)} pairs.
 *
 * <p>A connector's own audit compares its source against its indexing ledger.
 * It cannot see the index, so a failed write or a lost delete is invisible to
 * both products. Paging through this endpoint gives that audit its third
 * comparison — in the index but not the source, in the source but not the
 * index, and in both at different versions — while applying the repair stays
 * with the connector, which owns the queue.
 *
 * <p>Paging is a keyset cursor, not an offset: read {@code nextCursor} off each
 * page and pass it back as {@code after} until {@code hasMore} is false.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/sn/{snSiteId}/inventory")
@Tag(name = "Semantic Navigation Index Inventory",
        description = "Per-source enumeration of what the index holds, for external content audits")
@RequiredArgsConstructor
public class TurSNSiteInventoryAPI {

    private final TurSNSiteRepository snSiteRepository;
    private final TurSNIndexInventoryService inventoryService;

    @Operation(summary = "One page of (id, version, checksum) rows the index holds for a source")
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<TurSNIndexInventoryResponse> inventory(
            @PathVariable String snSiteId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String locale,
            @RequestParam(required = false) String after,
            @RequestParam(required = false, defaultValue = "0") int rows) {
        // Read inside the transaction so the service can resolve the site's
        // lazy locale association through the open session.
        TurSNSite site = resolveSite(snSiteId);
        if (site == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(inventoryService.inventory(site, locale, source, after, rows));
    }

    /**
     * T1035 — accepts a site <em>name</em> as well as an id. A connector holds the
     * name it publishes to ({@code TurSNServer.siteName}) and not the row's uuid,
     * so requiring the id here meant every audit needed a lookup Turing exposes
     * nowhere convenient. The id is tried first, so nothing about an existing
     * caller changes.
     */
    private TurSNSite resolveSite(String snSiteIdOrName) {
        return snSiteRepository.findById(snSiteIdOrName)
                .or(() -> snSiteRepository.findByNameIgnoreCase(snSiteIdOrName))
                .orElse(null);
    }
}
