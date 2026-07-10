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

package com.viglet.turing.sn.migration;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.viglet.core.manifest.VigletFieldManifest;
import com.viglet.core.manifest.VigletManifestResult;
import com.viglet.turing.api.sn.job.TurSNImportAPI;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.sn.manifest.TurSNSiteManifestService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Engine-agnostic sink for a migration run (Block AO / §XXXVIII).
 *
 * <p>Both importers — Elasticsearch (T657) and Algolia (T658) — converge the same
 * two steps here: provision the derived field manifest against the SN site
 * (reusing {@link TurSNSiteManifestService}, so an importer-created field is
 * byte-identical to a manifested one), then reshape each source record into a
 * {@link TurSNJobItem} and push batches through the existing import path
 * ({@link TurSNImportAPI#send}), which routes RAG-enabled sites to the vector
 * store and everything to the indexing queue exactly as a normal import would. A
 * trailing {@code COMMIT} flushes the search engine at the end of the run.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TurMigrationImportService {

    private final TurSNSiteManifestService manifestService;
    private final TurSNImportAPI turSNImportAPI;

    /** Creates or converges the SN site + field schema from the derived manifest. */
    public VigletManifestResult provision(VigletFieldManifest manifest) {
        return manifestService.provision(manifest);
    }

    /**
     * Reshapes and queues one batch of documents for indexing. Each element of
     * {@code attributesList} is a ready-to-index attribute map (id + fields) — the
     * engine-specific document mapper has already dropped absent/empty values per
     * the grounding contract.
     */
    public void importBatch(String siteName, Locale locale, List<Map<String, Object>> attributesList) {
        if (attributesList == null || attributesList.isEmpty()) {
            return;
        }
        TurSNJobItems items = new TurSNJobItems();
        for (Map<String, Object> attributes : attributesList) {
            items.add(new TurSNJobItem(TurSNJobAction.CREATE, List.of(siteName), locale, attributes, null));
        }
        turSNImportAPI.send(items);
        log.info("[Block AO] Queued {} document(s) for SN site '{}' ({})", items.size(), siteName, locale);
    }

    /** Flushes the search engine for the (site, locale) once every batch is queued. */
    public void commit(String siteName, Locale locale) {
        TurSNJobItem commit = new TurSNJobItem(TurSNJobAction.COMMIT, List.of(siteName), locale);
        turSNImportAPI.send(new TurSNJobItems(commit));
        log.info("[Block AO] Sent COMMIT for SN site '{}' ({})", siteName, locale);
    }
}
