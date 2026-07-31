/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.transcription;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.repository.kb.TurThesaurusTermRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * T847 / §LXIII.2 — sources the transcription {@code keywords} hint from a
 * vocabulary Turing already curates: the Thesaurus (Block AQ). The same terms
 * the search engine expands are handed to the ASR, which is what stops product
 * names, model ids and jargon from being transcribed phonetically.
 *
 * <p><b>Opt-in and bounded</b> by design ({@code turing.transcription.keywords-from-thesaurus},
 * default {@code false}): only enabled preferred-term labels, broadest
 * descriptors first, capped at {@link TurTranscriptionHints#MAX_KEYWORDS}. A
 * keyword list is a hint, not a dictionary dump — and an install with no
 * Thesaurus simply gets an empty list, never an error.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurTranscriptionKeywordSource {

    private final TurThesaurusTermRepository thesaurusTermRepository;

    public TurTranscriptionKeywordSource(TurThesaurusTermRepository thesaurusTermRepository) {
        this.thesaurusTermRepository = thesaurusTermRepository;
    }

    /**
     * Up to {@code limit} Thesaurus terms, broadest first. Fail-open: any read
     * problem yields an empty list so transcription still runs — a missing hint
     * costs accuracy, never the transcript.
     */
    @Transactional(readOnly = true)
    public List<String> keywords(int limit) {
        int capped = Math.clamp(limit, 0, TurTranscriptionHints.MAX_KEYWORDS);
        if (capped == 0) {
            return List.of();
        }
        try {
            return thesaurusTermRepository.findEnabledLabelsBroadestFirst(
                    PageRequest.of(0, capped));
        } catch (RuntimeException e) {
            log.debug("[Transcription] could not read Thesaurus keywords: {}", e.getMessage());
            return List.of();
        }
    }
}
