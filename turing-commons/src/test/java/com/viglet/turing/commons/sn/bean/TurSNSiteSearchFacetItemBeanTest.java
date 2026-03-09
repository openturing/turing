package com.viglet.turing.commons.sn.bean;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurSNSiteSearchFacetItemBeanTest {

    @Test
    void shouldStoreFacetItemFields() {
        TurSNSiteSearchFacetItemBean bean = new TurSNSiteSearchFacetItemBean();
        bean.setCount(10);
        bean.setLink("/search?fq=type:Article");
        bean.setLabel("Article");
        bean.setFilterQuery("type:Article");
        bean.setSelected(true);

        assertThat(bean.getCount()).isEqualTo(10);
        assertThat(bean.getLink()).contains("fq=type:Article");
        assertThat(bean.getLabel()).isEqualTo("Article");
        assertThat(bean.getFilterQuery()).isEqualTo("type:Article");
        assertThat(bean.isSelected()).isTrue();
    }
}
