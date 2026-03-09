package com.viglet.turing.commons.sn.bean;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurSNSiteSearchQueryContextBeanTest {

    @Test
    void shouldStoreQueryContextFields() {
        TurSNSiteSearchQueryContextBean bean = new TurSNSiteSearchQueryContextBean();
        TurSNSiteSearchQueryContextQueryBean query = new TurSNSiteSearchQueryContextQueryBean();
        TurSNSiteSearchDefaultFieldsBean defaults = new TurSNSiteSearchDefaultFieldsBean();

        bean.setCount(100);
        bean.setIndex("site-index");
        bean.setLimit(10);
        bean.setOffset(20);
        bean.setPage(3);
        bean.setPageCount(10);
        bean.setPageStart(21);
        bean.setPageEnd(30);
        bean.setResponseTime(42L);
        bean.setQuery(query);
        bean.setDefaultFields(defaults);
        bean.setFacetType("simple");
        bean.setFacetItemType("value");

        assertThat(bean.getCount()).isEqualTo(100);
        assertThat(bean.getIndex()).isEqualTo("site-index");
        assertThat(bean.getLimit()).isEqualTo(10);
        assertThat(bean.getOffset()).isEqualTo(20);
        assertThat(bean.getPage()).isEqualTo(3);
        assertThat(bean.getPageCount()).isEqualTo(10);
        assertThat(bean.getPageStart()).isEqualTo(21);
        assertThat(bean.getPageEnd()).isEqualTo(30);
        assertThat(bean.getResponseTime()).isEqualTo(42L);
        assertThat(bean.getQuery()).isSameAs(query);
        assertThat(bean.getDefaultFields()).isSameAs(defaults);
        assertThat(bean.getFacetType()).isEqualTo("simple");
        assertThat(bean.getFacetItemType()).isEqualTo("value");
    }
}
