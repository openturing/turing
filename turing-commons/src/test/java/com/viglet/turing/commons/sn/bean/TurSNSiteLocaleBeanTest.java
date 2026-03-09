package com.viglet.turing.commons.sn.bean;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;

class TurSNSiteLocaleBeanTest {

    @Test
    void shouldStoreLocaleAndLink() {
        TurSNSiteLocaleBean bean = new TurSNSiteLocaleBean();
        bean.setLocale(Locale.CANADA_FRENCH);
        bean.setLink("/search?_setlocale=fr-CA");

        assertThat(bean.getLocale()).isEqualTo(Locale.CANADA_FRENCH);
        assertThat(bean.getLink()).isEqualTo("/search?_setlocale=fr-CA");
    }
}
