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
 * T1104 — a custom sort a {@link TurSNManifest} provisions, such as "closing
 * soonest": a date ascending, documents without one last. A search selects it by
 * passing its name as {@code sort}.
 *
 * <p>The server matches it to the live sort by {@code name}: absent, it is
 * created; different, its description and levels are replaced; equal, nothing
 * happens. A sort the manifest does not declare is left as the console made it.
 *
 * @param name        the sort name, unique per site (required); not
 *                    {@code relevance}, {@code newest} or {@code oldest}
 * @param description shown beside the sort in the console
 * @param items       the sort levels, in order (at least one)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.5
 */
public record TurSNManifestCustomSort(String name, String description, List<TurSNManifestCustomSortItem> items) {

    /** A sort with its levels and no description. */
    public static TurSNManifestCustomSort of(String name, List<TurSNManifestCustomSortItem> items) {
        return new TurSNManifestCustomSort(name, null, items == null ? null : List.copyOf(items));
    }
}
