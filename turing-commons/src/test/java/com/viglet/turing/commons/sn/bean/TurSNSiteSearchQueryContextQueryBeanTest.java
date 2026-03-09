package com.viglet.turing.commons.sn.bean;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;

class TurSNSiteSearchQueryContextQueryBeanTest {

    @Test
    void shouldStoreQueryStringSortAndLocale() {
        TurSNSiteSearchQueryContextQueryBean bean = new TurSNSiteSearchQueryContextQueryBean();
        bean.setQueryString("enterprise search");
        bean.setSort("relevance");
        bean.setLocale(Locale.US);

        assertThat(bean.getQueryString()).isEqualTo("enterprise search");
        assertThat(bean.getSort()).isEqualTo("relevance");
        assertThat(bean.getLocale()).isEqualTo(Locale.US);
    }
}
