package com.viglet.turing.client.sn.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.viglet.turing.client.sn.TurSNDocumentList;
import com.viglet.turing.client.sn.TurSNGroupList;
import com.viglet.turing.client.sn.didyoumean.TurSNDidYouMean;
import com.viglet.turing.client.sn.facet.TurSNFacetFieldList;
import com.viglet.turing.client.sn.pagination.TurSNPagination;
import com.viglet.turing.client.sn.spotlight.TurSNSpotlightDocument;

class QueryTurSNResponseTest {

    @Test
    void shouldStoreAndExposeAllResponseSections() {
        QueryTurSNResponse response = new QueryTurSNResponse();
        TurSNDocumentList results = new TurSNDocumentList();
        TurSNGroupList groups = new TurSNGroupList();
        TurSNPagination pagination = new TurSNPagination(Collections.emptyList());
        TurSNDidYouMean didYouMean = new TurSNDidYouMean();
        TurSNFacetFieldList facetFields = new TurSNFacetFieldList(Collections.emptyList(), null);

        response.setResults(results);
        response.setGroupResponse(groups);
        response.setPagination(pagination);
        response.setDidYouMean(didYouMean);
        response.setFacetFields(facetFields);
        response.setSpotlightDocuments(Collections.<TurSNSpotlightDocument>emptyList());

        assertThat(response.getResults()).isSameAs(results);
        assertThat(response.getGroupResponse()).isSameAs(groups);
        assertThat(response.getPagination()).isSameAs(pagination);
        assertThat(response.getDidYouMean()).isSameAs(didYouMean);
        assertThat(response.getFacetFields()).isSameAs(facetFields);
        assertThat(response.getSpotlightDocuments()).isEmpty();
    }
}
