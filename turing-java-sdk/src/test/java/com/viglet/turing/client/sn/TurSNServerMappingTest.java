package com.viglet.turing.client.sn;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.turing.client.sn.response.QueryTurSNResponse;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchFacetBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchFacetItemBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchFacetLabelBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchGroupBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchPaginationBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchQueryContextBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchResultsBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchWidgetBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSpotlightDocumentBean;
import com.viglet.turing.commons.sn.bean.spellcheck.TurSNSiteSpellCheckBean;
import com.viglet.turing.commons.sn.bean.spellcheck.TurSNSiteSpellCheckText;
import com.viglet.turing.commons.sn.pagination.TurSNPaginationType;

class TurSNServerMappingTest {

    @Test
    void shouldCreateTuringResponseFromSearchBean() throws Exception {
        TurSNSiteSearchBean searchBean = new TurSNSiteSearchBean();

        TurSNSiteSearchQueryContextBean queryContext = new TurSNSiteSearchQueryContextBean();
        queryContext.setCount(1);

        TurSNSiteSearchDocumentBean documentBean = TurSNSiteSearchDocumentBean.builder()
                .fields(Map.of("title", "Document One"))
                .build();
        TurSNSiteSearchResultsBean resultsBean = new TurSNSiteSearchResultsBean();
        resultsBean.setDocument(List.of(documentBean));

        TurSNSiteSearchPaginationBean paginationBean = new TurSNSiteSearchPaginationBean();
        paginationBean.setPage(1);
        paginationBean.setType(TurSNPaginationType.CURRENT);
        paginationBean.setText("1");
        paginationBean.setHref("http://localhost/search?p=1");

        TurSNSiteSearchGroupBean groupBean = new TurSNSiteSearchGroupBean();
        groupBean.setName("type");
        groupBean.setCount(1);
        groupBean.setLimit(10);
        groupBean.setPage(1);
        groupBean.setPageCount(1);
        groupBean.setPageStart(1);
        groupBean.setPageEnd(1);
        groupBean.setResults(resultsBean);
        groupBean.setPagination(List.of(paginationBean));

        TurSNSiteSearchFacetLabelBean facetLabelBean = new TurSNSiteSearchFacetLabelBean();
        facetLabelBean.setText("Type");
        TurSNSiteSearchFacetItemBean facetItemBean = new TurSNSiteSearchFacetItemBean();
        facetItemBean.setLabel("Page");
        facetItemBean.setCount(1);
        facetItemBean.setLink("http://localhost/search?fq=type:Page");
        facetItemBean.setFilterQuery("type:Page");

        TurSNSiteSearchFacetBean facetBean = new TurSNSiteSearchFacetBean();
        facetBean.setLabel(facetLabelBean);
        facetBean.setName("type");
        facetBean.setDescription("Type field");
        facetBean.setType(TurSEFieldType.STRING);
        facetBean.setFacets(List.of(facetItemBean));

        TurSNSiteSpellCheckBean spellCheckBean = new TurSNSiteSpellCheckBean();
        spellCheckBean.setCorrectedText(true);
        spellCheckBean.setOriginal(new TurSNSiteSpellCheckText(URI.create("http://localhost"), "orig", true));
        spellCheckBean.setCorrected(new TurSNSiteSpellCheckText(URI.create("http://localhost"), "corr", false));

        TurSNSiteSpotlightDocumentBean spotlightBean = new TurSNSiteSpotlightDocumentBean();
        spotlightBean.setId("spot-1");
        spotlightBean.setPosition(1);
        spotlightBean.setTitle("Spotlight");
        spotlightBean.setType("Page");
        spotlightBean.setReferenceId("ref-1");
        spotlightBean.setContent("content");
        spotlightBean.setLink("http://localhost/doc");

        TurSNSiteSearchWidgetBean widgetBean = new TurSNSiteSearchWidgetBean();
        widgetBean.setFacet(List.of(facetBean));
        widgetBean.setFacetToRemove(facetBean);
        widgetBean.setSpellCheck(spellCheckBean);
        widgetBean.setSpotlights(List.of(spotlightBean));

        searchBean.setQueryContext(queryContext);
        searchBean.setResults(resultsBean);
        searchBean.setGroups(List.of(groupBean));
        searchBean.setPagination(List.of(paginationBean));
        searchBean.setWidget(widgetBean);

        TurSNServer server = new TurSNServer(URI.create("http://localhost:2700"), "Portal", Locale.US);
        QueryTurSNResponse response = invokeCreateTuringResponse(server, searchBean);

        assertThat(response.getResults()).isNotNull();
        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().getTurSNDocuments().getFirst().getFieldValue("title"))
                .isEqualTo("Document One");
        assertThat(response.getGroupResponse()).isNotNull();
        assertThat(response.getGroupResponse()).hasSize(1);
        assertThat(response.getFacetFields().getFields()).hasSize(1);
        assertThat(response.getDidYouMean().isCorrectedText()).isTrue();
        assertThat(response.getSpotlightDocuments()).hasSize(1);
        assertThat(response.getPagination()).isNotNull();
    }

    @Test
    void shouldHandleHasSearchResultsBranches() throws Exception {
        TurSNServer server = new TurSNServer(URI.create("http://localhost:2700"), "Portal", Locale.US);

        TurSNSiteSearchResultsBean emptyResults = new TurSNSiteSearchResultsBean();
        emptyResults.setDocument(List.of());

        TurSNSiteSearchResultsBean nonEmpty = new TurSNSiteSearchResultsBean();
        nonEmpty.setDocument(List.of(TurSNSiteSearchDocumentBean.builder().fields(Map.of("title", "ok")).build()));

        assertThat(invokeHasSearchResults(server, null)).isFalse();
        assertThat(invokeHasSearchResults(server, emptyResults)).isFalse();
        assertThat(invokeHasSearchResults(server, nonEmpty)).isTrue();
    }

    private static QueryTurSNResponse invokeCreateTuringResponse(TurSNServer server, TurSNSiteSearchBean bean)
            throws Exception {
        Method method = TurSNServer.class.getDeclaredMethod("createTuringResponse", TurSNSiteSearchBean.class);
        method.setAccessible(true);
        return (QueryTurSNResponse) method.invoke(server, bean);
    }

    private static boolean invokeHasSearchResults(TurSNServer server, TurSNSiteSearchResultsBean bean)
            throws Exception {
        Method method = TurSNServer.class.getDeclaredMethod("hasSearchResults", TurSNSiteSearchResultsBean.class);
        method.setAccessible(true);
        return (boolean) method.invoke(server, bean);
    }
}
