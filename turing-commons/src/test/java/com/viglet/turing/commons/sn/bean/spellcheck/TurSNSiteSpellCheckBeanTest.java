package com.viglet.turing.commons.sn.bean.spellcheck;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;

class TurSNSiteSpellCheckBeanTest {

    @Test
    void shouldBuildFromContextAndSpellCheckResult() {
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ("machne");
        TurSEParameters seParameters = new TurSEParameters(searchParams);
        TurSNSiteSearchContext context = new TurSNSiteSearchContext(
                "Sample",
                new TurSNConfig(),
                seParameters,
                Locale.US,
                URI.create("http://localhost/search?q=machne"));

        TurSESpellCheckResult result = new TurSESpellCheckResult(true, "machine");
        result.setUsingCorrected(true);

        TurSNSiteSpellCheckBean bean = new TurSNSiteSpellCheckBean(context, result);

        assertThat(bean.isCorrectedText()).isTrue();
        assertThat(bean.isUsingCorrectedText()).isTrue();
        assertThat(bean.getOriginal().getText()).isEqualTo("machne");
        assertThat(bean.getCorrected().getText()).isEqualTo("machine");
    }
}
