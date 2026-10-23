/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.turing.client.sn.manifest;

import java.util.List;

/**
 * T1030 / §XX.17 — the plan a manifest would apply against the live schema: the
 * dry-run a connector runs before provisioning, so a schema change is reviewable
 * rather than discovered from a 409.
 *
 * @param schemaVersion   the manifest's declared version (may be {@code null})
 * @param siteExists      {@code false} when the manifest would create the site,
 *                        in which case everything is additive
 * @param fieldsToAdd     fields the manifest declares that the site lacks
 *                        (applied silently)
 * @param breakingChanges type/cardinality changes on fields that already exist
 * @param fieldsUnchanged fields that already match the manifest
 * @param localesToAdd    locales the manifest declares that the site lacks
 * @param customFacetsToChange custom facets provisioning would create or
 *                        rewrite (T1084); {@code null} from a server that
 *                        predates them
 * @param customSortsToChange custom sorts provisioning would create or rewrite
 *                        (T1104); {@code null} from a server that predates them
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNManifestDiff(String schemaVersion, boolean siteExists, List<String> fieldsToAdd,
        List<TurSNManifestFieldChange> breakingChanges, List<String> fieldsUnchanged,
        List<String> localesToAdd, List<String> customFacetsToChange, List<String> customSortsToChange) {

    /** The constructor this record had before {@link #customFacetsToChange}, kept for existing callers. */
    public TurSNManifestDiff(String schemaVersion, boolean siteExists, List<String> fieldsToAdd,
            List<TurSNManifestFieldChange> breakingChanges, List<String> fieldsUnchanged,
            List<String> localesToAdd) {
        this(schemaVersion, siteExists, fieldsToAdd, breakingChanges, fieldsUnchanged, localesToAdd, null, null);
    }

    /** The constructor this record had before {@link #customSortsToChange}, kept for existing callers. */
    public TurSNManifestDiff(String schemaVersion, boolean siteExists, List<String> fieldsToAdd,
            List<TurSNManifestFieldChange> breakingChanges, List<String> fieldsUnchanged,
            List<String> localesToAdd, List<String> customFacetsToChange) {
        this(schemaVersion, siteExists, fieldsToAdd, breakingChanges, fieldsUnchanged, localesToAdd,
                customFacetsToChange, null);
    }

    /** Whether any breaking change exists, declared or not. */
    public boolean hasBreakingChanges() {
        return breakingChanges != null && !breakingChanges.isEmpty();
    }

    /**
     * The breaking changes no migration authorises. A non-empty result is exactly
     * what {@link TurSNManifestClient#provision} would reject, so a connector can
     * fail its own start-up with the reason instead of posting and catching.
     */
    public List<TurSNManifestFieldChange> undeclaredBreakingChanges() {
        return breakingChanges == null ? List.of()
                : breakingChanges.stream().filter(c -> !c.migrationDeclared()).toList();
    }

    /** True when applying this manifest would change nothing. */
    public boolean isNoOp() {
        return siteExists && isEmpty(fieldsToAdd) && isEmpty(localesToAdd)
                && isEmpty(customFacetsToChange) && isEmpty(customSortsToChange) && !hasBreakingChanges();
    }

    private static boolean isEmpty(List<String> values) {
        return values == null || values.isEmpty();
    }
}
