/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.sn.inventory;

import java.util.List;

import com.viglet.turing.plugins.se.TurSEInventoryEntry;

/**
 * T1029 / §XX.16 — one page of the answer to "what does the index hold for this
 * source", as returned by {@code GET /api/sn/{snSiteId}/inventory}.
 *
 * <p>The index is per (site, locale), so a caller auditing a multilingual site
 * runs the sweep once per locale: {@link #locale()} says which one this page
 * enumerated and {@link #locales()} lists the rest, so the loop needs no second
 * endpoint to discover them.
 *
 * @param siteId     the audited site
 * @param siteName   the site's name, for the caller's own report
 * @param locale     the language tag of the index this page enumerated
 * @param locales    every language tag configured on the site, in position
 *                   order — the full sweep is one pass per entry
 * @param source     the {@code source_apps} filter applied, or {@code null} when
 *                   the whole index was enumerated
 * @param entries    the page's {@code (id, version)} rows, id-ascending
 * @param nextCursor pass back as {@code after} for the next page; {@code null}
 *                   at the end
 * @param hasMore    whether anything remains beyond this page
 * @param remaining  documents matching the filter from the cursor onward, this
 *                   page included; {@code -1} when the engine cannot count
 * @param supported  {@code false} when the site's engine has no inventory
 *                   capability — an empty page then means "cannot answer", not
 *                   "the index is empty"
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNIndexInventoryResponse(String siteId, String siteName, String locale,
        List<String> locales, String source, List<TurSEInventoryEntry> entries, String nextCursor,
        boolean hasMore, long remaining, boolean supported) {
}
