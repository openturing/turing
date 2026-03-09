package com.viglet.turing.client.sn.didyoumean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.sn.bean.spellcheck.TurSNSiteSpellCheckBean;
import com.viglet.turing.commons.sn.bean.spellcheck.TurSNSiteSpellCheckText;

class TurSNDidYouMeanTest {

    @Test
    void defaultConstructorShouldCreateNestedObjects() {
        TurSNDidYouMean didYouMean = new TurSNDidYouMean();

        assertThat(didYouMean.getOriginal()).isNotNull();
        assertThat(didYouMean.getCorrected()).isNotNull();
        assertThat(didYouMean.isCorrectedText()).isFalse();
    }

    @Test
    void shouldMapFromSpellCheckBean() {
        TurSNSiteSpellCheckText original = mock(TurSNSiteSpellCheckText.class);
        TurSNSiteSpellCheckText corrected = mock(TurSNSiteSpellCheckText.class);
        when(original.getText()).thenReturn("machne");
        when(original.getLink()).thenReturn("/q=machne");
        when(corrected.getText()).thenReturn("machine");
        when(corrected.getLink()).thenReturn("/q=machine");

        TurSNSiteSpellCheckBean spellCheckBean = new TurSNSiteSpellCheckBean();
        spellCheckBean.setCorrectedText(true);
        spellCheckBean.setOriginal(original);
        spellCheckBean.setCorrected(corrected);

        TurSNDidYouMean didYouMean = new TurSNDidYouMean(spellCheckBean);

        assertThat(didYouMean.isCorrectedText()).isTrue();
        assertThat(didYouMean.getOriginal().getText()).isEqualTo("machne");
        assertThat(didYouMean.getCorrected().getText()).isEqualTo("machine");
    }
}
