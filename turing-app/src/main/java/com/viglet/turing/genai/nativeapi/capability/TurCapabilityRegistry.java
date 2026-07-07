/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.capability;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.tool.TurNativeToolService;
import com.viglet.turing.genai.tool.TurNativeToolService.NativeToolDescriptor;
import com.viglet.turing.genai.tool.TurNativeToolService.NativeToolGroup;

/**
 * T432 / §X.18 — the unified capability registry: the single source of truth
 * that merges provider-native capabilities ({@link TurNativeCapability}) with
 * the in-JVM Turing native-tool groups ({@link TurNativeToolService}) into one
 * tagged list of {@link TurCapabilityDescriptor}s.
 *
 * <p>Both the agent's capability-first picker (T434) and the admin instance ×
 * capability heatmap (T186) render from {@link #all()} — so a capability is
 * described in exactly one place. As future §X tasks land, a provider
 * capability ships by adding a {@link TurNativeCapability} constant (already
 * tagged with kind/function/category) and a Turing tool ships by adding a
 * {@code @Tool} method; both are absorbed here with no per-task screen.
 *
 * <p>The {@code function} tag is what makes the picker's mutex generic: the
 * only genuine cross-source overlap today is {@code code-exec} (Turing
 * {@code code-interpreter} ↔ OpenAI {@code code_interpreter} ↔ Anthropic
 * {@code code_execution}); every other Turing group keeps its own group id as
 * its function and never collides.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurCapabilityRegistry {

    /**
     * Turing tool groups whose function is a cross-source overlap (so they
     * group with a provider-native alternative in the picker). Anything not
     * listed uses its own group id as the function.
     */
    private static final Map<String, String> TURING_FUNCTION_OVERRIDES = Map.of(
            "code-interpreter", TurCapabilityFunctions.CODE_EXEC);

    /** Coarse category per Turing tool group, for visual layout. Default {@code "Tools"}. */
    private static final Map<String, String> TURING_CATEGORIES = Map.ofEntries(
            Map.entry("dsl", "Search"),
            Map.entry("date-time", "Utility"),
            Map.entry("finance", "Data"),
            Map.entry("weather", "Data"),
            Map.entry("code-interpreter", "Code"),
            Map.entry("web-crawler", "Web"),
            Map.entry("image-search", "Media"),
            Map.entry("logging", "Observability"),
            Map.entry("integration-monitoring", "Observability"),
            Map.entry("system-info", "Observability"),
            Map.entry("iconify", "Media"),
            Map.entry("scaffold", "Authoring"));

    private final TurNativeToolService nativeToolService;

    public TurCapabilityRegistry(TurNativeToolService nativeToolService) {
        this.nativeToolService = nativeToolService;
    }

    /** Control type for a {@code REQUEST_OPTION} row: a toggle. */
    static final String VALUE_BOOLEAN = "BOOLEAN";
    /** Control type for a {@code REQUEST_OPTION} row: a single-choice dropdown over {@code options}. */
    static final String VALUE_SELECT = "SELECT";

    /**
     * T435 / §X.18.d — the curated catalogue of {@code REQUEST_OPTION} knobs:
     * capabilities that change <em>how</em> a call is made rather than something
     * the model invokes. They render in the agent "Request Options" settings
     * (never the tool picker) and give the F.13 request-shape tasks
     * (T152/T160/T164/T167/T171/T173/T178–T182) a single, registry-driven home.
     * The descriptors are stable metadata; wiring each knob into the provider
     * request is the respective task's job.
     */
    private static final List<TurCapabilityDescriptor> REQUEST_OPTIONS = List.of(
            requestOption("reasoning-effort", "Reasoning Effort", "Compute budget for reasoning models.",
                    TurCapabilityProvider.OPENAI, "Reasoning", VALUE_SELECT,
                    List.of("minimal", "low", "medium", "high")),
            requestOption("reasoning-summary", "Reasoning Summary", "Include a summary of the model's reasoning.",
                    TurCapabilityProvider.OPENAI, "Reasoning", VALUE_SELECT,
                    List.of("auto", "concise", "detailed")),
            requestOption("logprobs", "Log Probabilities", "Return token log-probabilities with the answer.",
                    TurCapabilityProvider.OPENAI, "Reasoning", VALUE_BOOLEAN, List.of()),
            requestOption("structured-outputs", "Structured Outputs",
                    "Constrain the reply to a JSON schema.",
                    TurCapabilityProvider.ANY, "Output", VALUE_BOOLEAN, List.of()),
            requestOption("predicted-outputs", "Predicted Outputs",
                    "Speed up edits by predicting most of the output.",
                    TurCapabilityProvider.OPENAI, "Output", VALUE_BOOLEAN, List.of()),
            requestOption("service-tier", "Service Tier", "Latency/price tier for the request.",
                    TurCapabilityProvider.OPENAI, "Cost & Tier", VALUE_SELECT,
                    List.of("auto", "default", "flex", "priority")),
            requestOption("token-counting", "Token Counting",
                    "Pre-flight count of prompt tokens before the call.",
                    TurCapabilityProvider.ANTHROPIC, "Cost & Tier", VALUE_BOOLEAN, List.of()),
            requestOption("stored-completions", "Stored Completions",
                    "Persist completions for later eval/distillation.",
                    TurCapabilityProvider.OPENAI, "Cost & Tier", VALUE_BOOLEAN, List.of()),
            requestOption("citations", "Citations", "Return per-sentence source citations.",
                    TurCapabilityProvider.ANTHROPIC, "Provenance", VALUE_BOOLEAN, List.of()),
            requestOption("native-pdf", "Native PDF Grounding",
                    "Send retrieved PDFs to Claude as native documents (tables, layout and "
                            + "figures preserved) for page-accurate citations, instead of "
                            + "Tika-extracted text only. Pairs with Citations.",
                    TurCapabilityProvider.ANTHROPIC, "Provenance", VALUE_BOOLEAN, List.of()),
            requestOption("context-editing", "Context Editing",
                    "Let the provider prune stale context automatically.",
                    TurCapabilityProvider.ANTHROPIC, "Context", VALUE_BOOLEAN, List.of()),
            requestOption("compaction", "Compaction",
                    "Let the provider summarize older turns when the window fills.",
                    TurCapabilityProvider.ANTHROPIC, "Context", VALUE_BOOLEAN, List.of()),
            requestOption("moderation", "Moderation Pre-filter",
                    "Screen user input before it reaches the model.",
                    TurCapabilityProvider.OPENAI, "Safety", VALUE_BOOLEAN, List.of()),
            requestOption("safety-identifier", "Safety Identifier",
                    "Send a hashed end-user id for abuse monitoring.",
                    TurCapabilityProvider.OPENAI, "Safety", VALUE_BOOLEAN, List.of()),
            requestOption("gemini-context-cache", "Context Caching",
                    "Cache the stable prefix (system prompt + pinned grounding) as a named, "
                            + "TTL'd Gemini cachedContents object and reuse it across turns and "
                            + "users at a steep discount. The RAG cost lever; off by default.",
                    TurCapabilityProvider.GEMINI, "Cost & Tier", VALUE_BOOLEAN, List.of()),
            requestOption("openai-background", "Background Mode",
                    "Submit long reasoning/agentic turns asynchronously (background:true) and "
                            + "resume by polling the response id, so a slow turn doesn't hold the "
                            + "connection open. Off by default.",
                    TurCapabilityProvider.OPENAI, "Cost & Tier", VALUE_BOOLEAN, List.of()),
            requestOption("anthropic-extended-thinking", "Extended Thinking",
                    "Give Claude a reasoning budget (thinking budget_tokens); its thought "
                            + "summary feeds the same Why-this-answer panel. Optionally interleave "
                            + "thinking between tool calls. Off by default.",
                    TurCapabilityProvider.ANTHROPIC, "Reasoning", VALUE_BOOLEAN, List.of()));

    private static TurCapabilityDescriptor requestOption(String key, String label, String description,
            TurCapabilityProvider provider, String category, String valueType, List<String> options) {
        return new TurCapabilityDescriptor(key, label, description, TurCapabilityKind.REQUEST_OPTION,
                key, provider, category, false, List.of(), valueType, options);
    }

    /** Every capability — provider-native + Turing + request options — in one tagged list. */
    public List<TurCapabilityDescriptor> all() {
        List<TurCapabilityDescriptor> result = new ArrayList<>();
        result.addAll(nativeDescriptors());
        result.addAll(turingDescriptors());
        result.addAll(requestOptionDescriptors());
        return result;
    }

    /** T435 / §X.18.d — the {@code REQUEST_OPTION} knobs (request-shape settings). */
    public List<TurCapabilityDescriptor> requestOptionDescriptors() {
        return REQUEST_OPTIONS;
    }

    /** Provider-native capabilities, mapped from the enum taxonomy. */
    public List<TurCapabilityDescriptor> nativeDescriptors() {
        List<TurCapabilityDescriptor> result = new ArrayList<>();
        for (TurNativeCapability capability : TurNativeCapability.values()) {
            result.add(new TurCapabilityDescriptor(
                    capability.getKey(),
                    humanizeKey(capability.getKey()),
                    "",
                    capability.getKind(),
                    capability.getFunction(),
                    capability.getProvider(),
                    capability.getCategory(),
                    capability.isOwnsTurn(),
                    List.of()));
        }
        return result;
    }

    /** Turing in-JVM native-tool groups, tagged as {@code provider = TURING}. */
    public List<TurCapabilityDescriptor> turingDescriptors() {
        List<TurCapabilityDescriptor> result = new ArrayList<>();
        for (NativeToolGroup group : nativeToolService.getToolGroups()) {
            String function = TURING_FUNCTION_OVERRIDES.getOrDefault(group.id(), group.id());
            String category = TURING_CATEGORIES.getOrDefault(group.id(), "Tools");
            List<String> toolNames = group.tools().stream()
                    .map(NativeToolDescriptor::name)
                    .toList();
            result.add(new TurCapabilityDescriptor(
                    group.id(),
                    group.title(),
                    "",
                    TurCapabilityKind.TOOL,
                    function,
                    TurCapabilityProvider.TURING,
                    category,
                    false,
                    toolNames));
        }
        return result;
    }

    /**
     * Humanizes a capability key into a fallback label, e.g.
     * {@code "openai-web-search"} → {@code "OpenAI Web Search"}. The picker
     * localizes by key; this is only the no-i18n fallback.
     */
    static String humanizeKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String token : key.split("-")) {
            if (token.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(switch (token.toLowerCase(Locale.ROOT)) {
                case "openai" -> "OpenAI";
                case "mcp" -> "MCP";
                case "ai" -> "AI";
                default -> Character.toUpperCase(token.charAt(0)) + token.substring(1);
            });
        }
        return sb.toString();
    }
}
