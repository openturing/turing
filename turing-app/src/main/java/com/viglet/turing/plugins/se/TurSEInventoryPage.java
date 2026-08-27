/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.plugins.se;

import java.util.List;

/**
 * T1029 / §XX.16 — one page of the index inventory returned by
 * {@link TurSearchEnginePlugin#retrieveInventoryPage}.
 *
 * <p>Paging is a <b>keyset cursor over the id</b>, not an offset: each page is
 * sorted by id ascending and the next one asks for ids strictly greater than
 * {@link #nextCursor()}. A deep offset costs the engine more the further in it
 * goes, which is exactly the regime a full-site audit runs in, so the offset
 * form was not an option.
 *
 * @param entries    the page's rows, id-ascending
 * @param nextCursor the last id on this page — pass it back as {@code after} to
 *                   continue; {@code null} when the page is empty
 * @param hasMore    whether the engine reported rows beyond this page
 * @param remaining  documents matching the source filter from the cursor
 *                   onward, this page included — so on the first page it is the
 *                   source's total, and on any page it is what is left to read.
 *                   Every engine reports this for free with the page itself;
 *                   the source total on a later page would cost a second query
 *                   per page, which a full-site sweep is the wrong place to
 *                   spend. {@code -1} when the engine cannot count
 * @param supported  {@code false} when the engine has no inventory capability,
 *                   so a caller distinguishes "nothing indexed" from "cannot
 *                   answer" instead of reading an empty page as an empty index
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSEInventoryPage(List<TurSEInventoryEntry> entries, String nextCursor,
        boolean hasMore, long remaining, boolean supported) {

    /** The engine cannot enumerate its index — never an empty index. */
    public static TurSEInventoryPage unsupported() {
        return new TurSEInventoryPage(List.of(), null, false, -1L, false);
    }

    /** The engine answered and nothing matched. */
    public static TurSEInventoryPage empty() {
        return new TurSEInventoryPage(List.of(), null, false, 0L, true);
    }

    /**
     * Builds a supported page from the rows the engine returned, deriving both
     * the cursor and {@code hasMore} so no implementation has to remember to.
     *
     * @param remaining the engine's match count from the cursor onward
     */
    public static TurSEInventoryPage of(List<TurSEInventoryEntry> entries, long remaining) {
        if (entries == null || entries.isEmpty()) {
            return new TurSEInventoryPage(List.of(), null, false, Math.max(remaining, 0L), true);
        }
        return new TurSEInventoryPage(List.copyOf(entries),
                entries.get(entries.size() - 1).id(), remaining > entries.size(), remaining, true);
    }
}
