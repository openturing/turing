/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.anthropic;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.anthropic.models.messages.CitationCharLocation;
import com.anthropic.models.messages.CitationPageLocation;
import com.anthropic.models.messages.CitationsConfigParam;
import com.anthropic.models.messages.CitationsSearchResultLocation;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.SearchResultBlockParam;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.TextCitation;
import com.viglet.turing.genai.citation.TurChatCitation;
import com.viglet.turing.genai.citation.TurCitationDocument;

import lombok.extern.slf4j.Slf4j;

/**
 * T152 / §X.7.a — translates Turing's RAG passages into Anthropic
 * {@code document} content blocks (citations enabled) and decodes the
 * per-sentence citations Claude returns back into {@link TurChatCitation}s.
 *
 * <p>Stateless and side-effect free, so it is trivially unit-testable against a
 * stubbed {@link Message}. The {@link TurAnthropicMessagesService} owns the
 * request/response loop; this collaborator owns only the citation-specific
 * encoding/decoding so that logic stays in one place when T153 swaps the
 * {@code document} block for the newer {@code search_result} block.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurAnthropicCitationSupport {

    private static final String LOCATION_CHAR = "char";
    private static final String LOCATION_PAGE = "page";
    private static final String LOCATION_SEARCH_RESULT = "search_result";
    private static final String DEFAULT_SOURCE = "source";
    private static final String DEFAULT_TITLE = "Source";

    /**
     * T153 / §X.7.b — wrap each retrieved passage in an Anthropic
     * {@code search_result} content block (citations enabled), the
     * encyclopedia-quality citation format for retrieval hits, instead of the
     * {@code document} block ({@link #documentBlocks}). Order is preserved so the
     * response's {@code searchResultIndex} resolves back onto
     * {@code documents.get(i)}. {@code source} carries the deep link (or the
     * sourceId when no URL), {@code title} the label, and the passage text is the
     * single text block of {@code content}; blank fields are defaulted so the
     * block stays valid and the index stays aligned.
     */
    public List<ContentBlockParam> searchResultBlocks(List<TurCitationDocument> documents) {
        List<ContentBlockParam> blocks = new ArrayList<>();
        if (documents == null) {
            return blocks;
        }
        for (TurCitationDocument doc : documents) {
            String text = doc.text() == null || doc.text().isBlank() ? " " : doc.text();
            String source = StringUtils.hasText(doc.url()) ? doc.url()
                    : (StringUtils.hasText(doc.sourceId()) ? doc.sourceId() : DEFAULT_SOURCE);
            String title = StringUtils.hasText(doc.title()) ? doc.title() : DEFAULT_TITLE;
            blocks.add(ContentBlockParam.ofSearchResult(SearchResultBlockParam.builder()
                    .source(source)
                    .title(title)
                    .addContent(TextBlockParam.builder().text(text).build())
                    .citations(CitationsConfigParam.builder().enabled(true).build())
                    .build()));
        }
        return blocks;
    }

    /**
     * Build one citations-enabled {@code document} block per passage, preserving
     * order so the response's {@code documentIndex} resolves back onto
     * {@code documents.get(i)}. A passage with blank text is skipped (Anthropic
     * rejects an empty document source) — callers should pass the same list to
     * {@link #extractCitations} so indices stay aligned, so this method does NOT
     * drop entries; blank text is replaced with a single space to keep the block
     * valid and the index stable.
     */
    public List<ContentBlockParam> documentBlocks(List<TurCitationDocument> documents) {
        List<ContentBlockParam> blocks = new ArrayList<>();
        if (documents == null) {
            return blocks;
        }
        for (TurCitationDocument doc : documents) {
            String text = doc.text() == null || doc.text().isBlank() ? " " : doc.text();
            DocumentBlockParam.Builder builder = DocumentBlockParam.builder()
                    .textSource(text)
                    .citations(CitationsConfigParam.builder().enabled(true).build());
            if (StringUtils.hasText(doc.title())) {
                builder.title(doc.title());
            }
            blocks.add(ContentBlockParam.ofDocument(builder.build()));
        }
        return blocks;
    }

    /**
     * Decode the per-sentence citations from every text block of the response,
     * resolving each {@code documentIndex} against the ordered {@code documents}
     * list for the sourceId / url. Citations whose index falls outside the list
     * (defensive — shouldn't happen) keep a null sourceId/url but are still
     * surfaced. Returns an empty list when the response carries no citations.
     */
    public List<TurChatCitation> extractCitations(Message response, List<TurCitationDocument> documents) {
        if (response == null) {
            return new ArrayList<>();
        }
        return extractFromContent(response.content(), documents);
    }

    /**
     * Decode the citations from a content-block list (split out from
     * {@link #extractCitations(Message, List)} so it is testable without
     * building a full {@link Message}).
     */
    List<TurChatCitation> extractFromContent(List<ContentBlock> content,
            List<TurCitationDocument> documents) {
        List<TurChatCitation> citations = new ArrayList<>();
        if (content == null) {
            return citations;
        }
        for (ContentBlock block : content) {
            if (!block.isText()) {
                continue;
            }
            block.asText().citations().ifPresent(list -> {
                for (TextCitation citation : list) {
                    TurChatCitation decoded = decode(citation, documents);
                    if (decoded != null) {
                        citations.add(decoded);
                    }
                }
            });
        }
        return citations;
    }

    private TurChatCitation decode(TextCitation citation, List<TurCitationDocument> documents) {
        if (citation.isCharLocation()) {
            CitationCharLocation loc = citation.asCharLocation();
            return build(documents, (int) loc.documentIndex(), loc.documentTitle().orElse(null),
                    loc.citedText(), (int) loc.startCharIndex(), (int) loc.endCharIndex(), LOCATION_CHAR);
        }
        if (citation.isPageLocation()) {
            CitationPageLocation loc = citation.asPageLocation();
            return build(documents, (int) loc.documentIndex(), loc.documentTitle().orElse(null),
                    loc.citedText(), (int) loc.startPageNumber(), (int) loc.endPageNumber(), LOCATION_PAGE);
        }
        if (citation.isSearchResultLocation()) {
            // T153 / §X.7.b — search_result blocks cite by searchResultIndex (which
            // block) + start/endBlockIndex (which text blocks within it).
            CitationsSearchResultLocation loc = citation.asSearchResultLocation();
            return build(documents, (int) loc.searchResultIndex(), loc.title().orElse(null),
                    loc.citedText(), (int) loc.startBlockIndex(), (int) loc.endBlockIndex(),
                    LOCATION_SEARCH_RESULT);
        }
        // content-block / web-search locations aren't produced by the blocks
        // Turing sends (document T152 / search_result T153) — ignore defensively.
        log.debug("[Native][Anthropic-Citations] ignoring unsupported citation location type");
        return null;
    }

    private TurChatCitation build(List<TurCitationDocument> documents, int documentIndex,
            String documentTitle, String citedText, int startIndex, int endIndex, String locationType) {
        TurCitationDocument source = documents != null && documentIndex >= 0
                && documentIndex < documents.size() ? documents.get(documentIndex) : null;
        String title = StringUtils.hasText(documentTitle) ? documentTitle
                : (source != null ? source.title() : null);
        return new TurChatCitation(documentIndex,
                source != null ? source.sourceId() : null,
                title,
                source != null ? source.url() : null,
                citedText,
                startIndex,
                endIndex,
                locationType);
    }
}
