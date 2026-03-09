package com.viglet.turing.client.sn.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import com.viglet.turing.client.sn.HttpTurSNServer;
import com.viglet.turing.client.sn.TurSNDocument;
import com.viglet.turing.client.sn.TurSNDocumentList;
import com.viglet.turing.client.sn.TurSNGroup;
import com.viglet.turing.client.sn.TurSNGroupList;
import com.viglet.turing.client.sn.TurSNLocale;
import com.viglet.turing.client.sn.TurSNQuery;
import com.viglet.turing.client.sn.didyoumean.TurSNDidYouMean;
import com.viglet.turing.client.sn.didyoumean.TurSNDidYouMeanText;
import com.viglet.turing.client.sn.facet.TurSNFacetFieldList;
import com.viglet.turing.client.sn.pagination.TurSNPagination;
import com.viglet.turing.client.sn.response.QueryTurSNResponse;
import com.viglet.turing.client.sn.spotlight.TurSNSpotlightDocument;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchFacetBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchFacetItemBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchFacetLabelBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchPaginationBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSpotlightDocumentBean;
import com.viglet.turing.commons.sn.bean.spellcheck.TurSNSiteSpellCheckText;
import com.viglet.turing.commons.sn.pagination.TurSNPaginationType;

class TurSNClientSampleTest {

    @Test
    void shouldBuildQueryFromDefaultAndCustomArgs() throws Exception {
        HttpTurSNServer turSNServer = mock(HttpTurSNServer.class);
        QueryTurSNResponse queryTurSNResponse = new QueryTurSNResponse();
        when(turSNServer.query(any(TurSNQuery.class))).thenReturn(queryTurSNResponse);

        QueryTurSNResponse defaultResponse = invoke("query", new Class<?>[] { String[].class, HttpTurSNServer.class },
                new String[] {}, turSNServer);
        QueryTurSNResponse explicitResponse = invoke("query", new Class<?>[] { String[].class, HttpTurSNServer.class },
                new String[] { "custom search" }, turSNServer);

        assertThat(defaultResponse).isSameAs(queryTurSNResponse);
        assertThat(explicitResponse).isSameAs(queryTurSNResponse);

        ArgumentCaptor<TurSNQuery> queryCaptor = ArgumentCaptor.forClass(TurSNQuery.class);
        verify(turSNServer, times(2)).query(queryCaptor.capture());

        assertThat(queryCaptor.getAllValues().getFirst().getQuery()).isEqualTo("tast");
        assertThat(queryCaptor.getAllValues().getLast().getQuery()).isEqualTo("custom search");
    }

    @Test
    void shouldExecuteSampleHelpersWithoutExternalHttpCalls() throws Exception {
        HttpTurSNServer turSNServer = mock(HttpTurSNServer.class);
        when(turSNServer.getLatestSearches(10)).thenReturn(List.of("first", "second"));
        when(turSNServer.autoComplete(any())).thenReturn(List.of("viglet", "vigor"));

        QueryTurSNResponse queryResponse = new QueryTurSNResponse();
        TurSNGroupList turSNGroupList = new TurSNGroupList();
        turSNGroupList.setTurSNGroups(new ArrayList<>());
        queryResponse.setGroupResponse(turSNGroupList);
        when(turSNServer.query(any(TurSNQuery.class))).thenReturn(queryResponse);

        invoke("latestSearches", new Class<?>[] { HttpTurSNServer.class }, turSNServer);
        invoke("autoComplete", new Class<?>[] { HttpTurSNServer.class }, turSNServer);
        invoke("groupBy", new Class<?>[] { String[].class, HttpTurSNServer.class }, new String[] {}, turSNServer);

        QueryTurSNResponse helperResponse = new QueryTurSNResponse();
        helperResponse.setPagination(new TurSNPagination(new ArrayList<>()));
        helperResponse.setFacetFields(new TurSNFacetFieldList(null, null));
        helperResponse.setSpotlightDocuments(new ArrayList<>());

        TurSNDidYouMean turSNDidYouMean = new TurSNDidYouMean();
        turSNDidYouMean.setCorrectedText(true);
        TurSNDidYouMeanText original = new TurSNDidYouMeanText();
        original.setText("orig");
        original.setLink("/orig");
        TurSNDidYouMeanText corrected = new TurSNDidYouMeanText();
        corrected.setText("corr");
        corrected.setLink("/corr");
        turSNDidYouMean.setOriginal(original);
        turSNDidYouMean.setCorrected(corrected);
        helperResponse.setDidYouMean(turSNDidYouMean);

        invoke("pagination", new Class<?>[] { TurSNPagination.class }, helperResponse.getPagination());
        invoke("facet", new Class<?>[] { QueryTurSNResponse.class }, helperResponse);
        invoke("didYouMean", new Class<?>[] { QueryTurSNResponse.class }, helperResponse);
        invoke("spolight", new Class<?>[] { QueryTurSNResponse.class }, helperResponse);
        invoke("showKeyValue", new Class<?>[] { Map.Entry.class }, Map.entry("q", List.of("value")));

        verify(turSNServer, times(2)).getLatestSearches(10);
        verify(turSNServer, times(1)).query(any(TurSNQuery.class));
        verify(turSNServer).autoComplete(any());
    }

