package com.viglet.turing.commons.sn.bean;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurSNSiteSearchDefaultFieldsBeanTest {

    @Test
    void shouldStoreDefaultFieldNames() {
        TurSNSiteSearchDefaultFieldsBean bean = new TurSNSiteSearchDefaultFieldsBean();
        bean.setTitle("title");
        bean.setDate("publication_date");
        bean.setDescription("abstract");
        bean.setText("text");
        bean.setImage("image");
        bean.setUrl("url");

        assertThat(bean.getTitle()).isEqualTo("title");
        assertThat(bean.getDate()).isEqualTo("publication_date");
        assertThat(bean.getDescription()).isEqualTo("abstract");
        assertThat(bean.getText()).isEqualTo("text");
        assertThat(bean.getImage()).isEqualTo("image");
        assertThat(bean.getUrl()).isEqualTo("url");
    }
}
