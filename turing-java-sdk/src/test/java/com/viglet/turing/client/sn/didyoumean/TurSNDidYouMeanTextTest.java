package com.viglet.turing.client.sn.didyoumean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.sn.bean.spellcheck.TurSNSiteSpellCheckText;

class TurSNDidYouMeanTextTest {

    @Test
    void shouldMapFromSpellCheckTextBean() {
        TurSNSiteSpellCheckText source = mock(TurSNSiteSpellCheckText.class);
        when(source.getText()).thenReturn("corrigido");
        when(source.getLink()).thenReturn("http://localhost?q=corrigido");

        TurSNDidYouMeanText text = new TurSNDidYouMeanText(source);

        assertThat(text.getText()).isEqualTo("corrigido");
        assertThat(text.getLink()).isEqualTo("http://localhost?q=corrigido");
    }

    @Test
    void shouldAllowManualSetterUsage() {
        TurSNDidYouMeanText text = new TurSNDidYouMeanText();
        text.setText("original");
        text.setLink("http://localhost?q=original");

        assertThat(text.getText()).isEqualTo("original");
        assertThat(text.getLink()).isEqualTo("http://localhost?q=original");
    }
}
