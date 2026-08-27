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
package com.viglet.turing.client.sn.inventory;

import java.util.List;

/**
 * T1035 / §XX.20 — one page of {@code GET /api/sn/{site}/inventory}.
 *
 * <p>Most callers should not touch this: {@link TurSNInventoryClient#entries}
 * walks the cursor for you. Read a page directly only to checkpoint a sweep
 * across runs, which is the one thing the iterator cannot do for you.
 *
 * @param siteId     the audited site's id
 * @param siteName   its name
 * @param locale     the language tag of the index this page enumerated
 * @param locales    every locale the site has, in position order — a full sweep
 *                   is one pass per entry
 * @param source     the {@code source_apps} filter applied, or {@code null}
 * @param entries    this page's rows, id-ascending
 * @param nextCursor pass back as {@code after} to continue; {@code null} at the end
 * @param hasMore    whether anything remains beyond this page
 * @param remaining  rows matching the filter from the cursor onward, this page
 *                   included — so on the first page it is the source's total
 * @param supported  {@code false} when the site's engine cannot enumerate its
 *                   index. <b>Check this.</b> An empty page then means "cannot
 *                   answer", and acting on it as "the index is empty" is what
 *                   makes an audit republish a whole source
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNInventoryPage(String siteId, String siteName, String locale,
        List<String> locales, String source, List<TurSNInventoryEntry> entries, String nextCursor,
        boolean hasMore, long remaining, boolean supported) {

    /** True when the engine answered and this source has nothing indexed. */
    public boolean isEmptyIndex() {
        return supported && (entries == null || entries.isEmpty());
    }
}
