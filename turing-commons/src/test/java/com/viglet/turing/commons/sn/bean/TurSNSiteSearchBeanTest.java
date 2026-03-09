package com.viglet.turing.commons.sn.bean;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class TurSNSiteSearchBeanTest {

    @Test
    void shouldStoreAllSearchSections() {
        TurSNSiteSearchBean bean = new TurSNSiteSearchBean();
        TurSNSiteSearchPaginationBean paginationBean = new TurSNSiteSearchPaginationBean();
        TurSNSiteSearchQueryContextBean queryContext = new TurSNSiteSearchQueryContextBean();
        TurSNSiteSearchResultsBean results = new TurSNSiteSearchResultsBean();
        TurSNSiteSearchGroupBean group = new TurSNSiteSearchGroupBean();
        TurSNSiteSearchWidgetBean widget = new TurSNSiteSearchWidgetBean();

        bean.setPagination(List.of(paginationBean));
        bean.setQueryContext(queryContext);
        bean.setResults(results);
        bean.setGroups(Collections.singletonList(group));
        bean.setWidget(widget);

        assertThat(bean.getPagination()).containsExactly(paginationBean);
        assertThat(bean.getQueryContext()).isSameAs(queryContext);
        assertThat(bean.getResults()).isSameAs(results);
        assertThat(bean.getGroups()).containsExactly(group);
        assertThat(bean.getWidget()).isSameAs(widget);
    }
}
