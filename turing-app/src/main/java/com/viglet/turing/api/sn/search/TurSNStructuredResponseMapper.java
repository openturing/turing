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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.viglet.turing.commons.sn.bean.TurSNSearchRequest;
import com.viglet.turing.commons.sn.bean.TurSNSiteLocaleBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchFacetBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchFacetItemBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentMetadataBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchGroupBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchPaginationBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchResultsBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchWidgetBean;
import com.viglet.turing.commons.sn.bean.spellcheck.TurSNSiteSpellCheckBean;
import com.viglet.turing.commons.sn.bean.structured.TurSNStructuredFacetBean;
import com.viglet.turing.commons.sn.bean.structured.TurSNStructuredFacetItemBean;
import com.viglet.turing.commons.sn.bean.structured.TurSNStructuredDocumentBean;
import com.viglet.turing.commons.sn.bean.structured.TurSNStructuredGroupBean;
import com.viglet.turing.commons.sn.bean.structured.TurSNStructuredMetadataBean;
import com.viglet.turing.commons.sn.bean.structured.TurSNStructuredLocaleBean;
import com.viglet.turing.commons.sn.bean.structured.TurSNStructuredPageBean;
import com.viglet.turing.commons.sn.bean.structured.TurSNStructuredResultsBean;
import com.viglet.turing.commons.sn.bean.structured.TurSNStructuredSearchBean;
import com.viglet.turing.commons.sn.bean.structured.TurSNStructuredSpellCheckBean;
import com.viglet.turing.commons.sn.pagination.TurSNPaginationType;
import com.viglet.turing.commons.sn.search.TurSNFacetItemAction;

