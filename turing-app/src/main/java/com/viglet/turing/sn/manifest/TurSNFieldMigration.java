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

/**
 * An explicit acknowledgement, carried in a {@link TurSNSiteManifest}, that a
 * breaking change to an existing field is intended (T386 / §XX.6).
 *
 * <p>Provisioning never silently rewrites the type or cardinality of a field
 * that already exists — it diffs the manifest against the live site and, when a
 * breaking change is found, requires a matching migration entry. This is the
 * "Liquibase for the SN field schema" contract: additive changes converge
 * silently, breaking changes are declared, not inferred. A migration with no
 * corresponding breaking change in the diff is harmless (a no-op).</p>
 *
 * @param field  the name of the field the migration applies to (matched against
 *               the manifest field spec by name).
 * @param action how to resolve the breaking change; see
 *               {@link TurSNManifestMigrationAction}. {@code null} defaults to
 *               {@link TurSNManifestMigrationAction#RECREATE}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNFieldMigration(String field, TurSNManifestMigrationAction action) {

    public TurSNManifestMigrationAction actionOrDefault() {
        return action == null ? TurSNManifestMigrationAction.RECREATE : action;
    }
}