    @Test
    void shouldExecuteMainFlowWithMockedServers() {
        QueryTurSNResponse richResponse = createRichResponse();
        TurSNLocale localeBean = new TurSNLocale();
        localeBean.setLocale("en-US");
        localeBean.setLink("/en-US");

        try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                (mock, context) -> {
                    when(mock.getLocales()).thenReturn(List.of(localeBean));
                    when(mock.autoComplete(any())).thenReturn(List.of("viglet"));
                    when(mock.getLatestSearches(10)).thenReturn(List.of("search term"));
                    when(mock.query(any(TurSNQuery.class))).thenReturn(richResponse);
                })) {
            TurSNClientSample.main(new String[] { "my-query" });

            assertThat(mocked.constructed()).hasSize(2);
            verify(mocked.constructed().getFirst(), atLeastOnce()).getLocales();
            verify(mocked.constructed().get(1), atLeastOnce()).autoComplete(any());
            verify(mocked.constructed().get(1), times(2)).query(any(TurSNQuery.class));
            verify(mocked.constructed().get(1), times(2)).getLatestSearches(10);
        }
    }

    private static QueryTurSNResponse createRichResponse() {
        QueryTurSNResponse response = new QueryTurSNResponse();

        TurSNSiteSearchDocumentBean documentBean = TurSNSiteSearchDocumentBean.builder()
                .fields(Map.of("title", "Sample Title"))
                .build();
        TurSNDocument document = new TurSNDocument();
        document.setContent(documentBean);
        TurSNDocumentList documentList = new TurSNDocumentList();
        documentList.setTurSNDocuments(List.of(document));

        TurSNGroup group = new TurSNGroup();
        group.setName("type");
        group.setResults(documentList);

        TurSNGroupList groupList = new TurSNGroupList();
        groupList.setTurSNGroups(List.of(group));
        response.setGroupResponse(groupList);

        TurSNSiteSearchPaginationBean paginationBean = new TurSNSiteSearchPaginationBean();
        paginationBean.setPage(1);
        paginationBean.setType(TurSNPaginationType.CURRENT);
        paginationBean.setHref("http://localhost:2700/search?p=1");
        paginationBean.setText("1");
        response.setPagination(new TurSNPagination(List.of(paginationBean)));

        TurSNSiteSearchFacetLabelBean facetLabel = new TurSNSiteSearchFacetLabelBean();
        facetLabel.setText("Type");

        TurSNSiteSearchFacetItemBean facetItem = new TurSNSiteSearchFacetItemBean();
        facetItem.setCount(1);
        facetItem.setLabel("Page");
        facetItem.setLink("http://localhost:2700/search?fq=type:Page");
        facetItem.setFilterQuery("type:Page");

        TurSNSiteSearchFacetBean facetBean = new TurSNSiteSearchFacetBean();
        facetBean.setLabel(facetLabel);
        facetBean.setName("type");
        facetBean.setDescription("Type field");
        facetBean.setType(TurSEFieldType.STRING);
        facetBean.setFacets(List.of(facetItem));

        response.setFacetFields(new TurSNFacetFieldList(List.of(facetBean), facetBean));

        TurSNDidYouMean didYouMean = new TurSNDidYouMean();
        didYouMean.setCorrectedText(true);
        didYouMean.setOriginal(
                new TurSNDidYouMeanText(new TurSNSiteSpellCheckText(URI.create("http://localhost"), "orig", true)));
        didYouMean.setCorrected(
                new TurSNDidYouMeanText(new TurSNSiteSpellCheckText(URI.create("http://localhost"), "corr", false)));
        response.setDidYouMean(didYouMean);

        TurSNSiteSpotlightDocumentBean spotlightBean = new TurSNSiteSpotlightDocumentBean();
        spotlightBean.setId("1");
        spotlightBean.setPosition(1);
        spotlightBean.setTitle("Spotlight");
        spotlightBean.setType("Page");
        spotlightBean.setReferenceId("ref");
        spotlightBean.setContent("content");
        spotlightBean.setLink("http://localhost/doc");
        response.setSpotlightDocuments(List.of(new TurSNSpotlightDocument(spotlightBean)));

        response.setResults(documentList);
        return response;
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = TurSNClientSample.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return (T) method.invoke(null, args);
    }
}