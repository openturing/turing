/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Tool service that exposes the public Iconify search API to LLMs. Used
 * mainly by AI authoring flows (e.g. Intent creation) so the model can
 * pick a contextually appropriate icon name from the same registry the
 * frontend's IconPicker uses.
 *
 * <p>Iconify icons are referenced as {@code <prefix>:<name>} (for example
 * {@code mdi:home}, {@code tabler:user}, {@code ph:lightning}). The
 * frontend renders them via {@code @iconify/react}'s {@code <Icon icon="..."/>}.
 *
 * <p>The actual {@code @Tool} description is loaded from
 * {@code prompts/tools/iconify/search_icons.md} via
 * {@link TurToolDescriptionService} — the {@code "."} placeholder is
 * required by the platform convention so {@code TurNativeToolService}
 * recognizes it as a discoverable tool.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@Slf4j
@Service
public class TurIconifyToolService {

    private static final String ICONIFY_SEARCH_URL = "https://api.iconify.design/search";
    /** Same set the frontend IconPicker queries — keep in sync. */
    private static final String DEFAULT_PREFIXES = "lucide,tabler,mdi,ph,solar,heroicons";
    private static final int DEFAULT_LIMIT = 32;
    private static final int MAX_LIMIT = 96;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool(name = "search_icons", description = ".")
    public String search(String query, Integer limit) {
        if (query == null || query.isBlank()) {
            return "Error: 'query' is required (e.g. 'home', 'shopping cart', 'graduation hat').";
        }
        int boundedLimit = clampLimit(limit);
        String url = ICONIFY_SEARCH_URL
                + "?query=" + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)
                + "&limit=" + boundedLimit
                + "&prefixes=" + DEFAULT_PREFIXES;

        log.info("[Iconify Tool] search_icons called: query='{}', limit={}", query, boundedLimit);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                return "Iconify search failed: HTTP " + response.statusCode();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode iconsNode = root.path("icons");
            if (!iconsNode.isArray() || iconsNode.isEmpty()) {
                return "No icons found for query: " + query;
            }

            List<String> icons = new java.util.ArrayList<>(iconsNode.size());
            for (JsonNode n : iconsNode) {
                if (n.isString()) icons.add(n.stringValue());
            }
            int total = root.path("total").asInt(icons.size());

            return "Found %d matches (showing %d). Pick one of these Iconify identifiers:\n%s"
                    .formatted(total, icons.size(), String.join("\n", icons));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[Iconify Tool] search_icons interrupted", e);
            return "Error searching Iconify: " + e.getMessage();
        } catch (Exception e) {
            log.error("[Iconify Tool] search_icons failed", e);
            return "Error searching Iconify: " + e.getMessage();
        }
    }

    private static int clampLimit(Integer requested) {
        if (requested == null || requested <= 0) return DEFAULT_LIMIT;
        return Math.min(requested, MAX_LIMIT);
    }
}
