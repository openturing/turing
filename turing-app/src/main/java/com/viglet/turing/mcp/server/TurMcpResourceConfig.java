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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceTemplateSpecification;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.ResourceTemplate;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;

/**
 * T250 / §XIII.3 — exposes Turing content as browsable MCP <em>resources</em>,
 * so a client can <em>attach</em> a source document or a site schema to its
 * context window directly, not only receive a search snippet — the difference
 * between "here's a summary" and "here's the actual record, reason over it".
 *
 * <p>Three resources are published (gated on {@code spring.ai.mcp.server.enabled}):
 * <ul>
 *   <li>a concrete <b>{@code turing://sites}</b> — the catalog of searchable
 *       sites (read dynamically, so it always reflects the current sites);</li>
 *   <li>a template <b>{@code turing://site/{site}/schema}</b> — the field schema
 *       of one site;</li>
 *   <li>a template <b>{@code turing://site/{site}/doc/{docId}}</b> — one indexed
 *       document by id.</li>
 * </ul>
 *
 * <p><b>Reuse, don't fork (§XIII.5)</b>: every read handler delegates to
 * {@link TurMcpSearchToolService} (the same code behind the {@code list_sites} /
 * {@code get_site_fields} / {@code get_document} tools), so resources and tools
 * never drift. Resources inherit the endpoint's T246 auth/loopback gate.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
// The MCP SDK 2.0.0-RC1 marks the Resource/ResourceTemplate builders and the
// ReadResourceResult/TextResourceContents constructors @Deprecated (a churning
// pre-GA API), but they remain the documented way to build these records and
// are not forRemoval. Suppressing here mirrors the sanctioned approach already
// used for the MCP client transport (see TurMcpToolCallbackService); revisit on
// the GA bump.
@SuppressWarnings("deprecation")
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "enabled", havingValue = "true")
public class TurMcpResourceConfig {

    private static final String JSON_MIME = "application/json";
    private static final String DEFAULT_LOCALE = "en";
    private static final Pattern SCHEMA_URI = Pattern.compile("^turing://site/([^/]+)/schema$");
    private static final Pattern DOC_URI = Pattern.compile("^turing://site/([^/]+)/doc/(.+)$");

    private final TurMcpSearchToolService searchToolService;

    public TurMcpResourceConfig(TurMcpSearchToolService searchToolService) {
        this.searchToolService = searchToolService;
    }

    @Bean
    List<SyncResourceSpecification> turingMcpResources() {
        Resource sites = Resource.builder()
                .uri("turing://sites")
                .name("turing-sites")
                .title("Turing sites")
                .description("The catalog of enterprise search sites available on this Turing server.")
                .mimeType(JSON_MIME)
                .build();

        SyncResourceSpecification sitesSpec = new SyncResourceSpecification(sites,
                (exchange, request) -> text(request.uri(), JSON_MIME, searchToolService.listSites(null)));

        return List.of(sitesSpec);
    }

    @Bean
    List<SyncResourceTemplateSpecification> turingMcpResourceTemplates() {
        ResourceTemplate schema = ResourceTemplate.builder()
                .uriTemplate("turing://site/{site}/schema")
                .name("turing-site-schema")
                .title("Turing site schema")
                .description("The field schema (names, types, facets) of one site.")
                .mimeType(JSON_MIME)
                .build();

        ResourceTemplate document = ResourceTemplate.builder()
                .uriTemplate("turing://site/{site}/doc/{docId}")
                .name("turing-site-document")
                .title("Turing indexed document")
                .description("A single indexed document by id, with all of its source fields.")
                .mimeType(JSON_MIME)
                .build();

        SyncResourceTemplateSpecification schemaSpec = new SyncResourceTemplateSpecification(schema,
                (exchange, request) -> readSchema(request.uri()));
        SyncResourceTemplateSpecification documentSpec = new SyncResourceTemplateSpecification(document,
                (exchange, request) -> readDocument(request.uri()));

        return List.of(schemaSpec, documentSpec);
    }

    private ReadResourceResult readSchema(String uri) {
        Matcher m = SCHEMA_URI.matcher(uri);
        if (!m.matches()) {
            return text(uri, JSON_MIME, "Error: malformed schema resource URI: " + uri);
        }
        return text(uri, JSON_MIME, searchToolService.getSiteFields(m.group(1)));
    }

    private ReadResourceResult readDocument(String uri) {
        Matcher m = DOC_URI.matcher(uri);
        if (!m.matches()) {
            return text(uri, JSON_MIME, "Error: malformed document resource URI: " + uri);
        }
        return text(uri, JSON_MIME, searchToolService.getDocument(m.group(1), DEFAULT_LOCALE, m.group(2)));
    }

    private ReadResourceResult text(String uri, String mimeType, String body) {
        return new ReadResourceResult(List.of(new TextResourceContents(uri, mimeType, body)));
    }
}
