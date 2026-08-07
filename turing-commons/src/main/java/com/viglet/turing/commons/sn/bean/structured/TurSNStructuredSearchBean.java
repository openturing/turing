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
package com.viglet.turing.commons.sn.bean.structured;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.viglet.turing.commons.se.similar.TurSESimilarResult;
import com.viglet.turing.commons.sn.bean.TurSNSearchRequest;
import com.viglet.turing.commons.sn.bean.TurSNSearchResponse;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchQueryContextBean;
import com.viglet.turing.commons.sn.bean.TurSNSortOptionBean;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * T708 / §XLV — the link-free search response.
 *
 * <p>
 * Nothing here is a URL. The contract is: <em>take {@link #getRequest()}, apply
 * one structured change, POST it back</em> —
 * </p>
 * <ul>
 * <li>facet item → apply its {@code action} to {@code request.fq};</li>
 * <li>pagination → set {@code request.page};</li>
 * <li>clear a facet → remove the facet's {@code clearFilterQueries} from
 * {@code request.fq}; clear everything → remove {@link #getSelectedFilterQueries()};</li>
 * <li>locale switch → set {@code request.locale};</li>
 * <li>spell-check → set {@code request.q} (+ {@code autoCorrectionDisabled});</li>
 * <li>sort → set {@code request.sort}. The available options are served by
 * {@code GET /api/sn/{site}/search/sort-options} and are <em>not</em> included
 * by default, because the control is already rendered on every request after the
 * first; ask for {@code includeSortOptions} to have them inlined here instead
 * (T857).</li>
 * </ul>
 *
 * <p>
 * {@code queryContext} is carried over from the legacy response unchanged.
 * {@code results} is <em>projected</em> since T864: §XLV.3 assumed it was
 * already link-free, and the T859 end-to-end run proved otherwise — each
 * document's {@code metadata} carried the document's own facet hrefs. Every
 * other property keeps its legacy name and shape.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TurSNStructuredSearchBean implements TurSNSearchResponse, Serializable {

    /**
     * The request that produced this response, with every default resolved. This
     * is the object the client mutates and re-sends — it is why no href is needed.
     */
    private TurSNSearchRequest request;

    /**
     * T879 / §LXX.1 — the site's configured icon, a plain Iconify name. It is the
     * one widget property that was never link-shaped, so dropping it with the
     * hrefs made every site with an icon fall back to the Turing logo.
     *
     * @since 2026.3.4
     */
    private String icon;

    private TurSNSiteSearchQueryContextBean queryContext;

    private TurSNStructuredResultsBean results;

    private List<TurSNStructuredGroupBean> groups;

    private List<TurSNStructuredPageBean> pagination;

    private List<TurSNStructuredFacetBean> facets;

    private List<TurSNStructuredFacetBean> secondaryFacets;

    /** Every filter query currently applied — remove them all to clear the search. */
    private List<String> selectedFilterQueries;

    private TurSNStructuredSpellCheckBean spellCheck;

    private List<TurSNStructuredLocaleBean> locales;

    /**
     * T857 / §LXV.2 — the site's sort options, present only when the request set
     * {@code includeSortOptions}. Same shape and same source as
     * {@code GET /api/sn/{site}/search/sort-options}; put the chosen
     * {@code value} on {@code request.sort}.
     *
     * @since 2026.3.4
     */
    private List<TurSNSortOptionBean> sortOptions;

    private List<TurSESimilarResult> similar;

    /**
     * T938 / §XLV.8 — projected rather than carried across: the legacy bean names
     * the document's destination {@code link}, which is indistinguishable from
     * the navigation hrefs this response exists to remove.
     */
    private List<TurSNStructuredSpotlightBean> spotlights;
}
