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

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import com.viglet.turing.api.sn.job.TurSNImportAPI;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.exchange.sn.TurSNSiteContentExchangeService;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T253 / §XIII.3 — the gated write / ingestion MCP tools: {@code index_document},
 * {@code deindex_document}, {@code reindex_site}. They let an agent onboard or
 * refresh content ("index this folder of policy PDFs into the compliance site")
 * without the admin console or a connector project.
 *
 * <p><b>Double-gated, read-only by default (§XIII.6)</b>: these tools are only
 * registered in the MCP catalog when {@code turing.mcp-server.write-enabled=true}
 * (see {@link com.viglet.turing.mcp.server.TurMcpServerConfig}), <em>and</em>
 * even then {@code TurMcpToolScopePolicy} refuses the call unless the caller's
 * token carries the write scope ({@code SCOPE_mcp:write} by default). A
 * read-only deployment never sees them.
 *
 * <p><b>Reuse, don't fork (§XIII.5)</b>: indexing routes through the same
 * {@link TurSNImportAPI} JMS path the import REST endpoint uses (so the engine
 * routing, schema auto-creation, RAG fan-out, and tenant propagation all apply),
 * and the site reindex routes through {@link TurSNSiteContentExchangeService}.
 * Index/deindex are asynchronous (enqueued); the tools report that the work was
 * accepted, not that it has committed.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurMcpWriteToolService {

    private static final String DEFAULT_LOCALE = "en";

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNImportAPI turSNImportAPI;
    private final TurSNSiteContentExchangeService contentExchangeService;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public TurMcpWriteToolService(TurSNSiteRepository turSNSiteRepository,
            TurSNImportAPI turSNImportAPI,
            TurSNSiteContentExchangeService contentExchangeService) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNImportAPI = turSNImportAPI;
        this.contentExchangeService = contentExchangeService;
    }

    // ==================== index_document ====================

    @Tool(name = "index_document", description = ".")
    public String indexDocument(String site, String locale, String documentJson) {
        TurSNSite snSite = resolveSite(site);
        if (snSite == null) {
            return siteNotFound(site);
        }
        if (documentJson == null || documentJson.isBlank()) {
            return "Error: 'documentJson' is required (a JSON object with at least an 'id' field).";
        }
        Map<String, Object> attributes;
        try {
            attributes = objectMapper.readValue(documentJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return "Error: 'documentJson' is not a valid JSON object: " + e.getMessage();
        }
        Object id = attributes.get("id");
        if (id == null || id.toString().isBlank()) {
            return "Error: the document must include a non-empty 'id' field.";
        }
        send(TurSNJobAction.CREATE, snSite.getName(), locale, attributes);
        return "Accepted: document '%s' queued for indexing into site '%s'. Indexing is asynchronous."
                .formatted(id, snSite.getName());
    }

    // ==================== deindex_document ====================

    @Tool(name = "deindex_document", description = ".")
    public String deindexDocument(String site, String locale, String documentId) {
        TurSNSite snSite = resolveSite(site);
        if (snSite == null) {
            return siteNotFound(site);
        }
        if (documentId == null || documentId.isBlank()) {
            return "Error: 'documentId' is required.";
        }
        send(TurSNJobAction.DELETE, snSite.getName(), locale, Map.of("id", documentId));
        return "Accepted: document '%s' queued for removal from site '%s'. Deletion is asynchronous."
                .formatted(documentId, snSite.getName());
    }

    // ==================== reindex_site ====================

    @Tool(name = "reindex_site", description = ".")
    public String reindexSite(String site) {
        TurSNSite snSite = resolveSite(site);
        if (snSite == null) {
            return siteNotFound(site);
        }
        String siteId = snSite.getId();
        String taskId = "mcp-reindex-" + siteId;
        // Rebuild the site's RAG vector store from its indexed documents. Run on a
        // virtual thread (as the REST endpoint does) so the MCP call returns
        // promptly with a task id instead of blocking on a long rebuild.
        Thread.ofVirtual().start(() -> {
            try {
                contentExchangeService.reindexVectorStore(siteId, taskId);
            } catch (Exception e) {
                log.error("[MCP] reindex_site failed for site {}: {}", siteId, e.getMessage(), e);
            }
        });
        return "Accepted: started rebuilding the RAG vector store for site '%s' (taskId=%s). "
                .formatted(snSite.getName(), taskId)
                + "This runs in the background; it requires a RAG-enabled agent on the site.";
    }

    // ==================== helpers ====================

    private void send(TurSNJobAction action, String siteName, String locale, Map<String, Object> attributes) {
        Locale jobLocale = Locale.forLanguageTag(
                (locale != null && !locale.isBlank()) ? locale.replace('_', '-') : DEFAULT_LOCALE);
        TurSNJobItems items = new TurSNJobItems();
        items.add(new TurSNJobItem(action, List.of(siteName), jobLocale, attributes));
        turSNImportAPI.send(items);
    }

    private TurSNSite resolveSite(String site) {
        if (site == null || site.isBlank()) {
            return null;
        }
        return turSNSiteRepository.findByNameIgnoreCase(site).orElse(null);
    }

    private String siteNotFound(String site) {
        return "Error: site not found: '" + site + "' (use list_sites to discover sites).";
    }
}
