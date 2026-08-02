/*
 * Copyright (C) 2016-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.commons.sn.search;

/**
 * T708 / §XLV — which search response shape the caller wants.
 *
 * <p>
 * §XLV.5 left the negotiation mechanism open (request flag vs {@code Accept}
 * header). It is a <strong>request field</strong>: it is the simplest to
 * implement, it is visible in the request log next to the query it produced, and
 * it works identically over GET and POST — which matters because the whole point
 * of the flag is to A/B the two shapes against the same query.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurSNResponseFormat {

    /**
     * The legacy response: facet/pagination/spell-check items carry pre-serialized
     * GET query-string links the client is expected to navigate. Default, and
     * frozen — the GET response must stay byte-identical.
     */
    LINKS,

    /**
     * The link-free response: every "next request" is expressed as data the client
     * applies to the echoed request, so no href is ever parsed.
     */
    STRUCTURED;

    /**
     * T856 — parses the flag as it arrives on a <em>query string</em>:
     * case-insensitively, because the GET entry point is typed by hand into a URL
     * when A/B-ing the two shapes against one query. A blank value keeps the
     * {@link #LINKS} default; an unknown one throws
     * {@link IllegalArgumentException} (a 400) rather than silently degrading to
     * the legacy shape.
     *
     * @since 2026.3.4
     */
    public static TurSNResponseFormat of(String value) {
        if (value == null || value.isBlank()) {
            return LINKS;
        }
        return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
