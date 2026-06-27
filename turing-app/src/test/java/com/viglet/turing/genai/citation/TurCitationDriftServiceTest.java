/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.citation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.genai.TurSNGenAi;
import com.viglet.turing.genai.citation.TurCitationDriftService.DriftVerdict;
import com.viglet.turing.genai.citation.TurCitationDriftService.ResolvedChunk;
import com.viglet.turing.genai.citation.TurCitationDriftService.Status;
import com.viglet.turing.genai.tool.TurRagSearchToolService;
import com.viglet.turing.persistence.model.agent.TurChatCitationRecord;
import com.viglet.turing.persistence.repository.agent.TurChatCitationRecordRepository;
import com.viglet.turing.properties.TurConfigProperties;

import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for the §X.7.d / T155 citation drift engine — the pure verdict
 * policy, citation persistence (parse + dedup), modification-date parsing, and
 * the scan's flag-and-save behaviour.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurCitationDriftServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private TurChatCitationRecordRepository repository;
    @Mock
    private TurRagSearchToolService ragSearchToolService;

    private TurConfigProperties configProperties;
    private TurCitationDriftService service;

    @BeforeEach
    void setUp() {
        configProperties = new TurConfigProperties();
        configProperties.getGenai().getCitationDrift().setEnabled(true);
        service = new TurCitationDriftService(configProperties, repository, ragSearchToolService);
    }

    // ---- evaluate() verdict policy --------------------------------------

    @Test
    void evaluate_stale_whenSourceReindexedAfterAnswer() {
        Instant answer = Instant.parse("2026-01-01T00:00:00Z");
        Instant reindexed = answer.plus(2, ChronoUnit.DAYS);
        DriftVerdict verdict = service.evaluate("the quick brown fox",
                List.of(new ResolvedChunk("the quick brown fox", reindexed)), answer);
        assertThat(verdict.status()).isEqualTo(Status.STALE);
        assertThat(verdict.reason()).contains("re-indexed");
    }

    @Test
    void evaluate_stale_whenCitedTextNoLongerPresent() {
        Instant answer = Instant.parse("2026-01-01T00:00:00Z");
        DriftVerdict verdict = service.evaluate("the original sentence",
                List.of(new ResolvedChunk("a totally rewritten passage", null)), answer);
        assertThat(verdict.status()).isEqualTo(Status.STALE);
        assertThat(verdict.reason()).contains("no longer present");
    }

    @Test
    void evaluate_fresh_whenPresentAndNotReindexed() {
        Instant answer = Instant.parse("2026-01-01T00:00:00Z");
        Instant indexedEarlier = answer.minus(5, ChronoUnit.DAYS);
        DriftVerdict verdict = service.evaluate("brown fox",
                List.of(new ResolvedChunk("the quick brown fox jumps", indexedEarlier)), answer);
        assertThat(verdict.status()).isEqualTo(Status.FRESH);
        assertThat(verdict.reason()).isNull();
    }

    @Test
    void evaluate_unverifiable_whenNoChunks() {
        DriftVerdict verdict = service.evaluate("anything", List.of(),
                Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(verdict.status()).isEqualTo(Status.UNVERIFIABLE);
    }

    @Test
    void evaluate_normalizesWhitespaceAndCase() {
        Instant answer = Instant.parse("2026-01-01T00:00:00Z");
        DriftVerdict verdict = service.evaluate("The   Quick\nBrown FOX",
                List.of(new ResolvedChunk("...the quick brown fox...", null)), answer);
        assertThat(verdict.status()).isEqualTo(Status.FRESH);
    }

    @Test
    void evaluate_reindexTakesPrecedenceOverPresentText() {
        // Even though the cited text is still present, a newer index timestamp
        // wins — the source changed and was re-indexed after the answer.
        Instant answer = Instant.parse("2026-01-01T00:00:00Z");
        DriftVerdict verdict = service.evaluate("brown fox",
                List.of(new ResolvedChunk("the quick brown fox", answer.plusSeconds(60))), answer);
        assertThat(verdict.status()).isEqualTo(Status.STALE);
        assertThat(verdict.reason()).contains("re-indexed");
    }

    // ---- modification_date parsing --------------------------------------

    @Test
    void parseModificationDate_handlesIsoEpochAndDate() {
        Instant iso = TurCitationDriftService.parseModificationDate("2026-01-02T03:04:05Z");
        assertThat(iso).isEqualTo(Instant.parse("2026-01-02T03:04:05Z"));

        long millis = 1_767_000_000_000L;
        assertThat(TurCitationDriftService.parseModificationDate(millis))
                .isEqualTo(Instant.ofEpochMilli(millis));
        assertThat(TurCitationDriftService.parseModificationDate(String.valueOf(millis)))
                .isEqualTo(Instant.ofEpochMilli(millis));
        assertThat(TurCitationDriftService.parseModificationDate(new Date(millis)))
                .isEqualTo(Instant.ofEpochMilli(millis));

        assertThat(TurCitationDriftService.parseModificationDate(null)).isNull();
        assertThat(TurCitationDriftService.parseModificationDate("not-a-date")).isNull();
    }

    // ---- recordCitations() persistence ----------------------------------

    @Test
    void recordCitations_persistsDistinctSourceCitedPairs() {
        String json = citationsJson(
                citation("doc-1", "quote A"),
                citation("doc-1", "quote A"),   // exact dup → dropped
                citation("doc-1", "quote B"),   // same source, different span → kept
                citation("doc-2", "quote C"));

        int written = service.recordCitations("agent-1", "conv-1", "what is X?", 5, json, Instant.now());

        assertThat(written).isEqualTo(3);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TurChatCitationRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        List<TurChatCitationRecord> rows = captor.getValue();
        assertThat(rows).hasSize(3);
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.getConversationId()).isEqualTo("conv-1");
            assertThat(r.getAgentId()).isEqualTo("agent-1");
            assertThat(r.getQuestion()).isEqualTo("what is X?");
            assertThat(r.getTopK()).isEqualTo(5);
            assertThat(r.isCitationStale()).isFalse();
        });
    }

    @Test
    void recordCitations_skipsCitationsWithoutSourceId() {
        String json = citationsJson(citation("", "no source"), citation(null, "still none"));
        int written = service.recordCitations("agent-1", "conv-1", "q", 5, json, Instant.now());
        assertThat(written).isZero();
        verify(repository, never()).saveAll(any());
    }

    @Test
    void recordCitations_returnsZeroOnBlankPayload() {
        assertThat(service.recordCitations("a", "c", "q", 5, "", Instant.now())).isZero();
        verify(repository, never()).saveAll(any());
    }

    // ---- runDriftScan() flag-and-save -----------------------------------

    @Test
    void runDriftScan_flagsStaleRecordsAndPersistsVerdict() {
        Instant answer = Instant.parse("2026-01-01T00:00:00Z");
        TurChatCitationRecord fresh = record("conv-1", "doc-fresh", "present text", answer);
        TurChatCitationRecord drifted = record("conv-1", "doc-gone", "vanished text", answer);

        when(repository.findDueForScan(any(), any(), any())).thenReturn(List.of(fresh, drifted));
        // doc-fresh still has its text; doc-gone returns rewritten content.
        when(ragSearchToolService.retrieveRawForCitations(anyString(), anyInt()))
                .thenReturn(List.of(
                        sourceDoc("doc-fresh", "this present text remains", null),
                        sourceDoc("doc-gone", "completely different now", null)));

        int flagged = service.runDriftScan();

        assertThat(flagged).isEqualTo(1);
        assertThat(fresh.isCitationStale()).isFalse();
        assertThat(fresh.getLastCheckedAt()).isNotNull();
        assertThat(drifted.isCitationStale()).isTrue();
        assertThat(drifted.getStaleReason()).contains("no longer present");
        assertThat(drifted.getDriftDetectedAt()).isNotNull();
        verify(repository).saveAll(any());
    }

    @Test
    void runDriftScan_returnsZeroWhenDisabled() {
        configProperties.getGenai().getCitationDrift().setEnabled(false);
        assertThat(service.runDriftScan()).isZero();
        verify(repository, never()).findDueForScan(any(), any(), any());
    }

    // ---- helpers --------------------------------------------------------

    private static TurChatCitation citation(String sourceId, String citedText) {
        return new TurChatCitation(0, sourceId, "Title", "http://x", citedText,
                null, null, "search_result", null, null);
    }

    private static String citationsJson(TurChatCitation... citations) {
        return MAPPER.writeValueAsString(List.of(citations));
    }

    private static TurChatCitationRecord record(String conv, String sourceId, String citedText,
            Instant answerAt) {
        TurChatCitationRecord r = new TurChatCitationRecord();
        r.setConversationId(conv);
        r.setSourceId(sourceId);
        r.setCitedText(citedText);
        r.setQuestion("what is X?");
        r.setTopK(5);
        r.setAnswerProducedAt(answerAt);
        r.setCitationStale(false);
        return r;
    }

    private static Document sourceDoc(String sourceId, String text, Instant modDate) {
        Map<String, Object> meta = new java.util.HashMap<>();
        meta.put(TurSNGenAi.SOURCE_ID, sourceId);
        if (modDate != null) {
            meta.put(TurSNFieldName.MODIFICATION_DATE, modDate.toString());
        }
        return Document.builder().id(sourceId).text(text).metadata(meta).build();
    }
}
