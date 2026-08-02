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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.bean.TurSNFilterParams;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;

/**
 * T858 / §LXV.3 — the search cache key, derived from the
 * <strong>effective parameters</strong> of a {@link TurSNSiteSearchContext}.
 *
 * <p>
 * It replaces {@code TurSNUtils.getCacheKey(siteName, HttpServletRequest)},
 * which was {@code siteName + "_" + request.getQueryString()}. That is correct
 * exactly as long as the query string <em>is</em> the request — true for GET,
 * false for every POST endpoint, where it collapses to {@code <site>_null} and
 * one cached result is served for every distinct body. The trap was hit twice
 * independently (T707 and T712), which is evidence about the primitive rather
 * than about the two authors: the signature made the wrong thing the easy thing,
 * and a mistake here is <em>silent</em> (wrong results, no error).
 * </p>
 *
 * <p>
 * Every transport already builds a context, so keying on it means the key cannot
 * depend on how the request arrived — a new POST endpoint cannot forget to
 * supply one.
 * </p>
 *
 * <p>
 * The key is a readable, injective encoding rather than a hash, so it stays
 * greppable in the cache log. Injectivity comes from escaping the three
 * separators in every value ({@code \}, {@code |} and {@code ,}), so no
 * combination of values can spell out another combination — the property the
 * query-string key lacked.
 * </p>
 *
 * <p>
 * Deliberately excluded: {@code userId} and {@code populateMetrics}, which are
 * metrics/impersonation concerns that do not change which documents match —
 * including them would fragment the cache per user for no gain.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurSNSearchCacheKey {

    private static final char SEPARATOR = '|';
    private static final char LIST_SEPARATOR = ',';
    private static final char ESCAPE = '\\';

    private TurSNSearchCacheKey() {
        throw new IllegalStateException("Utility class");
    }

    /** The cache key for a search, whatever transport produced the context. */
    public static String of(TurSNSiteSearchContext context) {
        StringBuilder key = new StringBuilder();
        append(key, "site", context.getSiteName());
        append(key, "locale", context.getLocale() == null ? null : context.getLocale().toString());
        append(key, "hl", context.getTurSNConfig() != null
                && context.getTurSNConfig().isHlEnabled() ? "1" : "0");
        appendParameters(key, context.getTurSEParameters());
        appendTargeting(key, context.getTurSNSitePostParamsBean());
        return key.toString();
    }

    private static void appendParameters(StringBuilder key, TurSEParameters parameters) {
        if (parameters == null) {
            return;
        }
        append(key, "q", parameters.getQuery());
        append(key, "page", parameters.getCurrentPage());
        append(key, "rows", parameters.getRows());
        append(key, "sort", parameters.getSort());
        append(key, "group", parameters.getGroup());
        append(key, "nfpr", parameters.getAutoCorrectionDisabled());
        appendList(key, "fl", parameters.getFieldList());
        appendList(key, "bq", parameters.getBoostQueries());
        appendFilters(key, parameters.getTurSNFilterParams());
        // Facet sets carry no defined iteration order, so they are sorted —
        // otherwise the same request could produce two different keys.
        appendList(key, "afct", sorted(parameters.getAllowedFacets()));
        appendList(key, "+fct", sorted(parameters.getAddedFacets()));
        appendList(key, "-fct", sorted(parameters.getRemovedFacets()));
    }

    private static void appendFilters(StringBuilder key, TurSNFilterParams filters) {
        if (filters == null) {
            return;
        }
        appendList(key, "fq", filters.getDefaultValues());
        appendList(key, "fqAnd", filters.getAnd());
        appendList(key, "fqOr", filters.getOr());
        append(key, "fqOp", filters.getOperator());
        append(key, "fqiOp", filters.getItemOperator());
    }

    /**
     * Targeting rules change which documents match, so two bodies that differ
     * only in their rules must not share a cached response — the exact collision
     * the query-string key allowed on the authenticated POST, whose rules travel
     * in the body while the key was read off the URL.
     */
    private static void appendTargeting(StringBuilder key, TurSNSitePostParamsBean postParams) {
        if (postParams == null) {
            return;
        }
        appendList(key, "tr", postParams.getTargetingRules());
        appendMap(key, "trc", postParams.getTargetingRulesWithCondition());
        appendMap(key, "trcAnd", postParams.getTargetingRulesWithConditionAND());
        appendMap(key, "trcOr", postParams.getTargetingRulesWithConditionOR());
    }

    private static void appendMap(StringBuilder key, String name, Map<String, List<String>> map) {
        if (map == null || map.isEmpty()) {
            return;
        }
        new TreeMap<>(map).forEach((entryKey, values) -> appendList(key,
                name + "." + entryKey, values));
    }

    private static List<String> sorted(Collection<String> values) {
        return values == null || values.isEmpty() ? null : List.copyOf(new TreeSet<>(values));
    }

    private static void appendList(StringBuilder key, String name, Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        key.append(name).append('=');
        boolean first = true;
        for (String value : values) {
            if (!first) {
                key.append(LIST_SEPARATOR);
            }
            escape(key, value);
            first = false;
        }
        key.append(SEPARATOR);
    }

    private static void append(StringBuilder key, String name, Object value) {
        if (value == null) {
            return;
        }
        key.append(name).append('=');
        escape(key, value.toString());
        key.append(SEPARATOR);
    }

    private static void escape(StringBuilder key, String value) {
        if (value == null) {
            return;
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == ESCAPE || character == SEPARATOR || character == LIST_SEPARATOR) {
                key.append(ESCAPE);
            }
            key.append(character);
        }
    }
}
