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

package com.viglet.turing.api.sn.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.commons.se.similar.TurSESimilarResult;
import com.viglet.turing.commons.sn.bean.TurSNSearchRequest;
import com.viglet.turing.commons.sn.bean.TurSNSiteLocaleBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSpotlightDocumentBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchFacetBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchFacetItemBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchFacetLabelBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentMetadataBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchPaginationBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchQueryContextBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchResultsBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchWidgetBean;
import com.viglet.turing.commons.sn.bean.spellcheck.TurSNSiteSpellCheckBean;
import com.viglet.turing.commons.sn.bean.spellcheck.TurSNSiteSpellCheckText;
import com.viglet.turing.commons.sn.bean.structured.TurSNStructuredFacetBean;
import com.viglet.turing.commons.sn.bean.structured.TurSNStructuredDocumentBean;
import com.viglet.turing.commons.sn.bean.structured.TurSNStructuredMetadataBean;
import com.viglet.turing.commons.sn.bean.structured.TurSNStructuredSearchBean;
import com.viglet.turing.commons.sn.bean.structured.TurSNStructuredSpotlightBean;
import com.viglet.turing.commons.sn.pagination.TurSNPaginationType;
import com.viglet.turing.commons.sn.search.TurSNFacetItemAction;

/**
 * T708 / §XLV — tests for the link-free structured response projection.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNStructuredResponseMapperTest {

    private static final String RESERVED_VALUE = "RAG & Chat";
    private static final String RESERVED_FQ = "section:" + RESERVED_VALUE;
    private static final String SITE_ICON = "tabler:building-store";
    private static final String SPOTLIGHT_DESTINATION = "/content/wknd/pinned";

    private static TurSNSiteSearchFacetItemBean item(String filterQuery, boolean selected,
            TurSNFacetItemAction action) {
        return new TurSNSiteSearchFacetItemBean()
                .setCount(7)
                .setLabel("label")
                .setFilterQuery(filterQuery)
                .setSelected(selected)
                .setAction(action)
                .setLink("/api/sn/wknd/search?q=*&fq[]=" + filterQuery);
    }

    private static TurSNSiteSearchBean legacyBean() {
        TurSNSiteSearchFacetBean facet = new TurSNSiteSearchFacetBean()
                .setName("section")
                .setLabel(new TurSNSiteSearchFacetLabelBean().setLang("en_US").setText("Section"))
                .setDescription("Content section")
                .setMultiValued(true)
                .setSelectedFilterQueries(List.of(RESERVED_FQ))
                .setCleanUpLink("/api/sn/wknd/search?q=*")
                .setFacets(List.of(
                        item(RESERVED_FQ, true, TurSNFacetItemAction.REMOVE),
                        item("section:Deploy & Operate", false, TurSNFacetItemAction.ADD)));

        TurSNSiteSpellCheckBean spellCheck = new TurSNSiteSpellCheckBean();
        spellCheck.setCorrectedText(true);
        spellCheck.setUsingCorrectedText(false);
        TurSNSiteSpellCheckText original = new TurSNSiteSpellCheckText();
        original.setText("serch");
        original.setLink("/api/sn/wknd/search?q=serch&nfpr=1");
        TurSNSiteSpellCheckText corrected = new TurSNSiteSpellCheckText();
        corrected.setText("search");
        corrected.setLink("/api/sn/wknd/search?q=search");
        spellCheck.setOriginal(original);
        spellCheck.setCorrected(corrected);

        TurSNSiteLocaleBean localeBean = new TurSNSiteLocaleBean()
                .setLocale(Locale.US)
                .setLink("/api/sn/wknd/search?_setlocale=en_US");
        TurSNSiteLocaleBean otherLocale = new TurSNSiteLocaleBean()
                .setLocale(Locale.of("pt", "BR"))
                .setLink("/api/sn/wknd/search?_setlocale=pt_BR");

        // T938 — the guard below string-matches the whole serialized response, so
        // every link-bearing part of the legacy bean has to be *in* the fixture.
        // A spotlight is the one that was missing, and it is where the block's
        // central claim actually failed.
        TurSNSiteSpotlightDocumentBean spotlight = new TurSNSiteSpotlightDocumentBean()
                .setId("sp-1")
                .setPosition(1)
                .setTitle("Pinned")
                .setType("Page")
                .setReferenceId("ref-1")
                .setContent("Editorially pinned")
                .setLink(SPOTLIGHT_DESTINATION);

        TurSNSiteSearchWidgetBean widget = new TurSNSiteSearchWidgetBean()
                .setIcon(SITE_ICON)
                .setCleanUpFacets("/api/sn/wknd/search?q=*")
                .setSelectedFilterQueries(List.of(RESERVED_FQ))
                .setFacet(List.of(facet))
                .setSimilar(List.of(TurSESimilarResult.builder().id("doc-2").title("Neighbour")
                        .type("Page").url("/content/wknd/neighbour").build()))
                .setSpotlights(List.of(spotlight))
                .setSpellCheck(spellCheck)
                .setLocales(List.of(localeBean, otherLocale));

        return new TurSNSiteSearchBean()
                .setQueryContext(new TurSNSiteSearchQueryContextBean().setPage(2).setCount(42))
                .setResults(legacyResults())
                .setWidget(widget)
                .setPagination(List.of(
                        new TurSNSiteSearchPaginationBean().setType(TurSNPaginationType.FIRST)
                                .setText("1").setPage(1).setHref("/api/sn/wknd/search?p=1"),
                        new TurSNSiteSearchPaginationBean().setType(TurSNPaginationType.CURRENT)
                                .setText("2").setPage(2).setHref("/api/sn/wknd/search?p=2")));
    }

    private static TurSNStructuredSearchBean map() {
        TurSNSearchRequest request = new TurSNSearchRequest();
        request.setFq(List.of(RESERVED_FQ));
        return TurSNStructuredResponseMapper.toStructured(legacyBean(), request, Locale.US);
    }

    /**
     * The load-bearing assertion of the block: nothing in the structured response
     * is a URL the client would have to parse.
     *
     * <p>
     * T938 / §XLV.8 — this used to pass on an incomplete fixture. The widget had
     * no spotlight in it, and {@code TurSNSiteSpotlightDocumentBean} was carried
     * across whole with a field named {@code link}; adding one failed the
     * assertion, which is what the projection in {@code spotlights(…)} fixes. The
     * fixture now holds every link-bearing part of the legacy bean, so the guard
     * asserts over the whole shape rather than the parts that happened to be set.
     * </p>
     */
    @Test
    void testStructuredResponseCarriesNoLinks() throws JsonProcessingException {
        String json = new ObjectMapper().writeValueAsString(map());

        assertThat(json)
                .doesNotContain("\"link\"")
                .doesNotContain("\"href\"")
                .doesNotContain("\"cleanUpLink\"")
                .doesNotContain("\"cleanUpFacets\"")
                .doesNotContain("/api/sn/");
    }

    /**
     * T938 — the fixture is only a guard if it actually carries the parts the
     * guard is about. This fails the moment a spotlight or a similar document
     * stops reaching the serialized response, which is how the hole opened.
     */
    @Test
    void testGuardFixtureCarriesTheLinkBearingParts() throws JsonProcessingException {
        String json = new ObjectMapper().writeValueAsString(map());

        assertThat(json).contains("\"spotlights\"").contains("\"similar\"");
    }

    /**
     * T938 — a spotlight's destination is the document's own URL, the same kind of
     * value {@code similar[].url} carries, so it survives the projection under
     * that name instead of being dropped with the navigation hrefs.
     */
    @Test
    void testSpotlightDestinationSurvivesAsUrl() {
        TurSNStructuredSpotlightBean spotlight = map().getSpotlights().getFirst();

        assertThat(spotlight.getId()).isEqualTo("sp-1");
        assertThat(spotlight.getPosition()).isEqualTo(1);
        assertThat(spotlight.getTitle()).isEqualTo("Pinned");
        assertThat(spotlight.getType()).isEqualTo("Page");
        assertThat(spotlight.getReferenceId()).isEqualTo("ref-1");
        assertThat(spotlight.getContent()).isEqualTo("Editorially pinned");
        assertThat(spotlight.getUrl()).isEqualTo(SPOTLIGHT_DESTINATION);
    }

    /** A site with no spotlight leaves the field absent rather than empty. */
    @Test
    void testMissingSpotlightsStayNull() {
        TurSNSiteSearchBean bean = legacyBean();
        bean.getWidget().setSpotlights(null);

        assertThat(TurSNStructuredResponseMapper
                .toStructured(bean, new TurSNSearchRequest(), Locale.US).getSpotlights()).isNull();
    }

    /**
     * The block is additive: the legacy shape is frozen. The facet-item
     * {@code action} is an in-process hand-off and must never reach the wire.
     */
    @Test
    void testLegacyFacetItemJsonIsUnchangedByTheActionField() throws JsonProcessingException {
        String json = new ObjectMapper()
                .writeValueAsString(item(RESERVED_FQ, true, TurSNFacetItemAction.REMOVE));

        assertThat(json).doesNotContain("action")
                .contains("\"link\"")
                .contains("\"filterQuery\"")
                .contains("\"selected\"");
    }

    @Test
    void testFacetItemIsDecomposedIntoFieldAndValue() {
        TurSNStructuredFacetBean facet = map().getFacets().getFirst();

        assertThat(facet.getName()).isEqualTo("section");
        assertThat(facet.getLabel()).isEqualTo("Section");
        assertThat(facet.getLabelLang()).isEqualTo("en_US");
        assertThat(facet.isMultiValued()).isTrue();
        assertThat(facet.getItems()).hasSize(2);
        assertThat(facet.getItems().getFirst().getField()).isEqualTo("section");
        assertThat(facet.getItems().getFirst().getValue()).isEqualTo(RESERVED_VALUE);
        assertThat(facet.getItems().getFirst().getFilterQuery()).isEqualTo(RESERVED_FQ);
        assertThat(facet.getItems().getFirst().getCount()).isEqualTo(7);
    }

    /**
     * T879 / §LXX.1 — the icon was the one widget property that carried no link,
     * and dropping it with the hrefs made every site with an icon render the
     * generic Turing logo.
     */
    @Test
    void testSiteIconSurvivesTheProjection() {
        assertThat(map().getIcon()).isEqualTo(SITE_ICON);
    }

    /** A site with no icon leaves the field absent, so the client can fall back. */
    @Test
    void testMissingSiteIconStaysNull() {
        TurSNSiteSearchBean bean = legacyBean();
        bean.getWidget().setIcon(null);

        assertThat(TurSNStructuredResponseMapper
                .toStructured(bean, new TurSNSearchRequest(), Locale.US).getIcon()).isNull();
    }

    /** "Clear this facet" becomes a list to subtract, not a URL to navigate. */
    @Test
    void testClearFilterQueriesReplacesTheCleanUpLink() {
        assertThat(map().getFacets().getFirst().getClearFilterQueries())
                .containsExactly(RESERVED_FQ);
        assertThat(map().getSelectedFilterQueries()).containsExactly(RESERVED_FQ);
    }

    @Test
    void testFacetItemActionsAreCarriedThrough() {
        List<com.viglet.turing.commons.sn.bean.structured.TurSNStructuredFacetItemBean> items =
                map().getFacets().getFirst().getItems();

        assertThat(items.getFirst().isSelected()).isTrue();
        assertThat(items.getFirst().getAction()).isEqualTo(TurSNFacetItemAction.REMOVE);
        assertThat(items.get(1).isSelected()).isFalse();
        assertThat(items.get(1).getAction()).isEqualTo(TurSNFacetItemAction.ADD);
    }

    /**
     * A field in AND facet-item mode single-selects, so its unselected items must
     * say REPLACE — this is the piece that cannot be recovered from the item alone.
     */
    @Test
    void testReplaceActionSurvivesTheProjection() {
        TurSNSiteSearchBean bean = legacyBean();
        bean.getWidget().getFacet().getFirst().getFacets().get(1)
                .setAction(TurSNFacetItemAction.REPLACE);

        TurSNStructuredSearchBean structured = TurSNStructuredResponseMapper
                .toStructured(bean, new TurSNSearchRequest(), Locale.US);

        assertThat(structured.getFacets().getFirst().getItems().get(1).getAction())
                .isEqualTo(TurSNFacetItemAction.REPLACE);
    }

    /** A legacy item with no action still yields a usable one. */
    @Test
    void testMissingActionFallsBackToSelectionState() {
        TurSNSiteSearchBean bean = legacyBean();
        bean.getWidget().getFacet().getFirst().getFacets().forEach(item -> item.setAction(null));

        List<com.viglet.turing.commons.sn.bean.structured.TurSNStructuredFacetItemBean> items =
                TurSNStructuredResponseMapper
                        .toStructured(bean, new TurSNSearchRequest(), Locale.US)
                        .getFacets().getFirst().getItems();

        assertThat(items.getFirst().getAction()).isEqualTo(TurSNFacetItemAction.REMOVE);
        assertThat(items.get(1).getAction()).isEqualTo(TurSNFacetItemAction.ADD);
    }

    /** A value containing a colon must not be split on the first one. */
    @Test
    void testValueWithColonIsSplitOnTheFieldPrefix() {
        TurSNSiteSearchBean bean = legacyBean();
        bean.getWidget().getFacet().getFirst().setFacets(List.of(
                item("section:https://example.com/a:b", false, TurSNFacetItemAction.ADD)));

        assertThat(TurSNStructuredResponseMapper
                .toStructured(bean, new TurSNSearchRequest(), Locale.US)
                .getFacets().getFirst().getItems().getFirst().getValue())
                        .isEqualTo("https://example.com/a:b");
    }

    @Test
    void testPaginationBecomesPageNumbers() {
        assertThat(map().getPagination()).hasSize(2);
        assertThat(map().getPagination().getFirst().getPage()).isEqualTo(1);
        assertThat(map().getPagination().getFirst().isCurrent()).isFalse();
        assertThat(map().getPagination().get(1).getType()).isEqualTo(TurSNPaginationType.CURRENT);
        assertThat(map().getPagination().get(1).isCurrent()).isTrue();
    }

    @Test
    void testLocalesMarkTheCurrentOne() {
        assertThat(map().getLocales()).hasSize(2);
        assertThat(map().getLocales().getFirst().getLocale()).isEqualTo("en_US");
        assertThat(map().getLocales().getFirst().isSelected()).isTrue();
        assertThat(map().getLocales().get(1).getLocale()).isEqualTo("pt_BR");
        assertThat(map().getLocales().get(1).isSelected()).isFalse();
    }

    @Test
    void testSpellCheckBecomesTextsNotLinks() {
        assertThat(map().getSpellCheck().isCorrectedText()).isTrue();
        assertThat(map().getSpellCheck().isUsingCorrectedText()).isFalse();
        assertThat(map().getSpellCheck().getOriginalText()).isEqualTo("serch");
        assertThat(map().getSpellCheck().getCorrectedTextValue()).isEqualTo("search");
    }

    @Test
    void testQueryContextIsCarriedOverUnchanged() {
        TurSNSiteSearchBean bean = legacyBean();

        TurSNStructuredSearchBean structured = TurSNStructuredResponseMapper
                .toStructured(bean, new TurSNSearchRequest(), Locale.US);

        assertThat(structured.getQueryContext()).isSameAs(bean.getQueryContext());
    }

    /**
     * T864 / §LXV.6 — a document's facet chips become data. Everything else about
     * the document is copied across by name, so a result renderer is unchanged.
     */
    @Test
    void testDocumentMetadataBecomesFilterQueriesNotLinks() {
        TurSNStructuredDocumentBean document = map().getResults().getDocument().get(0);

        assertThat(document.getSource()).isEqualTo("/content/wknd/deploy");
        assertThat(document.isElevate()).isTrue();
        assertThat(document.getFields()).containsEntry("title", RESERVED_VALUE);

        TurSNStructuredMetadataBean chip = document.getMetadata().get(0);
        assertThat(chip.getField()).isEqualTo("section");
        assertThat(chip.getValue()).isEqualTo(RESERVED_VALUE);
        assertThat(chip.getFilterQuery()).isEqualTo(RESERVED_FQ);
        assertThat(chip.getText()).isEqualTo(RESERVED_VALUE);
    }

    /**
     * T869 / §LXVII.5 — the chip inherits the facet rail's verdict for the same
     * {@code field:value}. {@code section:RAG & Chat} is applied, so the rail says
     * REMOVE and the chip must too — otherwise clicking it re-adds a filter that is
     * already there and nothing happens.
     */
    @Test
    void testDocumentChipInheritsTheFacetRailSelectionAndAction() {
        TurSNStructuredMetadataBean chip = map().getResults().getDocument().get(0).getMetadata()
                .get(0);

        assertThat(chip.isSelected()).isTrue();
        assertThat(chip.getAction()).isEqualTo(TurSNFacetItemAction.REMOVE);
    }

    /** A single-select (AND) field replaces rather than adds — on the chip as well. */
    @Test
    void testDocumentChipCarriesReplaceForASingleSelectField() {
        TurSNSiteSearchBean bean = legacyBean();
        bean.getWidget().setSelectedFilterQueries(List.of());
        bean.getWidget().getFacet().getFirst()
                .setFacets(List.of(item(RESERVED_FQ, false, TurSNFacetItemAction.REPLACE)));

        TurSNStructuredMetadataBean chip = TurSNStructuredResponseMapper
                .toStructured(bean, new TurSNSearchRequest(), Locale.US)
                .getResults().getDocument().get(0).getMetadata().get(0);

        assertThat(chip.isSelected()).isFalse();
        assertThat(chip.getAction()).isEqualTo(TurSNFacetItemAction.REPLACE);
    }

    /**
     * A facet renders only up to its item limit, so a document can carry a chip for
     * a value the rail never listed. It still has to be actionable.
     */
    @Test
    void testDocumentChipOutsideTheFacetRailFallsBackToItsSelectionState() {
        TurSNSiteSearchBean bean = legacyBean();
        bean.getWidget().getFacet().getFirst()
                .setFacets(List.of(item("section:Something Else", false,
                        TurSNFacetItemAction.ADD)));

        TurSNStructuredMetadataBean chip = TurSNStructuredResponseMapper
                .toStructured(bean, new TurSNSearchRequest(), Locale.US)
                .getResults().getDocument().get(0).getMetadata().get(0);

        // Still in widget.selectedFilterQueries, just not among the rendered items.
        assertThat(chip.isSelected()).isTrue();
        assertThat(chip.getAction()).isEqualTo(TurSNFacetItemAction.REMOVE);
    }

    /** No widget means no facet state — the chips must still map, unselected. */
    @Test
    void testDocumentChipDefaultsToAddWhenThereIsNoWidget() {
        TurSNStructuredMetadataBean chip = TurSNStructuredResponseMapper
                .toStructured(new TurSNSiteSearchBean().setResults(legacyResults()),
                        new TurSNSearchRequest(), Locale.US)
                .getResults().getDocument().get(0).getMetadata().get(0);

        assertThat(chip.isSelected()).isFalse();
        assertThat(chip.getAction()).isEqualTo(TurSNFacetItemAction.ADD);
    }

    /** A response with no documents at all must not blow up the projection. */
    @Test
    void testResultsProjectionToleratesAnEmptyResponse() {
        TurSNStructuredSearchBean structured = TurSNStructuredResponseMapper.toStructured(
                new TurSNSiteSearchBean().setResults(new TurSNSiteSearchResultsBean()),
                new TurSNSearchRequest(), Locale.US);

        assertThat(structured.getResults()).isNotNull();
        assertThat(structured.getResults().getDocument()).isNull();
    }

    private static TurSNSiteSearchResultsBean legacyResults() {
        return new TurSNSiteSearchResultsBean().setDocument(List.of(
                TurSNSiteSearchDocumentBean.builder()
                        .source("/content/wknd/deploy")
                        .elevate(true)
                        .fields(java.util.Map.of("title", RESERVED_VALUE))
                        .metadata(List.of(TurSNSiteSearchDocumentMetadataBean.builder()
                                .href("/api/sn/wknd/search?q=*&fq[]=section:RAG%20%26%20Chat")
                                .text(RESERVED_VALUE)
                                .field("section")
                                .value(RESERVED_VALUE)
                                .filterQuery(RESERVED_FQ)
                                .build()))
                        .build()));
    }

    @Test
    void testResponseWithoutWidgetStillMaps() {
        TurSNStructuredSearchBean structured = TurSNStructuredResponseMapper.toStructured(
                new TurSNSiteSearchBean().setQueryContext(new TurSNSiteSearchQueryContextBean()),
                new TurSNSearchRequest(), Locale.US);

        assertThat(structured.getFacets()).isNull();
        assertThat(structured.getPagination()).isNull();
    }

    /** The echoed request is what the client mutates, so it must carry the defaults. */
    @Test
    void testEffectiveRequestEchoesResolvedDefaults() {
        TurSNSearchRequest request = new TurSNSearchRequest();
        request.setFq(List.of(RESERVED_FQ));

        TurSNSearchRequest effective = TurSNSearchRequestAdapter.effectiveRequest(request,
                TurSNSearchRequestAdapter.toSearchParams(request, Locale.US), Locale.US);

        assertThat(effective.getQ()).isEqualTo("*");
        assertThat(effective.getPage()).isEqualTo(1);
        assertThat(effective.getSort()).isEqualTo("relevance");
        assertThat(effective.getLocale()).isEqualTo("en_US");
        assertThat(effective.getFq()).containsExactly(RESERVED_FQ);
        assertThat(effective.getFqAnd()).isEmpty();
    }

    @Test
    void testEffectiveRequestIsACopy() {
        TurSNSearchRequest request = new TurSNSearchRequest();
        request.setQ("original");

        TurSNSearchRequest effective = TurSNSearchRequestAdapter.effectiveRequest(request,
                TurSNSearchRequestAdapter.toSearchParams(request, Locale.US), Locale.US);
        effective.setQ("mutated");

        assertThat(request.getQ()).isEqualTo("original");
    }
}
