package com.viglet.turing.client.sn.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TurSNJobItemTest {

    @Test
    void shouldInitializeDefaultsForActionAndSite() {
        TurSNJobItem item = new TurSNJobItem(TurSNJobAction.CREATE, List.of("SampleSite"));

        assertThat(item.getTurSNJobAction()).isEqualTo(TurSNJobAction.CREATE);
        assertThat(item.getSiteNames()).containsExactly("SampleSite");
        assertThat(item.getLocale()).isEqualTo(Locale.ENGLISH);
        assertThat(item.getSpecs()).isNull();
    }

    @Test
    void shouldReadAttributeHelpersAndProviderAndId() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "doc-42");
        attributes.put("source_apps", "sdk-provider");
        attributes.put("views", 100);

        TurSNJobItem item = new TurSNJobItem(TurSNJobAction.CREATE, List.of("Sample"), Locale.US, attributes);

        assertThat(item.containsAttribute("views")).isTrue();
        assertThat(item.getAttribute("views")).isEqualTo(100);
        assertThat(item.getStringAttribute("views")).isEqualTo("100");
        assertThat(item.getStringAttribute("missing")).isNull();
        assertThat(item.getId()).isEqualTo("doc-42");
        assertThat(item.getProviderName()).isEqualTo("sdk-provider");
    }
}
