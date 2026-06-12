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

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.viglet.turing.genai.tool.TurToolCallbackPipeline;
import com.viglet.turing.properties.TurConfigProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * T245 / §XIII — wires the Turing tool catalog into the Spring AI MCP server so
 * an external AI client (Claude Desktop/Code, Cursor) can drive Turing over the
 * {@code /mcp} Streamable HTTP endpoint.
 *
 * <p>The whole configuration is gated on {@code spring.ai.mcp.server.enabled};
 * when the MCP server is off (the default) none of these beans exist and the
 * deployment is byte-for-byte unchanged.
 *
 * <p><b>Reuse, don't fork (§XIII.5)</b>: the {@link ToolCallbackProvider}
 * exposed here is built from {@link TurMcpSearchToolService} — the dedicated,
 * MCP-spec-named read surface ({@code list_sites}, {@code get_site_fields},
 * {@code search_site}, {@code get_document}, {@code similar_documents},
 * {@code facet_search}, {@code autocomplete}) — which itself delegates to the
 * <em>same</em> {@code TurDslToolService} the internal Semantic-Navigation agent
 * uses, so there is no second search implementation. The callbacks run through
 * the <em>same</em> {@link TurToolCallbackPipeline#decorate(ToolCallback...)}
 * path, so the curated {@code prompts/tools/mcp/*.md} descriptions and logging
 * come along. Registering raw {@code @Tool} callbacks (with their {@code "."}
 * placeholder descriptions) is forbidden by a project invariant; always
 * decorate first.
 *
 * <p>The T246 OAuth 2.1 resource-server gate ({@link TurMcpSecurityConfig}) and
 * the per-call scope guard ({@link TurMcpScopeToolCallback}) protect this
 * surface; {@link TurMcpLoopbackFilter} is the loopback-first fallback.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "enabled", havingValue = "true")
public class TurMcpServerConfig {

    /**
     * The order at which Spring Boot registers the {@code springSecurityFilterChain}
     * (Boot's {@code SecurityProperties.DEFAULT_FILTER_ORDER}). The loopback gate is
     * registered just ahead of it.
     */
    private static final int SECURITY_FILTER_ORDER = -100;

    /**
     * The decorated tool catalog the MCP server publishes. Spring AI's MCP
     * server auto-configuration discovers every {@link ToolCallbackProvider}
     * bean and converts its callbacks into MCP tool specifications.
     */
    @Bean
    ToolCallbackProvider turingMcpToolCallbackProvider(TurMcpSearchToolService searchToolService,
            TurMcpRagToolService ragToolService,
            TurMcpAgentToolService agentToolService,
            TurMcpAnalyticsToolService analyticsToolService,
            TurMcpWriteToolService writeToolService,
            TurToolCallbackPipeline toolCallbackPipeline,
            TurMcpToolScopePolicy toolScopePolicy,
            TurConfigProperties configProperties) {
        // Read tools are always published; the T253 write/ingestion tools are
        // added ONLY when the operator opts in (and still require the write
        // scope per call via TurMcpToolScopePolicy).
        java.util.List<Object> toolObjects = new java.util.ArrayList<>(
                java.util.List.of(searchToolService, ragToolService, agentToolService, analyticsToolService));
        if (configProperties.getMcpServer().isWriteEnabled()) {
            toolObjects.add(writeToolService);
            log.info("[MCP] Write/ingestion tools ENABLED (require the {} scope)",
                    configProperties.getMcpServer().getWriteScope());
        }
        ToolCallback[] raw = MethodToolCallbackProvider.builder()
                .toolObjects(toolObjects.toArray())
                .build()
                .getToolCallbacks();
        ToolCallback[] decorated = toolCallbackPipeline.decorate(raw);
        // T246 — outermost decorator: enforce per-tool scope at call time
        // (defense in depth, independent of what the endpoint listed).
        ToolCallback[] scoped = TurMcpScopeToolCallback.wrap(decorated, toolScopePolicy);
        log.info("[MCP] Turing MCP server publishing {} tool(s) at /mcp", scoped.length);
        return ToolCallbackProvider.from(scoped);
    }

    /**
     * Registers the loopback-first trust boundary (T245) on the {@code /mcp}
     * path, ahead of the Spring Security filter chain so a remote request is
     * refused before any further processing. The gate honours
     * {@code turing.mcp-server.loopback-only} (default {@code true}).
     */
    @Bean
    FilterRegistrationBean<TurMcpLoopbackFilter> turingMcpLoopbackFilter(TurConfigProperties configProperties) {
        boolean loopbackOnly = configProperties.getMcpServer().isLoopbackOnly();
        FilterRegistrationBean<TurMcpLoopbackFilter> registration =
                new FilterRegistrationBean<>(new TurMcpLoopbackFilter(loopbackOnly));
        registration.addUrlPatterns("/mcp", "/mcp/*");
        // Run ahead of the Spring Security filter chain (registered at
        // SecurityProperties.DEFAULT_FILTER_ORDER == -100) so a non-loopback
        // request is rejected before any further processing.
        registration.setOrder(SECURITY_FILTER_ORDER - 10);
        registration.setName("turingMcpLoopbackFilter");
        log.info("[MCP] Loopback gate on /mcp is {}", loopbackOnly ? "ENABLED" : "DISABLED (remote access allowed)");
        return registration;
    }
}
