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

package com.viglet.turing.sn.manifest;

import java.util.List;

/**
 * The plan a manifest would apply against the live site — the "diff" half of
 * schema-as-code (T386 / §XX.6). Computed by
 * {@link TurSNManifestPlanner#diff(TurSNSiteManifest)} and returned verbatim by
 * the {@code POST /api/sn/manifest/plan} dry-run so a schema change is
 * reviewable before it is applied.
 *
 * @param schemaVersion       the manifest's declared schema version (may be {@code null}).
 * @param siteExists          {@code false} when the manifest would create a brand-new site
 *                            (then everything is additive — there is nothing to break).
 * @param fieldsToAdd         names of fields the manifest declares that the site lacks
 *                            (applied silently — additive).
 * @param breakingChanges     type/cardinality changes on fields that already exist; each
 *                            must be covered by a declared {@link TurSNFieldMigration} or
 *                            provisioning is rejected.
 * @param fieldsUnchanged     names of fields that already match the manifest (no-op).
 * @param localesToAdd        locale codes the manifest declares that the site lacks
 *                            (applied silently — additive).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNManifestDiff(
        String schemaVersion,
        boolean siteExists,
        List<String> fieldsToAdd,
        List<TurSNManifestFieldChange> breakingChanges,
        List<String> fieldsUnchanged,
        List<String> localesToAdd) {

    /** Whether any breaking change exists, regardless of whether it is declared. */
    public boolean hasBreakingChanges() {
        return breakingChanges != null && !breakingChanges.isEmpty();
    }

    /** Breaking changes the manifest does NOT declare a migration for — these block provisioning. */
    public List<TurSNManifestFieldChange> undeclaredBreakingChanges() {
        return breakingChanges == null ? List.of()
                : breakingChanges.stream().filter(c -> !c.migrationDeclared()).toList();
    }
}
