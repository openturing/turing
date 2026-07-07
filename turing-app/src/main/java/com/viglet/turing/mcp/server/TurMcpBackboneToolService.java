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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.system.TurLlmSummaryService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T188 / §X.15.b — {@code describe_backbone}: the zero-config discovery tool that
 * makes Turing consumable as a <em>headless enterprise-search backbone</em>
 * inside any other AI product. One call returns a machine-readable manifest of
 * this instance — product identity, which capabilities are live (search, cited
 * RAG answers, headless agents, analytics, write/ingestion), and the full tool
 * catalogue grouped by purpose with a one-line description each — so a consuming
 * orchestrator can self-onboard in a single round-trip instead of probing each
 * tool. It is the entry point that turns the T245–T253 MCP surface from "a set of
 * tools" into "a backbone": abandon the chat UI as the only consumption surface.
 *
 * <p><b>Reuse, don't fork (§XIII.5)</b>: every capability flag is read from the
 * same source the feature itself uses — {@link TurLlmSummaryService#isAvailable()}
 * (the exact gate {@code rag_answer} checks before composing a grounded answer),
 * the enabled-agent count from {@link TurAIAgentRepository}, and the write toggle
 * from {@link TurConfigProperties}. Site enumeration is intentionally NOT
 * duplicated here — the manifest points the consumer at {@code list_sites} /
 * {@code get_site_fields}, which already own that surface. Read-only; published on
 * every MCP turn (no extra scope).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurMcpBackboneToolService {

    private static final String PRODUCT = "Viglet Turing ES";

    private final TurAIAgentRepository turAIAgentRepository;
    private final TurLlmSummaryService llmSummaryService;
    private final TurConfigProperties configProperties;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public TurMcpBackboneToolService(TurAIAgentRepository turAIAgentRepository,
            TurLlmSummaryService llmSummaryService,
            TurConfigProperties configProperties) {
        this.turAIAgentRepository = turAIAgentRepository;
        this.llmSummaryService = llmSummaryService;
        this.configProperties = configProperties;
    }

    @Tool(name = "describe_backbone", description = ".")
    public String describeBackbone() {
        boolean ragAvailable = ragAvailable();
        long enabledAgents = enabledAgentCount();
        boolean writeEnabled = configProperties.getMcpServer().isWriteEnabled();

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("product", PRODUCT);
        manifest.put("version", appVersion());
        manifest.put("role", "Headless enterprise-search backbone — consume this server's tools "
                + "over MCP to add search and cited answers to your own AI product.");
        manifest.put("endpoint", "/mcp");

        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("search", true);
        capabilities.put("ragAnswer", ragAvailable);
        capabilities.put("agents", enabledAgents);
        capabilities.put("analytics", true);
        capabilities.put("write", writeEnabled);
        manifest.put("capabilities", capabilities);

        Map<String, Object> tools = new LinkedHashMap<>();
        tools.put("discovery", List.of(
                tool("describe_backbone", "This manifest — start here to learn what the backbone offers."),
                tool("list_sites", "List the searchable sites (indices) on this server."),
                tool("get_site_fields", "Describe a site's fields/types before querying it.")));
        tools.put("search", List.of(
                tool("search_site", "Full-text search a site; returns ranked documents."),
                tool("facet_search", "Aggregate a query by a field to drill down by facet."),
                tool("autocomplete", "Prefix suggestions for a partial query."),
                tool("similar_documents", "Find documents most similar to seed text."),
                tool("get_document", "Fetch one document by id.")));
        tools.put("answer", List.of(
                tool("rag_answer", ragAvailable
                        ? "Cited, LLM-grounded answer over one site's content (available)."
                        : "Cited answer over a site — currently returns ranked sources only "
                                + "(no default LLM configured).")));
        if (enabledAgents > 0) {
            tools.put("agents", List.of(
                    tool("list_agents", "Discover the configured AI agents (chat-flows)."),
                    tool("invoke_agent", "Run an agent headlessly; returns its answer + captured slots.")));
        }
        tools.put("analytics", List.of(
                tool("search_metrics", "Search volume / no-result rate over a period."),
                tool("top_failed_searches", "Queries that returned no results."),
                tool("content_gaps", "Demanded-but-missing topics inferred from failed searches.")));
        if (writeEnabled) {
            tools.put("write", List.of(
                    tool("index_document", "Index/update one document (requires the write scope)."),
                    tool("deindex_document", "Remove one document (requires the write scope)."),
                    tool("reindex_site", "Trigger a site reindex (requires the write scope).")));
        }
        manifest.put("tools", tools);

        manifest.put("gettingStarted", List.of(
                "1. Call list_sites to find the site(s) you can search.",
                "2. Call get_site_fields on a site to learn its fields before querying.",
                ragAvailable
                        ? "3. For a grounded, cited answer call rag_answer; for raw hits call search_site."
                        : "3. Call search_site for ranked hits (configure a default LLM to enable rag_answer).",
                enabledAgents > 0
                        ? "4. To reuse an authored flow (e.g. lead capture / triage) call invoke_agent."
                        : "4. No headless agents are configured on this server yet."));

        try {
            return objectMapper.writeValueAsString(manifest);
        } catch (Exception e) {
            log.error("[MCP] describe_backbone failed to serialize manifest", e);
            return "Error building backbone manifest: " + e.getMessage();
        }
    }

    private static Map<String, Object> tool(String name, String purpose) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("purpose", purpose);
        return m;
    }

    private boolean ragAvailable() {
        try {
            return llmSummaryService.isAvailable();
        } catch (Exception e) {
            log.debug("[MCP] describe_backbone could not resolve RAG availability", e);
            return false;
        }
    }

    private long enabledAgentCount() {
        try {
            long count = 0;
            for (TurAIAgent agent : turAIAgentRepository.findAll()) {
                if (agent.getEnabled() == 1) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            log.debug("[MCP] describe_backbone could not count agents", e);
            return 0;
        }
    }

    private String appVersion() {
        String version = getClass().getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }
}
