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
package com.viglet.turing.commons.sn.bean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.commons.sn.search.TurSNResponseFormat;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * T707 / §XLV — the public, anonymous-capable, <strong>read-only</strong> search
 * request sent as a JSON body to {@code POST /api/sn/{siteName}/search/query}.
 *
 * <p>
 * This bean exists to break the query-string round-trip described in
 * IMPROVEMENTS §XLV.1: a JSON body has no query-string grammar, so a filter value
 * containing {@code & = + # %} or a space cannot be mis-read as query structure.
 * Every field mirrors a GET parameter of {@code /api/sn/{siteName}/search} so the
 * two entry points stay semantically identical.
 * </p>
 *
 * <p>
 * It is deliberately <em>not</em> {@link TurSNSitePostParamsBean}: that bean is
 * the impersonation/metrics-oriented payload of the authenticated POST and
 * carries {@code userId}, {@code populateMetrics} and the conditional targeting
 * maps. Those fields are privileged and must never be settable by an anonymous
 * caller, which is exactly why this request omits them.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class TurSNSearchRequest implements Serializable {

    /** Full-text query. Defaults to {@code *} (match all) when omitted. */
    @JsonAlias("query")
    private String q;

    /** Default filter queries, each a raw {@code field:value} pair. */
    private List<String> fq = new ArrayList<>();

    /** Filter queries combined with AND. */
    @JsonAlias("fq.and")
    private List<String> fqAnd = new ArrayList<>();

    /** Filter queries combined with OR. */
    @JsonAlias("fq.or")
    private List<String> fqOr = new ArrayList<>();

    /** Operator applied between filter-query groups. */
    @JsonAlias("fq.op")
    private TurSNFilterQueryOperator fqOperator;

    /** Operator applied between the items of a single filter-query group. */
    @JsonAlias({"fqi.op", "fqItemOp"})
    private TurSNFilterQueryOperator fqItemOperator;

    /** 1-based page number. */
    @JsonAlias("p")
    private Integer page;

    /** Page size. {@code -1} (the default) uses the site's configured rows. */
    private Integer rows;

    /** Sort option: {@code relevance}, {@code newest}, {@code oldest} or a custom sort name. */
    private String sort;

    /** Locale tag, e.g. {@code en_US}. Falls back to the site's first locale. */
    private String locale;

    /** Grouping field. */
    private String group;

    /** Fields to return; empty means the site default. */
    @JsonAlias("fl")
    private List<String> fieldList = new ArrayList<>();

    /** Targeting rules to apply to this request (no conditional maps — read-only). */
    private List<String> targetingRules = new ArrayList<>();

    /**
     * When true, spelling auto-correction is not applied. Defaults to {@code true}
     * so the POST contract matches the GET default ({@code nfpr=1}).
     */
    private boolean autoCorrectionDisabled = true;

    /**
     * T708 — which response shape to return. Defaults to
     * {@link TurSNResponseFormat#LINKS} so an existing consumer that starts POSTing
     * gets the response it already knows; opt into
     * {@link TurSNResponseFormat#STRUCTURED} to receive the link-free shape.
     *
     * @since 2026.3.4
     */
    private TurSNResponseFormat responseFormat = TurSNResponseFormat.LINKS;
}
