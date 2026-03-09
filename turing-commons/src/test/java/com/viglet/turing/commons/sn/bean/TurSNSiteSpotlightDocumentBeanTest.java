package com.viglet.turing.commons.sn.bean;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurSNSiteSpotlightDocumentBeanTest {

    @Test
    void shouldStoreSpotlightFields() {
        TurSNSiteSpotlightDocumentBean bean = new TurSNSiteSpotlightDocumentBean();
        bean.setId("doc-1");
        bean.setPosition(1);
        bean.setTitle("Title");
        bean.setType("news");
        bean.setReferenceId("ref-1");
        bean.setContent("content");
        bean.setLink("http://localhost/doc-1");

        assertThat(bean.getId()).isEqualTo("doc-1");
        assertThat(bean.getPosition()).isEqualTo(1);
        assertThat(bean.getTitle()).isEqualTo("Title");
        assertThat(bean.getType()).isEqualTo("news");
        assertThat(bean.getReferenceId()).isEqualTo("ref-1");
        assertThat(bean.getContent()).isEqualTo("content");
        assertThat(bean.getLink()).isEqualTo("http://localhost/doc-1");
    }
}
