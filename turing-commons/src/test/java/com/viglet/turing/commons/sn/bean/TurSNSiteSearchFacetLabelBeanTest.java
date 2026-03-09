package com.viglet.turing.commons.sn.bean;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurSNSiteSearchFacetLabelBeanTest {

    @Test
    void shouldStoreFacetLabelFields() {
        TurSNSiteSearchFacetLabelBean bean = new TurSNSiteSearchFacetLabelBean();
        bean.setLang("en");
        bean.setText("Category");

        assertThat(bean.getLang()).isEqualTo("en");
        assertThat(bean.getText()).isEqualTo("Category");
    }
}
