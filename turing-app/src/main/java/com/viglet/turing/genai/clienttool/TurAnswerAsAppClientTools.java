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
 * T442 / §XXIII.1 — the built-in "answer-as-an-app" generative-UI client tools.
 *
 * <p>These are {@link TurClientTool} declarations the agent can call to render a
 * live mini-app from a typed SN search result set instead of replying with
 * prose. They are folded into an agent's advertised client tools by
 * {@link TurClientToolService} whenever {@code TurAIAgent#isAnswerAsAppEnabled()}
 * — so they park the turn and resume through the very same T438 protocol as
 * operator-declared client tools; the browser renders them through the T440
 * generative-UI surface (a registered component per tool name). The frontend
 * derives controls from the {@code type} carried on each field/column
 * ({@code currency} → slider, {@code date} → date, etc.), which the model fills
 * from the manifest field types (T386).
 *
 * <p>Names here MUST match the React component names the SDK registers
 * ({@code comparison_table}, {@code spec_card}, {@code configurator}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurAnswerAsAppClientTools implements TurBuiltInClientToolProvider {

    @Override
    public boolean appliesTo(TurAIAgent agent) {
        return agent != null && agent.isAnswerAsAppEnabled();
    }

    /** Stable tool/component name: a side-by-side comparison table. */
    public static final String COMPARISON_TABLE = "comparison_table";
    /** Stable tool/component name: a single-entity spec card. */
    public static final String SPEC_CARD = "spec_card";
    /** Stable tool/component name: an interactive configurator/refiner. */
    public static final String CONFIGURATOR = "configurator";

    private static final String COMPARISON_TABLE_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "title": { "type": "string", "description": "Heading for the table." },
                "columns": {
                  "type": "array",
                  "description": "Ordered columns. 'type' drives client formatting.",
                  "items": {
                    "type": "object",
                    "properties": {
                      "key": { "type": "string" },
                      "label": { "type": "string" },
                      "type": {
                        "type": "string",
                        "enum": ["string","number","currency","date","boolean","image","url"]
                      }
                    },
                    "required": ["key","label"]
                  }
                },
                "rows": {
                  "type": "array",
                  "description": "One object per result, keyed by column key.",
                  "items": { "type": "object" }
                }
              },
              "required": ["columns","rows"]
            }""";

    private static final String SPEC_CARD_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "title": { "type": "string" },
                "subtitle": { "type": "string" },
                "imageUrl": { "type": "string" },
                "fields": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "label": { "type": "string" },
                      "value": {},
                      "type": {
                        "type": "string",
                        "enum": ["string","number","currency","date","boolean","url"]
                      }
                    },
                    "required": ["label","value"]
                  }
                },
                "actions": {
                  "type": "array",
                  "description": "Buttons; the chosen value is sent back to the agent.",
                  "items": {
                    "type": "object",
                    "properties": {
                      "label": { "type": "string" },
                      "value": { "type": "string" }
                    },
                    "required": ["label","value"]
                  }
                }
              },
              "required": ["title","fields"]
            }""";

    private static final String CONFIGURATOR_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "title": { "type": "string" },
                "submitLabel": { "type": "string" },
                "controls": {
                  "type": "array",
                  "description": "Inputs derived from field types; the chosen values are sent back.",
                  "items": {
                    "type": "object",
                    "properties": {
                      "key": { "type": "string" },
                      "label": { "type": "string" },
                      "type": {
                        "type": "string",
                        "enum": ["slider","range","select","toggle","text"]
                      },
                      "min": { "type": "number" },
                      "max": { "type": "number" },
                      "step": { "type": "number" },
                      "unit": { "type": "string" },
                      "options": { "type": "array", "items": { "type": "string" } }
                    },
                    "required": ["key","label","type"]
                  }
                }
              },
              "required": ["controls"]
            }""";

    private final List<TurClientTool> declarations = List.of(
            new TurClientTool(COMPARISON_TABLE,
                    "Render a side-by-side comparison table from SN search results. "
                            + "Set each column's 'type' from the field's manifest type "
                            + "(CURRENCY→currency, DATE→date, etc.) so the client formats it.",
                    COMPARISON_TABLE_SCHEMA),
            new TurClientTool(SPEC_CARD,
                    "Render a spec card for a single result: image, key fields (typed) "
                            + "and optional action buttons whose value is returned on click.",
                    SPEC_CARD_SCHEMA),
            new TurClientTool(CONFIGURATOR,
                    "Render an interactive configurator/refiner. Derive controls from field "
                            + "types: numeric/CURRENCY→slider or range, faceted→select, boolean→toggle. "
                            + "The chosen values are returned for a follow-up search.",
                    CONFIGURATOR_SCHEMA));

    /** The built-in answer-as-app client-tool declarations (static — agent-independent). */
    @Override
    public List<TurClientTool> declarations(TurAIAgent agent) {
        return declarations;
    }
}
