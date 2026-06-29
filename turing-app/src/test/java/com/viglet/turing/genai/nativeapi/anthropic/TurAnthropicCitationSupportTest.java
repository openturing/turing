/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.anthropic.models.messages.CitationCharLocation;
import com.anthropic.models.messages.CitationPageLocation;
import com.anthropic.models.messages.CitationsSearchResultLocation;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.SearchResultBlockParam;
import com.anthropic.models.messages.TextBlock;
import com.viglet.turing.genai.citation.TurChatCitation;
import com.viglet.turing.genai.citation.TurCitationDocument;

/**
 * Unit tests for {@link TurAnthropicCitationSupport} (T152 / §X.7.a + T153 /
 * §X.7.b): document &amp; search_result block construction and per-sentence
 * citation extraction.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurAnthropicCitationSupportTest {

    private final TurAnthropicCitationSupport support = new TurAnthropicCitationSupport();

    private static final List<TurCitationDocument> DOCS = List.of(
            new TurCitationDocument("doc-a", "Doc A", "/sn/site/document/doc-a", "The sky is blue today."),
            new TurCitationDocument("doc-b", "Doc B", "/sn/site/document/doc-b", "Water boils at 100C."));

    @Test
    void documentBlocksEnableCitationsAndCarryTitleAndText() {
        List<ContentBlockParam> blocks = support.documentBlocks(DOCS);

        assertThat(blocks).hasSize(2);
        DocumentBlockParam first = blocks.get(0).document().orElseThrow();
        assertThat(first.citations().orElseThrow().enabled()).contains(true);
        assertThat(first.title()).contains("Doc A");
        assertThat(first.source().asText().data()).isEqualTo("The sky is blue today.");
    }

    @Test
    void documentBlocksKeepIndexStableForBlankText() {
        // A blank passage must still produce a block so the response's
        // documentIndex stays aligned with the source list.
        List<TurCitationDocument> withBlank = List.of(
                new TurCitationDocument("doc-a", "Doc A", null, "  "),
                new TurCitationDocument("doc-b", "Doc B", null, "real text"));

        List<ContentBlockParam> blocks = support.documentBlocks(withBlank);

        assertThat(blocks).hasSize(2);
        // Placeholder text keeps the block valid; the point is the index stays aligned.
        assertThat(blocks.get(0).document().orElseThrow().source().asText().data()).isNotEmpty();
    }

    @Test
    void documentBlocksToleratesNull() {
        assertThat(support.documentBlocks(null)).isEmpty();
    }

    @Test
    void documentBlocksEmitBase64PdfSourceForNativePdf() {
        // T176 / §X.12.b — a passage carrying a native PDF must become a base64
        // PDF document block (not the text source), with citations enabled so
        // Claude returns page-location citations.
        String base64 = java.util.Base64.getEncoder()
                .encodeToString("%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        List<TurCitationDocument> docs = List.of(
                new TurCitationDocument("doc-a", "Report", "/u", "tika fallback text")
                        .withNativePdf(new TurCitationDocument.NativePdf(base64, "application/pdf", "file_1")));

        DocumentBlockParam block = support.documentBlocks(docs).get(0).document().orElseThrow();

        assertThat(block.citations().orElseThrow().enabled()).contains(true);
        assertThat(block.title()).contains("Report");
        assertThat(block.source().isBase64()).isTrue();
        assertThat(block.source().asBase64().data()).isEqualTo(base64);
    }

    @Test
    void searchResultBlocksEnableCitationsAndCarrySourceTitleContent() {
        List<ContentBlockParam> blocks = support.searchResultBlocks(DOCS);

        assertThat(blocks).hasSize(2);
        SearchResultBlockParam first = blocks.get(0).searchResult().orElseThrow();
        assertThat(first.citations().orElseThrow().enabled()).contains(true);
        assertThat(first.title()).isEqualTo("Doc A");
        assertThat(first.source()).isEqualTo("/sn/site/document/doc-a");
        assertThat(first.content()).hasSize(1);
        assertThat(first.content().get(0).text()).isEqualTo("The sky is blue today.");
    }

    @Test
    void searchResultBlocksFallBackToSourceIdWhenNoUrl() {
        List<TurCitationDocument> noUrl = List.of(
                new TurCitationDocument("doc-a", "Doc A", null, "text"));
        SearchResultBlockParam block = support.searchResultBlocks(noUrl).get(0).searchResult().orElseThrow();
        assertThat(block.source()).isEqualTo("doc-a");
    }

    @Test
    void searchResultBlocksToleratesNull() {
        assertThat(support.searchResultBlocks(null)).isEmpty();
    }

    @Test
    void extractCitationsResolvesSearchResultLocationOntoSource() {
        ContentBlock block = ContentBlock.ofText(TextBlock.builder()
                .text("Water boils at 100C.")
                .addCitation(CitationsSearchResultLocation.builder()
                        .citedText("boils at 100C")
                        .searchResultIndex(1)
                        .source("/sn/site/document/doc-b")
                        .title("Doc B")
                        .startBlockIndex(0)
                        .endBlockIndex(1)
                        .build())
                .build());

        List<TurChatCitation> citations = support.extractFromContent(List.of(block), DOCS);

        assertThat(citations).hasSize(1);
        TurChatCitation c = citations.get(0);
        assertThat(c.documentIndex()).isEqualTo(1);
        assertThat(c.sourceId()).isEqualTo("doc-b");
        assertThat(c.url()).isEqualTo("/sn/site/document/doc-b");
        assertThat(c.citedText()).isEqualTo("boils at 100C");
        assertThat(c.startIndex()).isZero();
        assertThat(c.endIndex()).isEqualTo(1);
        assertThat(c.locationType()).isEqualTo("search_result");
    }

    @Test
    void extractCitationsResolvesCharLocationOntoSource() {
        ContentBlock block = ContentBlock.ofText(TextBlock.builder()
                .text("The sky is blue today.")
                .addCitation(CitationCharLocation.builder()
                        .citedText("The sky is blue")
                        .documentIndex(0)
                        .startCharIndex(0)
                        .endCharIndex(15)
                        .documentTitle("Doc A")
                        .fileId(Optional.empty())
                        .build())
                .build());

        List<TurChatCitation> citations = support.extractFromContent(List.of(block), DOCS);

        assertThat(citations).hasSize(1);
        TurChatCitation c = citations.get(0);
        assertThat(c.documentIndex()).isZero();
        assertThat(c.sourceId()).isEqualTo("doc-a");
        assertThat(c.url()).isEqualTo("/sn/site/document/doc-a");
        assertThat(c.citedText()).isEqualTo("The sky is blue");
        assertThat(c.startIndex()).isZero();
        assertThat(c.endIndex()).isEqualTo(15);
        assertThat(c.locationType()).isEqualTo("char");
    }

    @Test
    void extractCitationsResolvesPageLocationOntoSource() {
        ContentBlock block = ContentBlock.ofText(TextBlock.builder()
                .text("Water boils at 100C.")
                .addCitation(CitationPageLocation.builder()
                        .citedText("boils at 100C")
                        .documentIndex(1)
                        .startPageNumber(3)
                        .endPageNumber(3)
                        .documentTitle(Optional.empty())
                        .fileId(Optional.empty())
                        .build())
                .build());

        List<TurChatCitation> citations = support.extractFromContent(List.of(block), DOCS);

        assertThat(citations).hasSize(1);
        TurChatCitation c = citations.get(0);
        assertThat(c.sourceId()).isEqualTo("doc-b");
        assertThat(c.locationType()).isEqualTo("page");
        assertThat(c.startIndex()).isEqualTo(3);
        // Title falls back to the source title when Claude doesn't echo one.
        assertThat(c.documentTitle()).isEqualTo("Doc B");
    }

    @Test
    void extractCitationsEmptyWhenNoCitations() {
        ContentBlock block = ContentBlock.ofText(TextBlock.builder()
                .text("No citations here.")
                .citations(List.of())
                .build());
        assertThat(support.extractFromContent(List.of(block), DOCS)).isEmpty();
    }

    @Test
    void extractCitationsToleratesNull() {
        assertThat(support.extractCitations(null, DOCS)).isEmpty();
    }

    @Test
    void appendTextAndCitationsCapturesAnswerSpanOffsets() {
        // Two text blocks: the first uncited, the second cited. The cited block's
        // answer span must be [len(first), len(first)+len(second)).
        ContentBlock plain = ContentBlock.ofText(TextBlock.builder()
                .text("Here is the answer. ")
                .citations(List.of())
                .build());
        ContentBlock cited = ContentBlock.ofText(TextBlock.builder()
                .text("The sky is blue.")
                .addCitation(CitationsSearchResultLocation.builder()
                        .citedText("The sky is blue today.")
                        .searchResultIndex(0)
                        .source("/sn/site/document/doc-a")
                        .title("Doc A")
                        .startBlockIndex(0)
                        .endBlockIndex(1)
                        .build())
                .build());

        StringBuilder answer = new StringBuilder();
        List<TurChatCitation> sink = new java.util.ArrayList<>();
        support.appendTextAndCitations(List.of(plain, cited), answer, sink, DOCS);

        assertThat(answer.toString()).isEqualTo("Here is the answer. The sky is blue.");
        assertThat(sink).hasSize(1);
        TurChatCitation c = sink.get(0);
        assertThat(c.answerStart()).isEqualTo("Here is the answer. ".length());
        assertThat(c.answerEnd()).isEqualTo("Here is the answer. The sky is blue.".length());
        assertThat(c.sourceId()).isEqualTo("doc-a");
        assertThat(c.locationType()).isEqualTo("search_result");
    }
}
