package com.viglet.turing.client.sn.facet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.sn.bean.TurSNSiteSearchFacetItemBean;

class TurSNFacetFieldValueListTest {

    @Test
    void shouldMapFacetItemsAndExposeIterableList() {
        TurSNSiteSearchFacetItemBean item1 = new TurSNSiteSearchFacetItemBean();
        item1.setLabel("A");
        item1.setLink("/search?fq=a");
        item1.setCount(1);

        TurSNSiteSearchFacetItemBean item2 = new TurSNSiteSearchFacetItemBean();
        item2.setLabel("B");
        item2.setLink("/search?fq=b");
        item2.setCount(2);

        TurSNFacetFieldValueList valueList = new TurSNFacetFieldValueList(List.of(item1, item2));

        assertThat(valueList.getTurSNFacetFieldValues()).hasSize(2);
        assertThat(valueList.getTurSNFacetFieldValues().getFirst().getLabel()).isEqualTo("A");
        assertThat(valueList.getTurSNFacetFieldValues().get(1).getCount()).isEqualTo(2);
        assertThat(valueList).hasSize(2);
    }
}
