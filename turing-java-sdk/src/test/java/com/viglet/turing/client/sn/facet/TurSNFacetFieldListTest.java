package com.viglet.turing.client.sn.facet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchFacetBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchFacetItemBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchFacetLabelBean;

class TurSNFacetFieldListTest {

    @Test
    void shouldMapFacetFieldsAndRemovedFacetInformation() {
        TurSNSiteSearchFacetLabelBean labelBean = new TurSNSiteSearchFacetLabelBean();
        labelBean.setText("Category");

        TurSNSiteSearchFacetItemBean facetItemBean = new TurSNSiteSearchFacetItemBean();
        facetItemBean.setLabel("News");
        facetItemBean.setLink("/search?fq=cat:news");
        facetItemBean.setCount(5);

        TurSNSiteSearchFacetBean facetBean = new TurSNSiteSearchFacetBean();
        facetBean.setLabel(labelBean);
        facetBean.setName("category");
        facetBean.setDescription("Category filter");
        facetBean.setType(TurSEFieldType.TEXT);
        facetBean.setMultiValued(true);
        facetBean.setFacets(List.of(facetItemBean));

        TurSNFacetFieldList fieldList = new TurSNFacetFieldList(List.of(facetBean), facetBean);

        assertThat(fieldList.getFields()).hasSize(1);
        TurSNFacetField mappedField = fieldList.getFields().getFirst();
        assertThat(mappedField.getLabel()).isEqualTo("Category");
        assertThat(mappedField.getName()).isEqualTo("category");
        assertThat(mappedField.getDescription()).isEqualTo("Category filter");
        assertThat(mappedField.isMultiValued()).isTrue();
        assertThat(mappedField.getType()).isEqualTo(TurSEFieldType.TEXT);
        assertThat(mappedField.getValues().getTurSNFacetFieldValues()).hasSize(1);

        assertThat(fieldList.getFacetWithRemovedValues()).isPresent();
        assertThat(fieldList.getFacetWithRemovedValues().orElseThrow().getLabel()).isEqualTo("Category");
        assertThat(fieldList).hasSize(1);
    }

    @Test
    void shouldHandleNullFacetInputs() {
        TurSNFacetFieldList fieldList = new TurSNFacetFieldList(null, null);

        assertThat(fieldList.getFields()).isEmpty();
        assertThat(fieldList.getFacetWithRemovedValues()).isEmpty();
    }
}
