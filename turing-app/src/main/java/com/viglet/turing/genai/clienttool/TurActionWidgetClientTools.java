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
 * T450 / §XXIII.9 — the built-in "action widget" client tools that turn the
 * embedded SDK from a search/chat box into an ACTION LAYER over the host page.
 *
 * <p>The agent calls these to act on the site the visitor is on: {@code navigate}
 * (go to a URL), {@code fill_form} (set field values), {@code click_element}
 * (click a control), {@code add_to_cart} (a commerce hook the host implements).
 * They are <em>client</em> tools (T438): they park the turn and the embedding host
 * runs them via the vanilla SDK's {@code createHostActions} handlers — Turing
 * competes with browser-agent products without a browser extension. Folded into
 * the advertised set when {@code TurAIAgent#isActionWidgetEnabled()}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurActionWidgetClientTools implements TurBuiltInClientToolProvider {

    public static final String NAVIGATE = "navigate";
    public static final String FILL_FORM = "fill_form";
    public static final String CLICK_ELEMENT = "click_element";
    public static final String ADD_TO_CART = "add_to_cart";

    private static final String NAVIGATE_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "url": { "type": "string", "description": "Absolute or site-relative URL to open." }
              },
              "required": ["url"]
            }""";

    private static final String FILL_FORM_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "fields": {
                  "type": "array",
                  "description": "Form fields to set on the page.",
                  "items": {
                    "type": "object",
                    "properties": {
                      "selector": { "type": "string", "description": "CSS selector or input name." },
                      "value": { "type": "string" }
                    },
                    "required": ["selector","value"]
                  }
                }
              },
              "required": ["fields"]
            }""";

    private static final String CLICK_ELEMENT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "selector": { "type": "string", "description": "CSS selector of the element to click." }
              },
              "required": ["selector"]
            }""";

    private static final String ADD_TO_CART_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "productId": { "type": "string", "description": "Identifier of the product to add." },
                "quantity": { "type": "integer", "minimum": 1, "description": "How many (default 1)." }
              },
              "required": ["productId"]
            }""";

    private final List<TurClientTool> declarations = List.of(
            new TurClientTool(NAVIGATE,
                    "Navigate the host page to a URL (e.g. open a product or category page).",
                    NAVIGATE_SCHEMA),
            new TurClientTool(FILL_FORM,
                    "Fill one or more form fields on the host page by selector/name and value.",
                    FILL_FORM_SCHEMA),
            new TurClientTool(CLICK_ELEMENT,
                    "Click an element on the host page by CSS selector.",
                    CLICK_ELEMENT_SCHEMA),
            new TurClientTool(ADD_TO_CART,
                    "Add a product to the host site's cart (the site implements the action).",
                    ADD_TO_CART_SCHEMA));

    @Override
    public boolean appliesTo(TurAIAgent agent) {
        return agent != null && agent.isActionWidgetEnabled();
    }

    @Override
    public List<TurClientTool> declarations(TurAIAgent agent) {
        return declarations;
    }
}
