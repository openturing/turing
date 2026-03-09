package com.viglet.turing.commons.sn.bean;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TurSNSiteSearchResultsBeanTest {

    @Test
    void shouldStoreSearchDocuments() {
        TurSNSiteSearchResultsBean bean = new TurSNSiteSearchResultsBean();
        TurSNSiteSearchDocumentBean d1 = TurSNSiteSearchDocumentBean.builder().source("a").build();
        TurSNSiteSearchDocumentBean d2 = TurSNSiteSearchDocumentBean.builder().source("b").build();

        bean.setDocument(List.of(d1, d2));

        assertThat(bean.getDocument()).containsExactly(d1, d2);
    }
}