/**
 * T708 / §XLV — projects the legacy link-bearing {@link TurSNSiteSearchBean}
 * onto the link-free {@link TurSNStructuredSearchBean}.
 *
 * <p>
 * This is deliberately a <strong>pure post-processing pass</strong> over an
 * already-computed response, not a second search path: §XLV.3 observed that the
 * structured state the client needs is state the pipeline already produces
 * ({@code filterQuery} + {@code selected} + {@code count} on every facet item,
 * {@code page} + {@code type} on every pagination entry,
 * {@code selectedFilterQueries} on the widget). So the mapper only has to
 * <em>expose</em> it and drop the hrefs — the search itself is untouched, which
 * is why the two formats can never disagree about results.
 * </p>
 *
 * <p>
 * The one piece that is not recoverable from the legacy bean is the facet-item
 * {@link TurSNFacetItemAction}: whether clicking adds to or replaces the field's
 * selection depends on the field's per-field facet-item mode, known only inside
 * the renderer. It is therefore carried on a {@code @JsonIgnore} field of the
 * legacy item — an in-process hand-off that leaves the legacy JSON untouched.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurSNStructuredResponseMapper {

    private TurSNStructuredResponseMapper() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * @param searchBean the legacy response to project
     * @param request    the effective request, echoed so the client has something
     *                   to mutate and re-send
     * @param locale     the locale the response was produced in
     */
    public static TurSNStructuredSearchBean toStructured(TurSNSiteSearchBean searchBean,
            TurSNSearchRequest request, Locale locale) {
        TurSNStructuredSearchBean structured = new TurSNStructuredSearchBean()
                .setRequest(request)
                .setQueryContext(searchBean.getQueryContext())
                .setResults(results(searchBean.getResults()))
                .setPagination(pages(searchBean.getPagination(), currentPage(searchBean)))
                .setGroups(groups(searchBean.getGroups()));
        TurSNSiteSearchWidgetBean widget = searchBean.getWidget();
        if (widget == null) {
            return structured;
        }
        return structured
                .setFacets(facets(widget.getFacet()))
                .setSecondaryFacets(facets(widget.getSecondaryFacet()))
                .setSelectedFilterQueries(widget.getSelectedFilterQueries())
                .setSpellCheck(spellCheck(widget.getSpellCheck()))
                .setLocales(locales(widget.getLocales(), locale))
                .setSimilar(widget.getSimilar())
                .setSpotlights(widget.getSpotlights());
    }

    private static int currentPage(TurSNSiteSearchBean searchBean) {
        return searchBean.getQueryContext() == null ? 1 : searchBean.getQueryContext().getPage();
    }

    private static List<TurSNStructuredPageBean> pages(List<TurSNSiteSearchPaginationBean> pagination,
            int currentPage) {
        if (pagination == null) {
            return null;
        }
        List<TurSNStructuredPageBean> pages = new ArrayList<>();
        pagination.forEach(page -> pages.add(new TurSNStructuredPageBean()
                .setType(page.getType())
                .setText(page.getText())
                .setPage(page.getPage())
                .setCurrent(page.getType() == TurSNPaginationType.CURRENT
                        || page.getPage() == currentPage)));
        return pages;
    }

    /**
     * T864 / §LXV.6 — projects the result list so a document's facet chips stop
     * being URLs. Everything else about a document is copied across by name.
     */
    private static TurSNStructuredResultsBean results(TurSNSiteSearchResultsBean results) {
        if (results == null) {
            return null;
        }
        TurSNStructuredResultsBean structured = new TurSNStructuredResultsBean();
        if (results.getDocument() == null) {
            return structured;
        }
        List<TurSNStructuredDocumentBean> documents = new ArrayList<>();
        results.getDocument().forEach(document -> documents.add(document(document)));
        return structured.setDocument(documents);
    }

    private static TurSNStructuredDocumentBean document(TurSNSiteSearchDocumentBean document) {
        return new TurSNStructuredDocumentBean()
                .setSource(document.getSource())
                .setElevate(document.isElevate())
                .setFields(document.getFields())
                .setRankingExplanation(document.getRankingExplanation())
                .setDuplicateCluster(document.getDuplicateCluster())
                .setMetadata(metadata(document.getMetadata()));
    }

    private static List<TurSNStructuredMetadataBean> metadata(
            List<TurSNSiteSearchDocumentMetadataBean> metadata) {
        if (metadata == null) {
            return null;
        }
        List<TurSNStructuredMetadataBean> structured = new ArrayList<>();
        metadata.forEach(item -> structured.add(new TurSNStructuredMetadataBean()
                .setField(item.getField())
                .setValue(item.getValue())
                .setFilterQuery(item.getFilterQuery())
                .setText(item.getText())));
        return structured;
    }

    private static List<TurSNStructuredGroupBean> groups(List<TurSNSiteSearchGroupBean> groups) {
        if (groups == null) {
            return null;
        }
        List<TurSNStructuredGroupBean> structuredGroups = new ArrayList<>();
        groups.forEach(group -> structuredGroups.add(new TurSNStructuredGroupBean()
                .setName(group.getName())
                .setCount(group.getCount())
                .setPage(group.getPage())
                .setPageCount(group.getPageCount())
                .setPageEnd(group.getPageEnd())
                .setPageStart(group.getPageStart())
                .setLimit(group.getLimit())
                .setResults(results(group.getResults()))
                .setPagination(pages(group.getPagination(), group.getPage()))));
        return structuredGroups;
    }

    private static List<TurSNStructuredFacetBean> facets(List<TurSNSiteSearchFacetBean> facets) {
        if (facets == null) {
            return null;
        }
        List<TurSNStructuredFacetBean> structuredFacets = new ArrayList<>();
        facets.forEach(facet -> structuredFacets.add(facet(facet)));
        return structuredFacets;
    }

    private static TurSNStructuredFacetBean facet(TurSNSiteSearchFacetBean facet) {
        List<TurSNStructuredFacetItemBean> items = new ArrayList<>();
        if (facet.getFacets() != null) {
            facet.getFacets().forEach(item -> items.add(item(facet.getName(), item)));
        }
        return new TurSNStructuredFacetBean()
                .setName(facet.getName())
                .setLabel(facet.getLabel() == null ? null : facet.getLabel().getText())
                .setLabelLang(facet.getLabel() == null ? null : facet.getLabel().getLang())
                .setDescription(facet.getDescription())
                .setType(facet.getType())
                .setMultiValued(facet.isMultiValued())
                .setSelectedFilterQueries(facet.getSelectedFilterQueries())
                // "Clear this facet" is exactly "drop the field's current
                // selections" — the legacy cleanUpLink navigated to a URL with
                // precisely those removed.
                .setClearFilterQueries(facet.getSelectedFilterQueries())
                .setItems(items);
    }

    private static TurSNStructuredFacetItemBean item(String field,
            TurSNSiteSearchFacetItemBean item) {
        return new TurSNStructuredFacetItemBean()
                .setField(field)
                .setValue(value(field, item.getFilterQuery()))
                .setFilterQuery(item.getFilterQuery())
                .setLabel(item.getLabel())
                .setCount(item.getCount())
                .setSelected(item.isSelected())
                .setAction(Optional.ofNullable(item.getAction())
                        .orElse(item.isSelected() ? TurSNFacetItemAction.REMOVE
                                : TurSNFacetItemAction.ADD));
    }

    /**
     * Splits {@code field:value} on the <em>field's own</em> prefix rather than on
     * the first colon, because a value may legitimately contain one (a URL, a
     * timestamp).
     */
    private static String value(String field, String filterQuery) {
        if (filterQuery == null) {
            return null;
        }
        String prefix = field + ":";
        if (field != null && filterQuery.startsWith(prefix)) {
            return filterQuery.substring(prefix.length());
        }
        return StringUtils.substringAfter(filterQuery, ":");
    }

    private static TurSNStructuredSpellCheckBean spellCheck(TurSNSiteSpellCheckBean spellCheck) {
        if (spellCheck == null) {
            return null;
        }
        return new TurSNStructuredSpellCheckBean()
                .setCorrectedText(spellCheck.isCorrectedText())
                .setUsingCorrectedText(spellCheck.isUsingCorrectedText())
                .setOriginalText(spellCheck.getOriginal() == null ? null
                        : spellCheck.getOriginal().getText())
                .setCorrectedTextValue(spellCheck.getCorrected() == null ? null
                        : spellCheck.getCorrected().getText());
    }

    private static List<TurSNStructuredLocaleBean> locales(List<TurSNSiteLocaleBean> locales,
            Locale current) {
        if (locales == null) {
            return null;
        }
        List<TurSNStructuredLocaleBean> structuredLocales = new ArrayList<>();
        locales.forEach(localeBean -> {
            String tag = localeBean.getLocale() == null ? null : localeBean.getLocale().toString();
            structuredLocales.add(new TurSNStructuredLocaleBean()
                    .setLocale(tag)
                    .setSelected(current != null && current.toString().equals(tag)));
        });
        return structuredLocales;
    }
}
