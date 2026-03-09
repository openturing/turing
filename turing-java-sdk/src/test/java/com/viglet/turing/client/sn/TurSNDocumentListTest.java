package com.viglet.turing.client.sn;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.sn.bean.TurSNSiteSearchQueryContextBean;

class TurSNDocumentListTest {

    @Test
    void shouldExposeDocumentsAndQueryContextAndIterator() {
        TurSNDocumentList documentList = new TurSNDocumentList();
        TurSNDocument d1 = new TurSNDocument();
        TurSNDocument d2 = new TurSNDocument();
        TurSNSiteSearchQueryContextBean queryContext = new TurSNSiteSearchQueryContextBean();

        documentList.setTurSNDocuments(List.of(d1, d2));
        documentList.setQueryContext(queryContext);

        assertThat(documentList.getTurSNDocuments()).containsExactly(d1, d2);
        assertThat(documentList.getQueryContext()).isSameAs(queryContext);
        assertThat(documentList).containsExactly(d1, d2);
    }
}
