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
 * T1030 / §XX.17 — what a {@link TurSNManifest} actually converged.
 *
 * <p>{@link #isNoOp()} is the idempotence signal a connector checks on every
 * start-up: re-posting an unchanged manifest creates nothing, which is the whole
 * point of provisioning up front rather than at index time.
 *
 * @param siteId         the converged site id
 * @param siteName       the converged site name
 * @param siteCreated    {@code true} when the site did not exist and was created
 * @param fieldsCreated  fields newly added by this call
 * @param fieldsSkipped  fields that already existed unchanged
 * @param fieldsMigrated fields recreated to apply a declared breaking change
 * @param localesCreated locales newly provisioned by this call
 * @param schemaVersion  the manifest's declared version, echoed back
 * @param customFacetsChanged custom facets created or rewritten by this call
 *                       (T1084); {@code null} from a server that predates them
 * @param customSortsChanged custom sorts created or rewritten by this call
 *                       (T1104); {@code null} from a server that predates them
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNManifestResult(String siteId, String siteName, boolean siteCreated,
        List<String> fieldsCreated, List<String> fieldsSkipped, List<String> fieldsMigrated,
        List<String> localesCreated, String schemaVersion, List<String> customFacetsChanged,
        List<String> customSortsChanged) {

    /** The constructor this record had before {@link #customFacetsChanged}, kept for existing callers. */
    public TurSNManifestResult(String siteId, String siteName, boolean siteCreated,
            List<String> fieldsCreated, List<String> fieldsSkipped, List<String> fieldsMigrated,
            List<String> localesCreated, String schemaVersion) {
        this(siteId, siteName, siteCreated, fieldsCreated, fieldsSkipped, fieldsMigrated, localesCreated,
                schemaVersion, null, null);
    }

    /** The constructor this record had before {@link #customSortsChanged}, kept for existing callers. */
    public TurSNManifestResult(String siteId, String siteName, boolean siteCreated,
            List<String> fieldsCreated, List<String> fieldsSkipped, List<String> fieldsMigrated,
            List<String> localesCreated, String schemaVersion, List<String> customFacetsChanged) {
        this(siteId, siteName, siteCreated, fieldsCreated, fieldsSkipped, fieldsMigrated, localesCreated,
                schemaVersion, customFacetsChanged, null);
    }

    /** True when nothing changed — the site, every declared field, custom facet and custom sort already matched. */
    public boolean isNoOp() {
        return !siteCreated && isEmpty(fieldsCreated) && isEmpty(fieldsMigrated)
                && isEmpty(localesCreated) && isEmpty(customFacetsChanged) && isEmpty(customSortsChanged);
    }

    private static boolean isEmpty(List<String> values) {
        return values == null || values.isEmpty();
    }
}
