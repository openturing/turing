package com.viglet.turing.commons.sn.bean;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.sn.pagination.TurSNPaginationType;

class TurSNSiteSearchPaginationBeanTest {

    @Test
    void shouldStorePaginationFields() {
        TurSNSiteSearchPaginationBean bean = new TurSNSiteSearchPaginationBean();
        bean.setType(TurSNPaginationType.NEXT);
        bean.setText("Next");
        bean.setHref("/search?p=2");
        bean.setPage(2);

        assertThat(bean.getType()).isEqualTo(TurSNPaginationType.NEXT);
        assertThat(bean.getText()).isEqualTo("Next");
        assertThat(bean.getHref()).isEqualTo("/search?p=2");
        assertThat(bean.getPage()).isEqualTo(2);
    }
}
