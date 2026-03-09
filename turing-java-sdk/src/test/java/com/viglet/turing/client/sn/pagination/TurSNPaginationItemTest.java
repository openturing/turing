package com.viglet.turing.client.sn.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.sn.bean.TurSNSiteSearchPaginationBean;
import com.viglet.turing.commons.sn.pagination.TurSNPaginationType;

class TurSNPaginationItemTest {

    @Test
    void shouldMapPaginationBeanData() {
        TurSNSiteSearchPaginationBean bean = new TurSNSiteSearchPaginationBean();
        bean.setType(TurSNPaginationType.NEXT);
        bean.setText("Next");
        bean.setHref("/search?page=2");
        bean.setPage(2);

        TurSNPaginationItem item = new TurSNPaginationItem(bean);

        assertThat(item.getType()).isEqualTo(TurSNPaginationType.NEXT);
        assertThat(item.getLabel()).isEqualTo("Next");
        assertThat(item.getApiURL()).contains("/search?page=2");
        assertThat(item.getPageNumber()).isEqualTo(2);
    }

    @Test
    void shouldAllowManualSetters() {
        TurSNPaginationItem item = new TurSNPaginationItem();
        item.setType(TurSNPaginationType.CURRENT);
        item.setLabel("1");
        item.setPageNumber(1);

        assertThat(item.getType()).isEqualTo(TurSNPaginationType.CURRENT);
        assertThat(item.getLabel()).isEqualTo("1");
        assertThat(item.getPageNumber()).isEqualTo(1);
    }
}
