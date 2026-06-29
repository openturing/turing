/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.clienttool;

import java.util.List;

import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.agent.TurAIAgent;

/**
 * T443 / §XXIII.2 — the built-in "co-browse" client tools.
 *
 * <p>These let the chat agent drive the host page's REAL search UI — query,
 * facet checkboxes, sort, page, locale — rather than answering in a parallel box.
 * "filter to the red ones under R$50" flips the actual facets and updates the URL
 * ({@code useTuringUrlSearch}). They are folded into an agent's advertised client
 * tools by {@link TurClientToolService} when {@code TurAIAgent#isCoBrowseEnabled()},
 * and park + resume through the T438 protocol; the SDK's {@code useCoBrowseSearch}
 * hook registers the matching handlers wired to the search store.
 *
 * <p>Names here MUST match the handler keys the SDK registers.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurCoBrowseClientTools implements TurBuiltInClientToolProvider {

    public static final String SET_SEARCH_QUERY = "set_search_query";
    public static final String TOGGLE_FACET = "toggle_facet";
    public static final String CLEAR_FACETS = "clear_facets";
    public static final String SET_SORT = "set_sort";
    public static final String SET_PAGE = "set_page";

    private static final String SET_SEARCH_QUERY_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "query": { "type": "string", "description": "The text to search for on the page." }
              },
              "required": ["query"]
            }""";

    private static final String TOGGLE_FACET_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "field": { "type": "string", "description": "The facet group name (e.g. 'color')." },
                "value": { "type": "string", "description": "The facet value label to toggle (e.g. 'Red')." }
              },
              "required": ["field","value"]
            }""";

    private static final String SET_SORT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "sort": { "type": "string", "description": "A sort option value (e.g. 'relevance', 'newest')." }
              },
              "required": ["sort"]
            }""";

    private static final String SET_PAGE_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "page": { "type": "integer", "minimum": 1, "description": "1-based page number." }
              },
              "required": ["page"]
            }""";

    private final List<TurClientTool> declarations = List.of(
            new TurClientTool(SET_SEARCH_QUERY,
                    "Set the search box query on the page and run the search (resets to page 1).",
                    SET_SEARCH_QUERY_SCHEMA),
            new TurClientTool(TOGGLE_FACET,
                    "Toggle a facet checkbox on the page by its group name and value label. "
                            + "Only use field/value pairs present in the current facet sidebar.",
                    TOGGLE_FACET_SCHEMA),
            new TurClientTool(CLEAR_FACETS,
                    "Clear all selected facet filters on the page.",
                    TurClientTool.EMPTY_OBJECT_SCHEMA),
            new TurClientTool(SET_SORT,
                    "Change the result sort order on the page.",
                    SET_SORT_SCHEMA),
            new TurClientTool(SET_PAGE,
                    "Go to a specific results page on the page.",
                    SET_PAGE_SCHEMA));

    @Override
    public boolean appliesTo(TurAIAgent agent) {
        return agent != null && agent.isCoBrowseEnabled();
    }

    @Override
    public List<TurClientTool> declarations(TurAIAgent agent) {
        return declarations;
    }
}
