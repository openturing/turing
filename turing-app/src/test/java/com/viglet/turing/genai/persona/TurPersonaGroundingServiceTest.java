/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import com.viglet.turing.genai.TurGenAiContext;
import com.viglet.turing.genai.TurGenAiContextFactory;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaGroundingSource;
import com.viglet.turing.persistence.model.persona.TurPersonaSource;
import com.viglet.turing.persistence.model.persona.TurPersonaSourceStatus;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.persona.TurPersonaSourceRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.sn.TurSNSearchProcess;

/**
 * Unit tests for the persona grounding service (T718).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurPersonaGroundingServiceTest {

    @Mock
    private TurSNSearchProcess turSNSearchProcess;
    @Mock
    private TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    @Mock
    private TurGenAiContextFactory turGenAiContextFactory;
    @Mock
    private TurPersonaSourceRepository turPersonaSourceRepository;

    @InjectMocks
    private TurPersonaGroundingService service;

    private static TurPersona persona(TurPersonaGroundingSource source) {
        TurPersona p = new TurPersona();
        p.setId("persona-1");
        p.setName("Ana");
        p.setGroundingSource(source);
        return p;
    }

    private static TurPersonaSource source(String text, TurPersonaSourceStatus status) {
        TurPersonaSource s = new TurPersonaSource();
        s.setSourceName("doc");
        s.setCachedText(text);
        s.setExtractionStatus(status);
        return s;
    }

    @Test
    void emptyWhenPersonaNull() {
        assertThat(service.groundingBlock(null, "q")).isEmpty();
    }

    @Test
    void emptyWhenSourceNone() {
        assertThat(service.groundingBlock(persona(TurPersonaGroundingSource.NONE), "q")).isEmpty();
    }

    @Test
    void emptyWhenQueryBlank() {
        assertThat(service.groundingBlock(persona(TurPersonaGroundingSource.NOTEBOOK), "  ")).isEmpty();
    }

    @Test
    void notebookGroundsOnExtractedCachedText() {
        when(turPersonaSourceRepository.findByTurPersona_IdOrderBySourceNameAsc("persona-1"))
                .thenReturn(List.of(
                        source("The refund window is 30 days.", TurPersonaSourceStatus.EXTRACTED),
                        source("still extracting", TurPersonaSourceStatus.PENDING),
                        source("failed one", TurPersonaSourceStatus.FAILED)));

        String block = service.groundingBlock(persona(TurPersonaGroundingSource.NOTEBOOK), "refund?");

        assertThat(block)
                .contains("# Grounded Knowledge")
                .contains("<persona_knowledge>")
                .contains("The refund window is 30 days.")
                .doesNotContain("still extracting")
                .doesNotContain("failed one");
    }

    @Test
    void notebookEmptyWhenNoExtractedText() {
        when(turPersonaSourceRepository.findByTurPersona_IdOrderBySourceNameAsc("persona-1"))
                .thenReturn(List.of(source("pending", TurPersonaSourceStatus.PENDING)));
        assertThat(service.groundingBlock(persona(TurPersonaGroundingSource.NOTEBOOK), "q")).isEmpty();
    }

    @Test
    void snSiteEmptyWhenSiteNotFound() {
        TurPersona p = persona(TurPersonaGroundingSource.SN_SITE);
        p.setGroundingSnSite("wknd");
        when(turSNSearchProcess.getSNSite("wknd")).thenReturn(Optional.empty());
        assertThat(service.groundingBlock(p, "q")).isEmpty();
    }

    @Test
    void snSiteEmptyWhenSiteNameBlank() {
        TurPersona p = persona(TurPersonaGroundingSource.SN_SITE);
        p.setGroundingSnSite("  ");
        assertThat(service.groundingBlock(p, "q")).isEmpty();
    }

    @Test
    void snSiteGroundsOnRetrievedDocuments() {
        TurPersona p = persona(TurPersonaGroundingSource.SN_SITE);
        p.setGroundingSnSite("wknd");
        TurSNSite site = new TurSNSite();
        VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
        TurGenAiContext context = TurGenAiContext.builder()
                .vectorStore(vectorStore).enabled(true).build();

        when(turSNSearchProcess.getSNSite("wknd")).thenReturn(Optional.of(site));
        lenient().when(turSNSiteLocaleRepository.findFirstByTurSNSiteOrderByPositionAsc(site))
                .thenReturn(null);
        when(turGenAiContextFactory.build(any(), any())).thenReturn(context);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("Trails open at dawn.")));

        String block = service.groundingBlock(p, "when do trails open?");

        assertThat(block)
                .contains("# Grounded Knowledge")
                .contains("Trails open at dawn.");
    }

    @Test
    void snSiteFailsOpenOnRetrievalError() {
        TurPersona p = persona(TurPersonaGroundingSource.SN_SITE);
        p.setGroundingSnSite("wknd");
        TurSNSite site = new TurSNSite();
        VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
        TurGenAiContext context = TurGenAiContext.builder()
                .vectorStore(vectorStore).enabled(true).build();

        when(turSNSearchProcess.getSNSite("wknd")).thenReturn(Optional.of(site));
        when(turGenAiContextFactory.build(any(), any())).thenReturn(context);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("embedding model unreachable"));

        assertThat(service.groundingBlock(p, "q")).isEmpty();
    }

    @Test
    void snSiteEmptyWhenContextDisabled() {
        TurPersona p = persona(TurPersonaGroundingSource.SN_SITE);
        p.setGroundingSnSite("wknd");
        TurSNSite site = new TurSNSite();
        when(turSNSearchProcess.getSNSite("wknd")).thenReturn(Optional.of(site));
        when(turGenAiContextFactory.build(any(), any())).thenReturn(TurGenAiContext.disabled());
        assertThat(service.groundingBlock(p, "q")).isEmpty();
    }
}
