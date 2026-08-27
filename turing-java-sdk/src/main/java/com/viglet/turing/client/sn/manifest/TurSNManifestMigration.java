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

/**
 * T1030 / §XX.17 — an explicit acknowledgement, carried in a
 * {@link TurSNManifest}, that a breaking change to an existing field is
 * intended.
 *
 * <p>Provisioning never silently rewrites the type or cardinality of a field
 * that already exists: it diffs the manifest against the live schema and rejects
 * an undeclared breaking change with HTTP 409. Declaring the migration here is
 * how a connector authorises the rewrite — the same way a database migration is
 * declared rather than inferred. A migration with no matching breaking change is
 * harmless.
 *
 * @param field  the field the migration applies to
 * @param action how to resolve the change; {@code null} means
 *               {@link TurSNManifestMigrationAction#RECREATE}
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNManifestMigration(String field, TurSNManifestMigrationAction action) {

    /** Declares a {@code RECREATE} migration for one field. */
    public static TurSNManifestMigration recreate(String field) {
        return new TurSNManifestMigration(field, TurSNManifestMigrationAction.RECREATE);
    }
}
