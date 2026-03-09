package com.viglet.turing.client.sn.spotlight;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.sn.bean.TurSNSiteSpotlightDocumentBean;

class TurSNSpotlightDocumentTest {

    @Test
    void shouldMapAndExposeSpotlightDocumentFields() {
        TurSNSiteSpotlightDocumentBean bean = new TurSNSiteSpotlightDocumentBean();
        bean.setId("doc-1");
        bean.setPosition(4);
        bean.setTitle("Spotlight Title");
        bean.setType("news");
        bean.setReferenceId("ref-1");
        bean.setContent("Highlighted content");
        bean.setLink("http://localhost/doc-1");

        TurSNSpotlightDocument spotlightDocument = new TurSNSpotlightDocument(bean);

        assertThat(spotlightDocument.getId()).isEqualTo("doc-1");
        assertThat(spotlightDocument.getPosition()).isEqualTo(4);
        assertThat(spotlightDocument.getTitle()).isEqualTo("Spotlight Title");
        assertThat(spotlightDocument.getType()).isEqualTo("news");
        assertThat(spotlightDocument.getReferenceId()).isEqualTo("ref-1");
        assertThat(spotlightDocument.getContent()).isEqualTo("Highlighted content");
        assertThat(spotlightDocument.getLink()).isEqualTo("http://localhost/doc-1");

        spotlightDocument.setId("doc-2");
        spotlightDocument.setPosition(8);
        spotlightDocument.setTitle("Updated");
        spotlightDocument.setType("blog");
        spotlightDocument.setReferenceId("ref-2");
        spotlightDocument.setContent("Updated content");
        spotlightDocument.setLink("http://localhost/doc-2");

        assertThat(spotlightDocument.getId()).isEqualTo("doc-2");
        assertThat(spotlightDocument.getPosition()).isEqualTo(8);
        assertThat(spotlightDocument.getTitle()).isEqualTo("Updated");
        assertThat(spotlightDocument.getType()).isEqualTo("blog");
        assertThat(spotlightDocument.getReferenceId()).isEqualTo("ref-2");
        assertThat(spotlightDocument.getContent()).isEqualTo("Updated content");
        assertThat(spotlightDocument.getLink()).isEqualTo("http://localhost/doc-2");
    }
}
