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

package com.viglet.turing.exchange.sn;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * T1033 / §D.13 — a site's indexed content offered as <em>lazily paged</em>
 * locales rather than a materialised map.
 *
 * <p>The export used to build the whole thing in memory first: every document of
 * every locale, each carrying its full stored text, held until the site was
 * finished and only then serialised. A site large enough for that to matter is
 * exactly the site somebody wants to export. Handing the writer an iterator
 * instead means nothing outlives the page it is in.
 *
 * <p>The written file's shape is unchanged —
 * <code>{ "&lt;site&gt;": { "&lt;locale&gt;": [ {doc}, {doc}, … ] } }</code> — so
 * an export produced this way imports exactly as before.
 *
 * <p>The iterators are <b>single-pass and stateful</b>: each {@code next()} calls
 * the search engine for the following page, and advancing one publishes export
 * progress. Consume each locale once, in order.
 *
 * @param siteName the site being exported — the outer JSON key
 * @param locales  one entry per configured locale, in the site's own order
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNSiteContentPages(String siteName, List<LocalePages> locales) {

    /**
     * One locale's documents, as pages pulled on demand.
     *
     * @param localeKey the locale's key — the inner JSON key
     * @param pages     successive non-empty pages; exhausted when the engine
     *                  returns nothing further
     */
    public record LocalePages(String localeKey, Iterator<List<Map<String, Object>>> pages) {
    }
}
