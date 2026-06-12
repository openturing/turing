/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.api.sn.job.TurSNImportAPI;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.exchange.sn.TurSNSiteContentExchangeService;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;

/**
 * Unit tests for {@link TurMcpWriteToolService} — the T253 gated write tools.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurMcpWriteToolServiceTest {

    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurSNImportAPI turSNImportAPI;
    @Mock
    private TurSNSiteContentExchangeService contentExchangeService;

    @InjectMocks
    private TurMcpWriteToolService service;

    private TurSNSite site(String name, String id) {
        TurSNSite site = new TurSNSite();
        site.setName(name);
        site.setId(id);
        return site;
    }

    private TurSNJobItem firstItem() {
        ArgumentCaptor<TurSNJobItems> captor = ArgumentCaptor.forClass(TurSNJobItems.class);
        verify(turSNImportAPI).send(captor.capture());
        return captor.getValue().iterator().next();
    }

    @Test
    void indexDocument_enqueuesCreateJob() {
        when(turSNSiteRepository.findByNameIgnoreCase("wiki")).thenReturn(Optional.of(site("wiki", "s1")));

        String result = service.indexDocument("wiki", "en_US",
                "{\"id\":\"doc-1\",\"title\":\"Hello\"}");

        assertTrue(result.startsWith("Accepted"), result);
        TurSNJobItem item = firstItem();
        assertEquals(TurSNJobAction.CREATE, item.getTurSNJobAction());
        assertEquals("doc-1", item.getAttributes().get("id"));
        assertEquals("Hello", item.getAttributes().get("title"));
    }

    @Test
    void indexDocument_missingId_returnsErrorAndDoesNotSend() {
        when(turSNSiteRepository.findByNameIgnoreCase("wiki")).thenReturn(Optional.of(site("wiki", "s1")));
        String result = service.indexDocument("wiki", "en_US", "{\"title\":\"No id\"}");
        assertTrue(result.startsWith("Error"), result);
        verify(turSNImportAPI, never()).send(any());
    }

    @Test
    void indexDocument_invalidJson_returnsError() {
        when(turSNSiteRepository.findByNameIgnoreCase("wiki")).thenReturn(Optional.of(site("wiki", "s1")));
        String result = service.indexDocument("wiki", "en_US", "not json");
        assertTrue(result.startsWith("Error"), result);
        verify(turSNImportAPI, never()).send(any());
    }

    @Test
    void indexDocument_siteNotFound_returnsError() {
        when(turSNSiteRepository.findByNameIgnoreCase("nope")).thenReturn(Optional.empty());
        assertTrue(service.indexDocument("nope", "en_US", "{\"id\":\"x\"}").startsWith("Error"));
        verify(turSNImportAPI, never()).send(any());
    }

    @Test
    void deindexDocument_enqueuesDeleteJob() {
        when(turSNSiteRepository.findByNameIgnoreCase("wiki")).thenReturn(Optional.of(site("wiki", "s1")));

        String result = service.deindexDocument("wiki", "en_US", "doc-9");

        assertTrue(result.startsWith("Accepted"), result);
        TurSNJobItem item = firstItem();
        assertEquals(TurSNJobAction.DELETE, item.getTurSNJobAction());
        assertEquals("doc-9", item.getAttributes().get("id"));
    }

    @Test
    void deindexDocument_missingId_returnsError() {
        when(turSNSiteRepository.findByNameIgnoreCase("wiki")).thenReturn(Optional.of(site("wiki", "s1")));
        assertTrue(service.deindexDocument("wiki", "en_US", " ").startsWith("Error"));
        verify(turSNImportAPI, never()).send(any());
    }

    @Test
    void reindexSite_startsBackgroundReindex() {
        when(turSNSiteRepository.findByNameIgnoreCase("wiki")).thenReturn(Optional.of(site("wiki", "s1")));

        String result = service.reindexSite("wiki");

        assertTrue(result.startsWith("Accepted"), result);
        assertTrue(result.contains("mcp-reindex-s1"), result);
        // The reindex runs on a virtual thread; verify it is invoked (with a timeout).
        verify(contentExchangeService, timeout(3000)).reindexVectorStore(eq("s1"), anyString());
    }

    @Test
    void reindexSite_siteNotFound_returnsError() {
        when(turSNSiteRepository.findByNameIgnoreCase("nope")).thenReturn(Optional.empty());
        assertTrue(service.reindexSite("nope").startsWith("Error"));
        verify(contentExchangeService, never()).reindexVectorStore(anyString(), anyString());
    }
}
