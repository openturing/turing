/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * §VII.4 / T316 — unit coverage for the three deterministic scaffold helpers
 * backing the platform skills. Pure text transforms, no Spring context.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurScaffoldToolServiceTest {

    private final TurScaffoldToolService tool = new TurScaffoldToolService();

    @Test
    void scaffoldMarkdownUsesProvidedBeatsUnderTheTitle() {
        String md = tool.scaffoldMarkdown("Pix agendado", "Hook, Conceito, Call to Action");
        assertThat(md).startsWith("# Pix agendado")
                .contains("## Hook").contains("## Conceito").contains("## Call to Action");
    }

    @Test
    void scaffoldMarkdownFallsBackToDefaultBeatsAndTitle() {
        String md = tool.scaffoldMarkdown(null, "  ");
        assertThat(md).startsWith("# Untitled")
                .contains("## Hook").contains("## Development").contains("## Call to Action");
    }

    @Test
    void pickQuotePrefersLongestSentenceContainingTheTerm() {
        String text = "Pix is fast. The new Pix agendado lets you schedule a payment so you never miss a bill again. Short one.";
        String quote = tool.scaffoldPickQuote(text, "agendado", 0);
        assertThat(quote).contains("Pix agendado").contains("schedule a payment");
    }

    @Test
    void pickQuoteFallsBackToLongestSentenceWhenTermAbsent() {
        String text = "Alpha beta. This is clearly the longest sentence in the whole text body here. Tiny.";
        String quote = tool.scaffoldPickQuote(text, "nonexistent", 0);
        assertThat(quote).isEqualTo("This is clearly the longest sentence in the whole text body here.");
    }

    @Test
    void pickQuoteReturnsEmptyForBlankText() {
        assertThat(tool.scaffoldPickQuote("  ", "x", 0)).isEmpty();
    }

    @Test
    void buildMindMapRootsOnTheTermAndDropsStopWordsAndTheTerm() {
        String text = "Search search search relevance relevance ranking the the the of of pix pix";
        String mm = tool.scaffoldBuildMindMap(text, "pix");
        assertThat(mm).startsWith("mindmap\n  root((Pix))")
                .contains("Search").contains("Relevance")
                // term itself and stop-words must not appear as branches
                .doesNotContain("    Pix").doesNotContain("    The").doesNotContain("    Of");
    }
}
