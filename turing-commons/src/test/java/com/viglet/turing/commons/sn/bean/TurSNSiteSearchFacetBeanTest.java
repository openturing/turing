package com.viglet.turing.commons.sn.bean;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.field.TurSEFieldType;

class TurSNSiteSearchFacetBeanTest {

    @Test
    void shouldStoreFacetMetadataAndValues() {
        TurSNSiteSearchFacetBean bean = new TurSNSiteSearchFacetBean();
        TurSNSiteSearchFacetItemBean item = new TurSNSiteSearchFacetItemBean();
        TurSNSiteSearchFacetLabelBean label = new TurSNSiteSearchFacetLabelBean();

        item.setLabel("Article");
        label.setLang("en");
        label.setText("Type");

        bean.setFacets(List.of(item));
        bean.setLabel(label);
        bean.setName("type");
        bean.setDescription("Type filter");
        bean.setType(TurSEFieldType.STRING);
        bean.setMultiValued(true);
        bean.setCleanUpLink("/search?cleanup=true");
        bean.setSelectedFilterQueries(List.of("type:Article"));

        assertThat(bean.getFacets()).containsExactly(item);
        assertThat(bean.getLabel()).isSameAs(label);
        assertThat(bean.getName()).isEqualTo("type");
        assertThat(bean.getDescription()).isEqualTo("Type filter");
        assertThat(bean.getType()).isEqualTo(TurSEFieldType.STRING);
        assertThat(bean.isMultiValued()).isTrue();
        assertThat(bean.getSelectedFilterQueries()).containsExactly("type:Article");
    }
}
