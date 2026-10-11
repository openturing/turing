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

    private static TurSNSiteSearchContext context(String q) {
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ(q);
        return new TurSNSiteSearchContext(
                "Sample",
                new TurSNConfig(),
                new TurSEParameters(searchParams),
                Locale.US,
                URI.create("http://localhost/search?q=" + q));
    }

    @Test
    void noSuggestionLeavesCorrectedAbsent() {
        TurSNSiteSpellCheckBean bean = new TurSNSiteSpellCheckBean(context("home"), new TurSESpellCheckResult());

        // T1067 — no blank correction whose link would search for nothing.
        assertThat(bean.isCorrectedText()).isFalse();
        assertThat(bean.isUsingCorrectedText()).isFalse();
        assertThat(bean.getCorrected()).isNull();
        assertThat(bean.getOriginal().getText()).isEqualTo("home");
    }

    @Test
    void aBlankCorrectionCountsAsNone() {
        TurSESpellCheckResult result = new TurSESpellCheckResult(true, "  ");
        result.setUsingCorrected(true);

        TurSNSiteSpellCheckBean bean = new TurSNSiteSpellCheckBean(context("home"), result);

        assertThat(bean.isCorrectedText()).isFalse();
        assertThat(bean.isUsingCorrectedText()).isFalse();
        assertThat(bean.getCorrected()).isNull();
    }

    @Test
    void shouldBuildFromContextAndSpellCheckResult() {
        TurSNSiteSearchContext context = context("machne");

        TurSESpellCheckResult result = new TurSESpellCheckResult(true, "machine");
        result.setUsingCorrected(true);

        TurSNSiteSpellCheckBean bean = new TurSNSiteSpellCheckBean(context, result);

        assertThat(bean.isCorrectedText()).isTrue();
        assertThat(bean.isUsingCorrectedText()).isTrue();
        assertThat(bean.getOriginal().getText()).isEqualTo("machne");
        assertThat(bean.getCorrected().getText()).isEqualTo("machine");
    }
}
