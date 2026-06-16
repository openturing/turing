/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.core.settings;

import java.util.Map;
import java.util.Optional;

/**
 * Persistence port for the runtime config-var store: a tiny {@code id -> value}
 * key-value table grouped by a {@code path}. A product implements this over its
 * own JPA entity + repository (Turing's {@code TurConfigVar}, Shio's
 * {@code ShConfigVar}, Dumont's {@code DumConnectorConfigVarModel}); the entity,
 * table name, caching and UUID strategy stay per-product because they bind to
 * that product's schema.
 *
 * <p>The reusable half — the typed get/set boilerplate every product
 * re-implemented by hand (null-coalesce, trim, parse-or-default, clamp,
 * case-insensitive enum resolution) — lives in {@link VigletConfigVarService},
 * which talks only to this port and never to JPA. This keeps {@code viglet-core}
 * persistence-agnostic: it owns the behavior, the product owns the storage.
 *
 * <p>Lifted from the three duplicated implementations into {@code viglet-core}
 * (T376 / Block Q, STRATEGY §XII.2).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface VigletConfigVarStore {

    /**
     * The raw stored value for {@code id}, or empty when no row exists. An
     * implementation may also return empty for a {@code null} stored value.
     */
    Optional<String> find(String id);

    /**
     * Upsert: create the row for {@code id} or update its value in place,
     * stamping {@code path}. Implementations preserve any other columns on an
     * existing row.
     */
    void put(String id, String path, String value);

    /**
     * Every {@code key -> value} pair under {@code path}. The meaning of the key
     * is the implementation's choice (the row id, or a distinct {@code name}
     * column where the product has one).
     */
    Map<String, String> findByPath(String path);

    /** Whether a row exists for {@code id}. */
    boolean exists(String id);

    /** Delete the row for {@code id}; a no-op when none exists. */
    void remove(String id);
}
