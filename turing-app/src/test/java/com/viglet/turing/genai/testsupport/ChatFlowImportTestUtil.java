/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAIAgentSlot;
import com.viglet.turing.persistence.model.agent.TurAIAgentSlotType;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowGuardrailMethod;
import com.viglet.turing.persistence.model.agent.TurChatFlowTriggerMode;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaLanguageStyle;
import com.viglet.turing.persistence.model.persona.TurPersonaTone;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurAIAgentSlotRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Stateless helper that imports a chat-flow JSON bundle into an agent —
 * the same pipeline {@code TurChatFlowAPI.turChatFlowImportBundle} runs at
 * the REST layer, but at the granularity ITs need (direct repository
 * writes, no JSR-303 dance, no security context).
 *
 * <p>Extracted from {@code ProgramaMatchEEFlowIT.importIntoAgent} so the
 * structural / contract harness ITs (§VI.6) can reuse the import logic
 * without depending on the customer-specific test class.
 *
 * <p>The flow JSON is expected to be the bundle shape produced by the
 * {@code /api/v2/chat-flow/{id}/export-bundle} endpoint — a top-level array
 * of {@code ExportEntry}-shaped objects with {@code personas}, {@code slots},
 * and {@code graph} embedded. See the harness fixtures under
 * {@code turing-app/src/test/resources/harness-it/}.
 *
 * <h2>Idempotence</h2>
 *
 * <ul>
 *   <li>Personas are looked up case-insensitively by name and upserted; an
 *       existing row keeps its id and gets the fresh export fields applied.</li>
 *   <li>Slots are upserted by {@code (agentId, name)} — re-imports don't
 *       duplicate.</li>
 *   <li>Persona graph nodes' {@code personaId} fields are rewritten from
 *       the export's authored id to the persisted persona's UUID so the
 *       runtime resolver finds them.</li>
 *   <li>Agent-persona join rows are added only when missing — supports
 *       importing multiple flows on the same agent (e.g. A/B variants)
 *       without unique-constraint violations.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public final class ChatFlowImportTestUtil {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ChatFlowImportTestUtil() {
        // utility class
    }

    /** A single flow entry from an export bundle (top-level array element). */
    public record ExportEntry(
            String id,
            String name,
            String description,
            String triggerDescription,
            String triggerMode,
            String guardrailMethod,
            String experimentKey,
            String variantLabel,
            Integer trafficWeight,
            List<Map<String, Object>> personas,
            List<Map<String, Object>> slots,
            Map<String, Object> graph) {}

    /**
     * The persisted artifacts. {@code persona} is the LAST persona created
     * by the import (matches the {@code defaultPersona} convention of
     * single-persona flows; A/B flows may produce more).
     */
    public record Imported(TurChatFlow flow, TurPersona persona) {}

    /**
     * Result of a {@link #importBundleIntoAgent} call. {@code flows} are the
     * persisted flows in bundle order; {@code byTransientId} maps each
     * bundle entry's transient {@code id} (the placeholder a
     * {@code subFlowId} references) to the persisted flow, so a test can
     * look up "which real flow did {@code chaos-sandwich} become".
     */
    public record ImportedBundle(
            List<TurChatFlow> flows,
            Map<String, TurChatFlow> byTransientId,
            List<TurPersona> personas) {}

    /**
     * Repositories the importer needs. Bundling them in a single record
     * keeps caller sites readable and lets {@link AbstractAgentExecutorIT}
     * publish a single {@code @Bean}-free factory method.
     */
    public record Repos(
            TurAIAgentRepository agents,
            TurChatFlowRepository flows,
            TurAIAgentSlotRepository slots,
            TurPersonaRepository personas) {}

    /**
     * Loads the bundle from the classpath resource and returns the entry
     * at {@code index}. Use index 0 for single-flow bundles.
     */
    public static ExportEntry loadFromClasspath(String resourcePath, int index) throws IOException {
        try (InputStream in = ChatFlowImportTestUtil.class.getResourceAsStream(resourcePath)) {
            Objects.requireNonNull(in, "Missing classpath resource: " + resourcePath);
            List<Map<String, Object>> bundle = JSON.readValue(in,
                    new TypeReference<List<Map<String, Object>>>() {});
            if (bundle.isEmpty()) {
                throw new IllegalStateException("Export bundle is empty: " + resourcePath);
            }
            if (index < 0 || index >= bundle.size()) {
                throw new IndexOutOfBoundsException(
                        "Bundle has " + bundle.size() + " entries; index " + index + " out of range");
            }
            return toEntry(bundle.get(index));
        }
    }

    /** Convenience: load the first entry (index 0). */
    public static ExportEntry loadFromClasspath(String resourcePath) throws IOException {
        return loadFromClasspath(resourcePath, 0);
    }

    /** Loads every entry of a multi-flow bundle from the classpath, in order. */
    public static List<ExportEntry> loadBundleFromClasspath(String resourcePath) throws IOException {
        try (InputStream in = ChatFlowImportTestUtil.class.getResourceAsStream(resourcePath)) {
            Objects.requireNonNull(in, "Missing classpath resource: " + resourcePath);
            List<Map<String, Object>> bundle = JSON.readValue(in,
                    new TypeReference<List<Map<String, Object>>>() {});
            if (bundle.isEmpty()) {
                throw new IllegalStateException("Export bundle is empty: " + resourcePath);
            }
            return bundle.stream().map(ChatFlowImportTestUtil::toEntry).toList();
        }
    }

    /**
     * Imports a single export entry into the given agent. Returns the
     * persisted flow and the last persona created (typically THE persona
     * for single-persona bundles).
     *
     * <p>The caller is expected to {@link TurAIAgentRepository#findById(Object)
     * re-fetch} the agent between imports of multiple entries — the lazy
     * persona collection may not reflect the previous import's flush,
     * causing duplicate inserts on the join table. See
     * {@code ProgramaMatchEEFlowIT.importIntoAgent} for the canonical
     * re-fetch dance.
     */
    public static Imported importIntoAgent(TurAIAgent agent, ExportEntry entry, Repos repos) {
        // 1) Personas — case-insensitive lookup, upsert
        Map<String, String> personaIdRemap = new HashMap<>();
        TurPersona resolvedPersona = null;
        if (entry.personas() != null) {
            for (Map<String, Object> p : entry.personas()) {
                String name = (String) p.get("name");
                TurPersona existing = repos.personas()
                        .findByNameIgnoreCase(name).orElse(null);
                TurPersona persona = upsertPersonaFromExport(existing, p, repos.personas());
                String originalId = (String) p.get("id");
                if (originalId != null && !originalId.isBlank()) {
                    personaIdRemap.put(originalId, persona.getId());
                }
                if (!agent.getPersonas().contains(persona)) {
                    agent.getPersonas().add(persona);
                }
                resolvedPersona = persona;
            }
        }
        repos.agents().save(agent);

        // 2) Slots — upsert per agent
        if (entry.slots() != null) {
            for (Map<String, Object> s : entry.slots()) {
                String name = (String) s.get("name");
                if (repos.slots().findByTurAIAgent_IdAndName(agent.getId(), name).isPresent()) {
                    continue;
                }
                TurAIAgentSlot slot = new TurAIAgentSlot();
                slot.setName(name);
                slot.setDescription((String) s.get("description"));
                slot.setType(parseSlotType((String) s.get("type")));
                slot.setTurAIAgent(agent);
                repos.slots().save(slot);
            }
        }

        // 3) Flow — definitionJson with personaIds rewritten
        Map<String, Object> rewrittenGraph = rewritePersonaIds(entry.graph(), personaIdRemap);
        TurChatFlow flow = new TurChatFlow();
        flow.setName(entry.name());
        flow.setDescription(entry.description());
        try {
            flow.setDefinitionJson(JSON.writeValueAsString(rewrittenGraph));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Cannot serialize rewritten graph: " + e.getMessage(), e);
        }
        flow.setEnabled(1);
        flow.setGuardrailMethod(entry.guardrailMethod() == null
                ? TurChatFlowGuardrailMethod.LLM_JUDGE
                : TurChatFlowGuardrailMethod.valueOf(entry.guardrailMethod()));
        flow.setTriggerDescription(entry.triggerDescription());
        flow.setTriggerMode(entry.triggerMode() == null
                ? TurChatFlowTriggerMode.ONCE
                : TurChatFlowTriggerMode.valueOf(entry.triggerMode()));
        flow.setExperimentKey(entry.experimentKey());
        flow.setVariantLabel(entry.variantLabel());
        flow.setTrafficWeight(entry.trafficWeight());
        flow.setTurAIAgent(agent);
        flow = repos.flows().save(flow);

        return new Imported(flow, resolvedPersona);
    }

    /**
     * Imports a whole multi-flow bundle (a main flow plus the sub-flows it
     * descends into) into {@code agent}, performing the two cross-flow
     * rewrites the engine needs at runtime — the same ones the production
     * {@code TurChatFlowAPI.turChatFlowImportBundle} endpoint performs, but
     * via direct repository writes:
     *
     * <ol>
     *   <li><b>Global persona remap.</b> Personas declared on ANY entry are
     *       upserted once (dedup by name) and a single
     *       {@code transientPersonaId → persistedUuid} map is applied to
     *       every flow's {@code personaId} graph fields — so a {@code persona}
     *       node in a sub-flow resolves even when only the main flow declares
     *       the persona.</li>
     *   <li><b>subFlowId remap.</b> Each entry's transient {@code id} is
     *       mapped to its persisted flow UUID, then every {@code subFlow}
     *       node's {@code subFlowId} and every {@code switchOptions[].subFlowId}
     *       (on {@code subFlowSwitch}) is rewritten to the real UUID. Without
     *       this the engine's {@code chatFlowRepository.findById(subFlowId)}
     *       descent silently fails (logs "not found" and advances).</li>
     * </ol>
     *
     * <p>Done in two passes: pass 1 persists every flow (graph carrying
     * remapped personaIds but still-transient subFlowIds) to learn each
     * flow's UUID; pass 2 rewrites the subFlowId references and re-saves.
     */
    public static ImportedBundle importBundleIntoAgent(TurAIAgent agent,
            List<ExportEntry> bundle, Repos repos) {
        // Pass 0 — upsert all personas across the bundle, build the global remap.
        Map<String, String> personaIdRemap = new HashMap<>();
        List<TurPersona> personas = new java.util.ArrayList<>();
        for (ExportEntry entry : bundle) {
            if (entry.personas() == null) {
                continue;
            }
            for (Map<String, Object> p : entry.personas()) {
                String name = (String) p.get("name");
                TurPersona existing = repos.personas().findByNameIgnoreCase(name).orElse(null);
                TurPersona persona = upsertPersonaFromExport(existing, p, repos.personas());
                String originalId = (String) p.get("id");
                if (originalId != null && !originalId.isBlank()) {
                    personaIdRemap.put(originalId, persona.getId());
                }
                if (!agent.getPersonas().contains(persona)) {
                    agent.getPersonas().add(persona);
                }
                personas.add(persona);
            }
        }
        repos.agents().save(agent);

        // Pass 1 — persist each flow (personaIds remapped, subFlowIds still transient).
        Map<String, TurChatFlow> byTransientId = new LinkedHashMap<>();
        List<TurChatFlow> flows = new java.util.ArrayList<>();
        for (ExportEntry entry : bundle) {
            upsertSlots(agent, entry, repos);
            Map<String, Object> rewrittenGraph = rewritePersonaIds(entry.graph(), personaIdRemap);
            TurChatFlow flow = new TurChatFlow();
            flow.setName(entry.name());
            flow.setDescription(entry.description());
            flow.setDefinitionJson(JSON.writeValueAsString(rewrittenGraph));
            flow.setEnabled(1);
            flow.setGuardrailMethod(entry.guardrailMethod() == null
                    ? TurChatFlowGuardrailMethod.LLM_JUDGE
                    : TurChatFlowGuardrailMethod.valueOf(entry.guardrailMethod()));
            flow.setTriggerDescription(entry.triggerDescription());
            flow.setTriggerMode(entry.triggerMode() == null
                    ? TurChatFlowTriggerMode.ONCE
                    : TurChatFlowTriggerMode.valueOf(entry.triggerMode()));
            flow.setTurAIAgent(agent);
            flow = repos.flows().save(flow);
            flows.add(flow);
            if (entry.id() != null && !entry.id().isBlank()) {
                byTransientId.put(entry.id(), flow);
            }
        }

        // Pass 2 — rewrite transient subFlowId references to persisted UUIDs.
        Map<String, String> subFlowIdRemap = new HashMap<>();
        byTransientId.forEach((transientId, flow) -> subFlowIdRemap.put(transientId, flow.getId()));
        for (TurChatFlow flow : flows) {
            String rewritten = rewriteSubFlowIds(flow.getDefinitionJson(), subFlowIdRemap);
            if (!rewritten.equals(flow.getDefinitionJson())) {
                flow.setDefinitionJson(rewritten);
                repos.flows().save(flow);
            }
        }

        return new ImportedBundle(flows, byTransientId, personas);
    }

    // ─────────────────────────── internals ───────────────────────────

    private static void upsertSlots(TurAIAgent agent, ExportEntry entry, Repos repos) {
        if (entry.slots() == null) {
            return;
        }
        for (Map<String, Object> s : entry.slots()) {
            String name = (String) s.get("name");
            if (repos.slots().findByTurAIAgent_IdAndName(agent.getId(), name).isPresent()) {
                continue;
            }
            TurAIAgentSlot slot = new TurAIAgentSlot();
            slot.setName(name);
            slot.setDescription((String) s.get("description"));
            slot.setType(parseSlotType((String) s.get("type")));
            slot.setTurAIAgent(agent);
            repos.slots().save(slot);
        }
    }

    /** Lenient slot-type parse — unknown/blank types fall back to STRING (matches the endpoint). */
    private static TurAIAgentSlotType parseSlotType(String type) {
        if (type == null || type.isBlank()) {
            return TurAIAgentSlotType.STRING;
        }
        try {
            return TurAIAgentSlotType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TurAIAgentSlotType.STRING;
        }
    }

    /**
     * Rewrites transient {@code subFlowId} references (on {@code subFlow}
     * nodes' {@code data.subFlowId} and on {@code switchOptions[].subFlowId})
     * to persisted flow UUIDs. Returns the re-serialized graph JSON.
     */
    @SuppressWarnings("unchecked")
    private static String rewriteSubFlowIds(String definitionJson, Map<String, String> remap) {
        if (definitionJson == null || remap.isEmpty()) {
            return definitionJson;
        }
        Map<String, Object> graph = JSON.readValue(definitionJson,
                new TypeReference<Map<String, Object>>() {});
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        if (nodes == null) {
            return definitionJson;
        }
        for (Map<String, Object> node : nodes) {
            Object dataObj = node.get("data");
            if (!(dataObj instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> data = (Map<String, Object>) dataObj;
            Object subFlowId = data.get("subFlowId");
            if (subFlowId instanceof String s && remap.containsKey(s)) {
                data.put("subFlowId", remap.get(s));
            }
            Object options = data.get("switchOptions");
            if (options instanceof List<?> list) {
                for (Object optObj : list) {
                    if (optObj instanceof Map<?, ?> optMap) {
                        Map<String, Object> opt = (Map<String, Object>) optMap;
                        Object optSub = opt.get("subFlowId");
                        if (optSub instanceof String s && remap.containsKey(s)) {
                            opt.put("subFlowId", remap.get(s));
                        }
                    }
                }
            }
        }
        return JSON.writeValueAsString(graph);
    }

    private static ExportEntry toEntry(Map<String, Object> raw) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> personas = (List<Map<String, Object>>) raw.get("personas");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> slots = (List<Map<String, Object>>) raw.get("slots");
        @SuppressWarnings("unchecked")
        Map<String, Object> graph = (Map<String, Object>) raw.get("graph");
        Object trafficWeightRaw = raw.get("trafficWeight");
        Integer trafficWeight = trafficWeightRaw instanceof Number n ? n.intValue() : null;
        return new ExportEntry(
                (String) raw.get("id"),
                (String) raw.get("name"),
                (String) raw.get("description"),
                (String) raw.get("triggerDescription"),
                (String) raw.get("triggerMode"),
                (String) raw.get("guardrailMethod"),
                (String) raw.get("experimentKey"),
                (String) raw.get("variantLabel"),
                trafficWeight,
                personas, slots, graph);
    }

    private static TurPersona upsertPersonaFromExport(TurPersona existing,
            Map<String, Object> p, TurPersonaRepository personaRepository) {
        TurPersona persona = existing != null ? existing : new TurPersona();
        persona.setName((String) p.get("name"));
        persona.setDescription((String) p.get("description"));
        persona.setSystemInstruction((String) p.get("systemInstruction"));
        String tone = (String) p.get("tone");
        if (tone != null && !tone.isBlank()) {
            persona.setTone(TurPersonaTone.valueOf(tone));
        }
        Object verbosity = p.get("verbosity");
        if (verbosity instanceof Number n) {
            persona.setVerbosity(n.intValue() == 0 ? 3 : n.intValue());
        }
        String style = (String) p.get("languageStyle");
        if (style != null && !style.isBlank()) {
            persona.setLanguageStyle(TurPersonaLanguageStyle.valueOf(style));
        }
        persona.setMandatoryTerms((String) p.get("mandatoryTerms"));
        persona.setForbiddenTerms((String) p.get("forbiddenTerms"));
        Object enabled = p.get("enabled");
        persona.setEnabled(enabled instanceof Number n && n.intValue() != 0 ? n.intValue() : 1);
        return personaRepository.save(persona);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> rewritePersonaIds(Map<String, Object> graph,
            Map<String, String> personaIdRemap) {
        if (graph == null || personaIdRemap.isEmpty()) {
            return graph;
        }
        Map<String, Object> copy = new LinkedHashMap<>(graph);
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) copy.get("nodes");
        if (nodes == null) {
            return copy;
        }
        for (Map<String, Object> node : nodes) {
            Object dataObj = node.get("data");
            if (!(dataObj instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> data = (Map<String, Object>) dataObj;
            Object pid = data.get("personaId");
            if (pid instanceof String s) {
                String mapped = personaIdRemap.get(s);
                if (mapped != null) {
                    data.put("personaId", mapped);
                }
            }
        }
        return copy;
    }
}
