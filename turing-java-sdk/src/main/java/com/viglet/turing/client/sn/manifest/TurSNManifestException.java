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

import java.io.Serial;
import java.util.List;

/**
 * T1030 / §XX.17 — provisioning did not converge the site.
 *
 * <p>Indexing tolerates a failure by retrying the job; provisioning does not,
 * because every document that follows would be indexed against a schema the
 * connector did not declare. So the client throws rather than returning a
 * boolean: a connector that ignores this has silently gone back to inferring
 * fields at index time, which is the failure provisioning exists to remove.
 *
 * <p>{@link #getBreakingChanges()} is populated only for the HTTP 409 case — a
 * manifest that would rewrite an existing field's type or cardinality without
 * declaring a {@link TurSNManifestMigration}. That is not a transport error and
 * retrying will not fix it: the connector either declares the migration or
 * changes the manifest.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurSNManifestException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** HTTP status the server returned, or {@code -1} when the call never completed. */
    private final int statusCode;

    private final List<TurSNManifestFieldChange> breakingChanges;

    public TurSNManifestException(String message, int statusCode,
            List<TurSNManifestFieldChange> breakingChanges) {
        super(message);
        this.statusCode = statusCode;
        this.breakingChanges = breakingChanges == null ? List.of() : List.copyOf(breakingChanges);
    }

    public TurSNManifestException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.breakingChanges = List.of();
    }

    public int getStatusCode() {
        return statusCode;
    }

    /** The undeclared breaking changes behind a 409; empty for every other failure. */
    public List<TurSNManifestFieldChange> getBreakingChanges() {
        return breakingChanges;
    }

    /** True when the site was refused because a breaking change was not declared. */
    public boolean isMigrationRequired() {
        return statusCode == 409;
    }
}
