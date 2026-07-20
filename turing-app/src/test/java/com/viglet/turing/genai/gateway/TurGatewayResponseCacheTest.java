/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.gateway.TurOpenAiWire.WireMessage;
import com.viglet.turing.properties.TurGatewayProperty;

/**
 * T743 / §XLIX — unit coverage for the opt-in gateway response cache and the
 * header-driven options parsing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurGatewayResponseCacheTest {

    private TurGatewayResponseCache cache() {
        return new TurGatewayResponseCache(new TurGatewayProperty());
    }

    private List<WireMessage> msgs(String text) {
        return List.of(new WireMessage("user", text));
    }

    @Test
    void putThenGetReturnsCachedContent() {
        TurGatewayResponseCache c = cache();
        String key = c.keyFor("gpt-4o", msgs("hello"));
        assertThat(c.get(key)).isNull();
        c.put(key, "world");
        assertThat(c.get(key)).isEqualTo("world");
    }

    @Test
    void keyIsStableForSamePromptAndDiffersOtherwise() {
        TurGatewayResponseCache c = cache();
        assertThat(c.keyFor("gpt-4o", msgs("a"))).isEqualTo(c.keyFor("gpt-4o", msgs("a")));
        assertThat(c.keyFor("gpt-4o", msgs("a"))).isNotEqualTo(c.keyFor("gpt-4o", msgs("b")));
        assertThat(c.keyFor("gpt-4o", msgs("a"))).isNotEqualTo(c.keyFor("claude-3", msgs("a")));
    }

    @Test
    void emptyContentIsNotCached() {
        TurGatewayResponseCache c = cache();
        String key = c.keyFor("m", msgs("x"));
        c.put(key, "");
        assertThat(c.get(key)).isNull();
    }

    @Test
    void optionsParseFromHeaders() {
        assertThat(TurGatewayOptions.from("semantic", "strict", null, null).semanticCache()).isTrue();
        assertThat(TurGatewayOptions.from("semantic", "strict", null, null).guardrails()).isTrue();
        assertThat(TurGatewayOptions.from(null, null, null, null)).isEqualTo(TurGatewayOptions.none());
        assertThat(TurGatewayOptions.from("SEMANTIC", null, null, null).semanticCache()).isTrue();
        assertThat(TurGatewayOptions.from(null, "lax", null, null).guardrails()).isFalse();
        assertThat(TurGatewayOptions.from(null, null, "wknd", null).ragSite()).isEqualTo("wknd");
        assertThat(TurGatewayOptions.from(null, null, "  ", null).ragSite()).isNull();
    }

    @Test
    void optionsParseNativeToolsHeader() {
        assertThat(TurGatewayOptions.from(null, null, null, "web_search, code_execution").nativeTools())
                .containsExactlyInAnyOrder("web-search", "code-exec");
        assertThat(TurGatewayOptions.from(null, null, null, "web-fetch").nativeTools())
                .containsExactly("web-fetch");
        assertThat(TurGatewayOptions.none().nativeTools()).isEmpty();
    }
}
