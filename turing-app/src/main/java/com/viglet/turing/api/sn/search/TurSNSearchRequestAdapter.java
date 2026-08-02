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
package com.viglet.turing.api.sn.search;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;

import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSearchRequest;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.commons.sn.search.TurSNParamType;
import com.viglet.turing.commons.utils.TurCommonsUtils;

/**
 * T707 / §XLV — translates the public JSON {@link TurSNSearchRequest} into the
 * internal search primitives, and rebuilds the <em>canonical GET URI</em> the
 * legacy link-bearing response is rendered against.
 *
 * <p>
 * Two responsibilities, both mechanical:
 * </p>
 * <ol>
 * <li>{@link #toSearchParams} maps the JSON body onto {@link TurSNSearchParams},
 * the same object the GET mapping binds from query parameters — so both entry
 * points feed one identical pipeline;</li>
 * <li>{@link #canonicalUri} synthesises {@code /api/sn/{site}/search?…} from the
 * body. The response's facet/pagination hrefs are built by appending to the
 * request URI, and a POST arrives at {@code …/search/query}; without this the
 * emitted links would point at the POST endpoint and stop being deep-linkable.
 * The query string is written through
 * {@link TurCommonsUtils#addParameterToQueryString} — the same already-hardened
 * encoder the link builders use — so this single server-side hop cannot
 * reintroduce the {@code &}-in-a-value bug the block is closing.</li>
 * </ol>
 *
 * <p>
 * The derived {@link TurSNSitePostParamsBean} is an <strong>internal</strong>
 * carrier only: it never receives {@code userId} or the conditional targeting
 * maps, so an anonymous caller cannot reach the impersonation surface.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurSNSearchRequestAdapter {

    private static final String QUERY_SUFFIX = "/query";
    private static final String DEFAULT_QUERY = "*";

    private TurSNSearchRequestAdapter() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Maps the public request onto the internal {@link TurSNSearchParams}. Absent
     * fields keep the same defaults the GET mapping applies.
     */
    public static TurSNSearchParams toSearchParams(TurSNSearchRequest request, Locale locale) {
        TurSNSearchParams params = new TurSNSearchParams();
        params.setQ(StringUtils.defaultIfBlank(request.getQ(), DEFAULT_QUERY));
        params.setFq(nullIfEmpty(request.getFq()));
        params.setFqAnd(nullIfEmpty(request.getFqAnd()));
        params.setFqOr(nullIfEmpty(request.getFqOr()));
        params.setFqOp(defaultOperator(request.getFqOperator()));
        params.setFqiOp(defaultOperator(request.getFqItemOperator()));
        if (request.getPage() != null) {
            params.setP(request.getPage());
        }
        if (request.getRows() != null) {
            params.setRows(request.getRows());
        }
        if (StringUtils.isNotBlank(request.getSort())) {
            params.setSort(request.getSort());
        }
        if (StringUtils.isNotBlank(request.getGroup())) {
            params.setGroup(request.getGroup());
        }
        params.setFl(nullIfEmpty(request.getFieldList()));
        params.setNfpr(request.isAutoCorrectionDisabled() ? 1 : 0);
        params.setLocale(locale);
        return params;
    }

    /**
     * Builds the internal post-params carrier. Only the read-only targeting rules
     * cross over; {@code userId} and the conditional maps stay untouched so the
     * anonymous path can never impersonate.
     */
    public static TurSNSitePostParamsBean toPostParams(TurSNSearchRequest request) {
        TurSNSitePostParamsBean postParams = new TurSNSitePostParamsBean();
        postParams.setTargetingRules(
                request.getTargetingRules() == null ? List.of() : request.getTargetingRules());
        return postParams;
    }

    /**
     * T708 — the request with every default resolved, echoed back in the
     * structured response as the object the client mutates for its next call. It
     * is a copy: the caller's bean is never modified.
     *
     * @since 2026.3.4
     */
    public static TurSNSearchRequest effectiveRequest(TurSNSearchRequest request,
            TurSNSearchParams params, Locale locale) {
        TurSNSearchRequest effective = new TurSNSearchRequest();
        effective.setQ(params.getQ());
        effective.setFq(orEmpty(params.getFq()));
        effective.setFqAnd(orEmpty(params.getFqAnd()));
        effective.setFqOr(orEmpty(params.getFqOr()));
        effective.setFqOperator(params.getFqOp());
        effective.setFqItemOperator(params.getFqiOp());
        effective.setPage(params.getP());
        effective.setRows(params.getRows());
        effective.setSort(params.getSort());
        effective.setGroup(params.getGroup());
        effective.setFieldList(orEmpty(params.getFl()));
        effective.setLocale(locale == null ? null : locale.toString());
        effective.setTargetingRules(orEmpty(request.getTargetingRules()));
        effective.setAutoCorrectionDisabled(request.isAutoCorrectionDisabled());
        effective.setResponseFormat(request.getResponseFormat());
        return effective;
    }

    /** Resolves the request's locale, or {@code null} when it carries none. */
    public static Locale resolveLocale(TurSNSearchRequest request) {
        return StringUtils.isBlank(request.getLocale())
                ? null
                : LocaleUtils.toLocale(request.getLocale());
    }

    /**
     * Rebuilds the GET-equivalent URI for a POST call, so the legacy link-bearing
     * response keeps emitting deep-linkable {@code /api/sn/{site}/search?…} hrefs.
     *
     * @param requestUri the URI the POST actually arrived on (…/search/query)
     * @param request    the JSON body
     * @param locale     the resolved locale (may be {@code null})
     */
    public static URI canonicalUri(URI requestUri, TurSNSearchRequest request, Locale locale) {
        StringBuilder queryString = new StringBuilder();
        TurCommonsUtils.addParameterToQueryString(queryString, TurSNParamType.QUERY,
                StringUtils.defaultIfBlank(request.getQ(), DEFAULT_QUERY));
        addAll(queryString, TurSNParamType.FILTER_QUERIES_DEFAULT, request.getFq());
        addAll(queryString, TurSNParamType.FILTER_QUERIES_AND, request.getFqAnd());
        addAll(queryString, TurSNParamType.FILTER_QUERIES_OR, request.getFqOr());
        addOperator(queryString, TurSNParamType.FILTER_QUERY_OPERATOR, request.getFqOperator());
        addOperator(queryString, TurSNParamType.FILTER_QUERY_ITEM_OPERATOR,
                request.getFqItemOperator());
        TurCommonsUtils.addParameterToQueryString(queryString, TurSNParamType.PAGE,
                String.valueOf(request.getPage() == null ? 1 : request.getPage()));
        if (request.getRows() != null) {
            TurCommonsUtils.addParameterToQueryString(queryString, TurSNParamType.ROWS,
                    String.valueOf(request.getRows()));
        }
        if (StringUtils.isNotBlank(request.getSort())) {
            TurCommonsUtils.addParameterToQueryString(queryString, TurSNParamType.SORT,
                    request.getSort());
        }
        if (StringUtils.isNotBlank(request.getGroup())) {
            TurCommonsUtils.addParameterToQueryString(queryString, TurSNParamType.GROUP,
                    request.getGroup());
        }
        addAll(queryString, TurSNParamType.FIELD_LIST, request.getFieldList());
        if (locale != null) {
            TurCommonsUtils.addParameterToQueryString(queryString, TurSNParamType.LOCALE,
                    locale.toString());
        }
        return TurCommonsUtils.modifiedURI(searchUri(requestUri), queryString);
    }

    /**
     * Strips the trailing {@code /query} segment so the canonical URI sits on the
     * GET counterpart of whichever POST variant was called
     * ({@code …/search/query} → {@code …/search}, and since T712
     * {@code …/ac/query} → {@code …/ac}, {@code …/search/list/query} →
     * {@code …/search/list}). Any other incoming path is left as-is.
     */
    private static URI searchUri(URI requestUri) {
        String path = requestUri.getRawPath() == null ? "" : requestUri.getRawPath();
        if (path.endsWith(QUERY_SUFFIX)) {
            path = path.substring(0, path.length() - QUERY_SUFFIX.length());
        }
        return URI.create(path);
    }

    private static void addAll(StringBuilder queryString, String name, List<String> values) {
        if (values == null) {
            return;
        }
        values.stream().filter(StringUtils::isNotBlank)
                .forEach(value -> TurCommonsUtils.addParameterToQueryString(queryString, name, value));
    }

    private static void addOperator(StringBuilder queryString, String name,
            TurSNFilterQueryOperator operator) {
        if (operator != null && operator != TurSNFilterQueryOperator.NONE) {
            TurCommonsUtils.addParameterToQueryString(queryString, name, operator.name());
        }
    }

    private static TurSNFilterQueryOperator defaultOperator(TurSNFilterQueryOperator operator) {
        return operator == null ? TurSNFilterQueryOperator.NONE : operator;
    }

    private static List<String> orEmpty(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private static List<String> nullIfEmpty(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return Collections.unmodifiableList(values);
    }
}
