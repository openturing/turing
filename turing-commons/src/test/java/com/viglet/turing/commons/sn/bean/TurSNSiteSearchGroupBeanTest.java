package com.viglet.turing.commons.sn.bean;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TurSNSiteSearchGroupBeanTest {

    @Test
    void shouldStoreGroupMetadataResultsAndPagination() {
        TurSNSiteSearchGroupBean bean = new TurSNSiteSearchGroupBean();
        TurSNSiteSearchResultsBean results = new TurSNSiteSearchResultsBean();
        TurSNSiteSearchPaginationBean page = new TurSNSiteSearchPaginationBean();

        bean.setName("news");
        bean.setCount(50);
        bean.setPage(2);
        bean.setPageCount(10);
        bean.setPageStart(6);
        bean.setPageEnd(10);
        bean.setLimit(5);
        bean.setResults(results);
        bean.setPagination(List.of(page));

        assertThat(bean.getName()).isEqualTo("news");
        assertThat(bean.getCount()).isEqualTo(50);
        assertThat(bean.getPage()).isEqualTo(2);
        assertThat(bean.getPageCount()).isEqualTo(10);
        assertThat(bean.getPageStart()).isEqualTo(6);
        assertThat(bean.getPageEnd()).isEqualTo(10);
        assertThat(bean.getLimit()).isEqualTo(5);
        assertThat(bean.getResults()).isSameAs(results);
        assertThat(bean.getPagination()).containsExactly(page);
    }
}
