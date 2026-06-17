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
 * Outcome of a {@link TurSNSiteManifestService#provision(TurSNSiteManifest)}
 * call: what converged. Re-running the same manifest yields {@code siteCreated=false}
 * with empty {@code fieldsCreated}/{@code localesCreated} — the signal that the
 * provisioning was a no-op.
 *
 * @param siteId         the converged site id.
 * @param siteName       the converged site name.
 * @param siteCreated    {@code true} when the site did not exist and was created.
 * @param fieldsCreated  field names newly added by this call.
 * @param fieldsSkipped  field names that already existed unchanged (idempotent no-op).
 * @param fieldsMigrated field names recreated to apply a declared breaking change
 *                       (T386); empty unless a {@link TurSNFieldMigration} fired.
 * @param localesCreated locale codes newly provisioned by this call.
 * @param schemaVersion  the manifest's declared schema version, echoed back (T386).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNManifestResult(
        String siteId,
        String siteName,
        boolean siteCreated,
        List<String> fieldsCreated,
        List<String> fieldsSkipped,
        List<String> fieldsMigrated,
        List<String> localesCreated,
        String schemaVersion) {
}
