package com.viglet.turing.commons.sn.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;

class TurSNSiteSearchContextTest {

    @Test
    void shouldCreateDefaultPostParamsWhenNullIsProvided() {
        TurSNSearchParams searchParams = new TurSNSearchParams();
        TurSEParameters seParameters = new TurSEParameters(searchParams);

        TurSNSiteSearchContext context = new TurSNSiteSearchContext(
                "site",
                new TurSNConfig(),
                seParameters,
                Locale.US,
                URI.create("http://localhost/search"),
                null);

        assertThat(context.getSiteName()).isEqualTo("site");
        assertThat(context.getLocale()).isEqualTo(Locale.US);
        assertThat(context.getTurSNSitePostParamsBean()).isNotNull();
        assertThat(context.toString()).contains("siteName='site'");
    }

    @Test
    void shouldKeepProvidedPostParams() {
        TurSNSitePostParamsBean post = new TurSNSitePostParamsBean();
        post.setUserId("u-1");

        TurSNSiteSearchContext context = new TurSNSiteSearchContext(
                "site",
                new TurSNConfig(),
                new TurSEParameters(new TurSNSearchParams()),
                Locale.CANADA,
                URI.create("http://localhost/search"),
                post);

        assertThat(context.getTurSNSitePostParamsBean()).isSameAs(post);
        assertThat(context.getTurSNSitePostParamsBean().getUserId()).isEqualTo("u-1");
    }
}
