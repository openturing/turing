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

import java.io.Serial;
import java.util.List;

import com.viglet.core.manifest.VigletFieldMigration;
import com.viglet.core.manifest.VigletManifestFieldChange;

/**
 * Raised when a manifest would change the type or cardinality of one or more
 * existing fields but does not declare a {@link VigletFieldMigration} for them
 * (T386 / §XX.6). Provisioning is rejected and nothing is mutated — the operator
 * must add an explicit migration to acknowledge the breaking change. Mapped to
 * HTTP 409 Conflict by {@code TurSNSiteManifestAPI}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurSNManifestMigrationRequiredException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<VigletManifestFieldChange> changes;

    public TurSNManifestMigrationRequiredException(List<VigletManifestFieldChange> changes) {
        super(buildMessage(changes));
        this.changes = changes;
    }

    public List<VigletManifestFieldChange> getChanges() {
        return changes;
    }

    private static String buildMessage(List<VigletManifestFieldChange> changes) {
        String fields = changes.stream()
                .map(c -> "%s.%s: %s -> %s".formatted(
                        c.field(), c.attribute(), c.liveValue(), c.manifestValue()))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return "Manifest declares breaking change(s) to existing field(s) without a migration: "
                + fields + ". Declare a migration to apply, or revert the change.";
    }
}
