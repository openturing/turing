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
 * How an explicitly-declared {@link TurSNFieldMigration} resolves a breaking
 * field change detected by the schema-as-code diff (T386 / §XX.6).
 *
 * <p>A breaking change — a {@code type} or {@code multiValued} change on a field
 * that already exists in the live site — cannot be applied silently because it
 * alters the search-engine schema and invalidates already-indexed values. The
 * operator must declare the intent in the manifest, the same way a Liquibase
 * change is declared rather than inferred.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurSNManifestMigrationAction {

    /**
     * Drop the live field (its DB rows and its search-engine schema definition
     * across every locale core) and recreate it from the manifest spec. This is
     * destructive: values already indexed under the old type are lost and the
     * source must reindex. It is the only faithful way to change a field's type
     * in a schema-managed engine.
     */
    RECREATE
}
