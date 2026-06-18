/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TurChatArtifactUrls#normalize(String)} — the
 * server-side backstop that strips the {@code sandbox:} virtual scheme from
 * code-interpreter artifact URLs so inline charts render with any markdown
 * client.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurChatArtifactUrlsTest {

    @Test
    void stripsSandboxSchemeFromImageMarkdown() {
        String in = """
                Here you go:

                ![Chart](sandbox:/api/v2/code-interpreter/c4d6dfc4/bar_chart_example.png)
                """;
        assertThat(TurChatArtifactUrls.normalize(in))
                .contains("![Chart](/api/v2/code-interpreter/c4d6dfc4/bar_chart_example.png)")
                .doesNotContain("sandbox:");
    }

    @Test
    void preservesSignedQueryString() {
        String in = "[Download r.pdf](sandbox:/api/v2/code-interpreter/s1/r.pdf?sig=abc&exp=9)";
        assertThat(TurChatArtifactUrls.normalize(in))
                .isEqualTo("[Download r.pdf](/api/v2/code-interpreter/s1/r.pdf?sig=abc&exp=9)");
    }

    @Test
    void collapsesDoubledSandboxPrefix() {
        assertThat(TurChatArtifactUrls.normalize("![x](sandbox:sandbox:/api/v2/x.png)"))
                .isEqualTo("![x](/api/v2/x.png)");
    }

    @Test
    void collapsesExtraSlashesAfterScheme() {
        assertThat(TurChatArtifactUrls.normalize("![x](sandbox://api/v2/x.png)"))
                .isEqualTo("![x](/api/v2/x.png)");
    }

    @Test
    void isCaseInsensitiveOnScheme() {
        assertThat(TurChatArtifactUrls.normalize("![x](SANDBOX:/api/v2/x.png)"))
                .isEqualTo("![x](/api/v2/x.png)");
    }

    @Test
    void rewritesEveryOccurrence() {
        String in = "![a](sandbox:/api/v2/a.png) and ![b](sandbox:/api/v2/b.png)";
        assertThat(TurChatArtifactUrls.normalize(in))
                .isEqualTo("![a](/api/v2/a.png) and ![b](/api/v2/b.png)");
    }

    @Test
    void leavesSandboxBeforeNonApiPathsUntouched() {
        // Only Turing API artifacts are rewritten; a stray sandbox: elsewhere
        // is not our concern and must not be mangled.
        String in = "see sandbox:/mnt/data/foo.txt";
        assertThat(TurChatArtifactUrls.normalize(in)).isEqualTo(in);
    }

    @Test
    void noOpsOnTextWithoutScheme() {
        String in = "A normal reply with a [link](/api/v2/code-interpreter/s/x.png).";
        assertThat(TurChatArtifactUrls.normalize(in)).isEqualTo(in);
    }

    @Test
    void handlesNullAndBlank() {
        assertThat(TurChatArtifactUrls.normalize(null)).isNull();
        assertThat(TurChatArtifactUrls.normalize("")).isEmpty();
    }
}
