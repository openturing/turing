package com.viglet.turing.commons.sn.bean.spellcheck;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;

class TurSNSiteSpellCheckTextTest {

    @Test
    void shouldCreateLinkWithQueryAndOriginalFlagWhenOriginal() {
        URI uri = URI.create("http://localhost/search?q=old");

        TurSNSiteSpellCheckText text = new TurSNSiteSpellCheckText(uri, "new text", true);

        assertThat(text.getText()).isEqualTo("new text");
        assertThat(text.getLink()).contains("q=new%20text");
        assertThat(text.getLink()).contains("nfpr=1");
    }

    @Test
    void shouldCreateLinkWithoutOriginalFlagWhenNotOriginal() {
        URI uri = URI.create("http://localhost/search?q=old");

        TurSNSiteSpellCheckText text = new TurSNSiteSpellCheckText(uri, "fixed", false);

        assertThat(text.getLink()).contains("q=fixed");
        assertThat(text.getLink()).doesNotContain("nfpr=1");
    }
}
