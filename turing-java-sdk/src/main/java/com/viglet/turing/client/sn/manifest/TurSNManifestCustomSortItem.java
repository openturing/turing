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
 * T1104 — one level of a {@link TurSNManifestCustomSort}.
 *
 * <p>{@code order} and {@code missing} are spelled as the server's enum names, so
 * the SDK carries no server type.
 *
 * @param field   the site field to sort by (required)
 * @param order   {@code ASC} (the server's default) or {@code DESC}
 * @param missing where documents without the field go, {@code FIRST} or
 *                {@code LAST}; {@code null} keeps the engine's placement
 *
 * @author Alexandre Oliveira
 * @since 2026.3.5
 */
public record TurSNManifestCustomSortItem(String field, String order, String missing) {

    public static TurSNManifestCustomSortItem asc(String field) {
        return new TurSNManifestCustomSortItem(field, "ASC", null);
    }

    public static TurSNManifestCustomSortItem desc(String field) {
        return new TurSNManifestCustomSortItem(field, "DESC", null);
    }

    /** This level with documents that lack the field after every one that has it. */
    public TurSNManifestCustomSortItem missingLast() {
        return new TurSNManifestCustomSortItem(field, order, "LAST");
    }

    /** This level with documents that lack the field before every one that has it. */
    public TurSNManifestCustomSortItem missingFirst() {
        return new TurSNManifestCustomSortItem(field, order, "FIRST");
    }
}
