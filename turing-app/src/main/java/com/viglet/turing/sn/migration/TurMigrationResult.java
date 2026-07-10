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

import com.viglet.core.manifest.VigletFieldManifest;

/**
 * Outcome of a migration run (Block AO / §XXXVIII) — the same shape for the
 * Elasticsearch (T657) and Algolia (T658) importers.
 *
 * <p>On a {@code dryRun} it carries the derived manifest and the detected field
 * count without provisioning the site or importing any document, so an operator
 * (or the {@code turing migrate} CLI, T659) can review the schema before
 * committing. On a real run it also reports how many documents were read from the
 * source and successfully queued for indexing.</p>
 *
 * @param engine            the source engine ({@code "ELASTICSEARCH"} / {@code "ALGOLIA"}).
 * @param site              the target SN site name.
 * @param siteCreated       whether the site was created (vs. converged).
 * @param fieldsDetected    number of fields derived from the source schema.
 * @param fieldsCreated     field names newly created in the SN site.
 * @param fieldsSkipped     field names that already existed (idempotent no-op).
 * @param documentsRead     documents pulled from the source engine.
 * @param documentsImported documents reshaped and queued for indexing.
 * @param dryRun            true when nothing was provisioned or imported.
 * @param manifest          the derived field manifest (for review / re-use).
 * @param warnings          non-fatal notes (skipped unsupported field types, etc.).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurMigrationResult(
        String engine,
        String site,
        boolean siteCreated,
        int fieldsDetected,
        List<String> fieldsCreated,
        List<String> fieldsSkipped,
        int documentsRead,
        int documentsImported,
        boolean dryRun,
        VigletFieldManifest manifest,
        List<String> warnings) {
}
