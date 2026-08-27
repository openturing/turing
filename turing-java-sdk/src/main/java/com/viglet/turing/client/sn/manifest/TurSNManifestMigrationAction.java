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
 * T1030 / §XX.17 — how a declared {@link TurSNManifestMigration} resolves a
 * breaking field change.
 *
 * <p>The constant names match the server's own migration actions exactly, so the
 * JSON is identical either side of the wire.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurSNManifestMigrationAction {

    /**
     * Drop the live field across every locale/core and recreate it from the
     * manifest spec. Destructive: values already indexed under the old type are
     * lost and the connector must reindex. It is the only faithful way to change
     * a field's type in a schema-managed engine.
     */
    RECREATE
}
