/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.persistence.dto.agent.TurEvalDatasetDto;
import com.viglet.turing.persistence.dto.agent.TurEvalDatasetRowDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAIAgentSlot;
import com.viglet.turing.persistence.model.agent.TurAgentEvalExpectedOutcome;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurEvalDataset;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurAIAgentSlotRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRowRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * T597 / §XXXIII.12 — LLM-assisted eval-dataset generation &amp; augmentation.
 * Two operations, both reusing the T387 manifest-derivation posture (ground the
 * model on real artifacts, parse conservatively, fail-open, and <b>never
 * auto-trust</b> the output):
 *
 * <ul>
 *   <li><b>generate</b> — synthesizes candidate golden rows for an agent by
 *       grounding the model on its system prompt, declared slots, and chat-flow
 *       goals. Each candidate is a scripted user-turn sequence + an expected
 *       outcome / node / slots + a rubric.</li>
 *   <li><b>augment</b> — paraphrases the seed turns of an existing dataset's rows
 *       into fresh variants that keep the same expectations (a cheap way to widen
 *       coverage of phrasings the agent must survive).</li>
 * </ul>
 *
 * <p>Neither operation persists anything: both return <b>draft</b>
 * {@link TurEvalDatasetRowDto}s (no id, tagged {@code generated} /
 * {@code augmented}) that the caller renders in a <em>review form</em>. Only
 * {@link #saveReviewed} — invoked after a human edits/approves — writes, and it
 * routes through the same canonical-row persistence as the file / transcript
 * importers ({@link TurEvalDatasetImportService}), so a generated row is
 * shape-identical to an uploaded or mined one.
 *
 * <p>Fail-open: with no usable LLM (no agent/default instance, or the call
 * fails/garbles) generation returns an empty list — the review form simply shows
 * nothing to approve, and a legacy install with no LLM is unaffected.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurEvalDatasetGenerationService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Upper bound on rows synthesized per generate call (protects the LLM budget). */
    private static final int MAX_GENERATE = 25;
    /** Upper bound on paraphrase variants produced per source row. */
    private static final int MAX_VARIANTS = 5;
    /** Flow nodes shown to the model as goal context (keeps the prompt bounded). */
    private static final int MAX_FLOW_NODES = 40;

    private final TurAIAgentRepository agentRepository;
    private final TurAIAgentSlotRepository slotRepository;
    private final TurChatFlowRepository chatFlowRepository;
    private final TurChatFlowEngineService chatFlowEngineService;
    private final TurEvalDatasetImportService importService;
    private final TurEvalDatasetRowRepository datasetRowRepository;
    private final TurGlobalSettingsService globalSettingsService;
    private final com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository llmInstanceRepository;
    private final TurLlmModelFactory llmModelFactory;
    private final TurSecretCryptoService secretCryptoService;

    public TurEvalDatasetGenerationService(TurAIAgentRepository agentRepository,
            TurAIAgentSlotRepository slotRepository, TurChatFlowRepository chatFlowRepository,
            TurChatFlowEngineService chatFlowEngineService, TurEvalDatasetImportService importService,
            TurEvalDatasetRowRepository datasetRowRepository,
            TurGlobalSettingsService globalSettingsService,
            com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository llmInstanceRepository,
            TurLlmModelFactory llmModelFactory, TurSecretCryptoService secretCryptoService) {
        this.agentRepository = agentRepository;
        this.slotRepository = slotRepository;
        this.chatFlowRepository = chatFlowRepository;
        this.chatFlowEngineService = chatFlowEngineService;
        this.importService = importService;
        this.datasetRowRepository = datasetRowRepository;
        this.globalSettingsService = globalSettingsService;
        this.llmInstanceRepository = llmInstanceRepository;
        this.llmModelFactory = llmModelFactory;
        this.secretCryptoService = secretCryptoService;
    }

    // ─────────────────────────── generate ───────────────────────────

    /**
     * Synthesizes up to {@code count} candidate golden rows for {@code agentId},
     * grounded on its system prompt / slots / flow goals. Draft only — nothing
     * persisted. Returns an empty list when no LLM is usable.
     */
    public List<TurEvalDatasetRowDto> generateFromAgent(String agentId, int count) {
        TurAIAgent agent = agentRepository.findById(requireId(agentId, "agentId"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Agent not found: " + agentId));
        int target = Math.clamp(count, 1, MAX_GENERATE);
        TurLLMInstance llm = resolveLlm(agent);
        if (llm == null) {
            log.info("[T597] no usable LLM for agent {} — nothing to generate", agentId);
            return List.of();
        }
        String context = agentContext(agent);
        List<GeneratedRow> generated = callForRows(llm,
                GENERATE_SYSTEM_PROMPT, generateUserPrompt(context, target), "[T597] generate");
        List<TurEvalDatasetRowDto> drafts = new ArrayList<>();
        int order = 0;
        for (GeneratedRow g : generated) {
            List<String> turns = cleanTurns(g.turns());
            if (turns.isEmpty()) {
                continue; // never emit a row with nothing to replay
            }
            drafts.add(draft(order++, g.name(), turns, g.expectedOutcome(), g.expectedNodeId(),
                    g.expectedSlots(), g.rubric(), null, "generated",
                    provenance("llm-generated", agentId, null)));
            if (drafts.size() >= target) {
                break;
            }
        }
        return drafts;
    }

    // ─────────────────────────── augment ───────────────────────────

    /**
     * Paraphrases the seed turns of every row in {@code datasetId} into up to
     * {@code variantsPerRow} fresh variants that keep the original expectations.
     * Draft only — nothing persisted. Empty when no LLM is usable.
     */
    public List<TurEvalDatasetRowDto> augmentDataset(String datasetId, int variantsPerRow) {
        var rows = datasetRowRepository.findByTurEvalDataset_IdOrderBySortOrderAsc(
                requireId(datasetId, "datasetId"));
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dataset has no rows to augment");
        }
        int variants = Math.clamp(variantsPerRow, 1, MAX_VARIANTS);
        TurLLMInstance llm = resolveLlm(null);
        if (llm == null) {
            log.info("[T597] no default LLM — nothing to augment for dataset {}", datasetId);
            return List.of();
        }
        List<TurEvalDatasetRowDto> drafts = new ArrayList<>();
        int order = 0;
        for (var source : rows) {
            List<String> seedTurns = parseTurns(source.getSeedTurnsJson());
            if (seedTurns.isEmpty()) {
                continue;
            }
            List<GeneratedRow> paraphrased = callForRows(llm, AUGMENT_SYSTEM_PROMPT,
                    augmentUserPrompt(seedTurns, variants), "[T597] augment");
            int produced = 0;
            for (GeneratedRow p : paraphrased) {
                List<String> turns = cleanTurns(p.turns());
                if (turns.isEmpty() || turns.equals(seedTurns)) {
                    continue; // skip empty or verbatim-echoed paraphrases
                }
                drafts.add(draft(order++, variantName(source.getName()), turns,
                        outcomeName(source.getExpectedOutcome()), source.getExpectedNodeId(),
                        parseObject(source.getExpectedSlotsJson()), source.getRubric(),
                        source.getReferenceAnswer(), "augmented",
                        provenance("llm-augmented", null, source.getId())));
                if (++produced >= variants) {
                    break;
                }
            }
        }
        return drafts;
    }

    // ─────────────────────────── save (post-review) ───────────────────────────

    /**
     * Persists the human-reviewed draft rows: appends to {@code datasetId} when
     * given, else creates a new dataset named {@code name}. The rows are whatever
     * the reviewer approved/edited in the form — this is the only write path.
     */
    public TurEvalDatasetDto saveReviewed(String name, String datasetId,
            List<TurEvalDatasetRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No rows to save");
        }
        List<ObjectNode> canonical = rows.stream().map(this::toCanonical).toList();
        TurEvalDataset dataset = (datasetId != null && !datasetId.isBlank())
                ? importService.appendCanonicalRows(datasetId, canonical)
                : importService.importCanonicalRows(
                        name == null || name.isBlank() ? "generated-dataset" : name, canonical);
        return importService.get(dataset.getId());
    }

    // ─────────────────────────── LLM call + parsing ───────────────────────────

    /** Calls the model and parses its reply as a JSON array of {@link GeneratedRow}. */
    private List<GeneratedRow> callForRows(TurLLMInstance llm, String system, String user,
            String logTag) {
        try {
            ChatModel chatModel = createChatModel(llm);
            List<Message> messages = List.of(new SystemMessage(system), new UserMessage(user));
            String reply = chatModel.call(new Prompt(messages)).getResult().getOutput().getText();
            return parseRows(reply, logTag);
        } catch (RuntimeException e) {
            log.warn("{} LLM call failed, returning no drafts: {}", logTag, e.getMessage());
            return List.of();
        }
    }

    private List<GeneratedRow> parseRows(String reply, String logTag) {
        if (!StringUtils.hasText(reply)) {
            return List.of();
        }
        try {
            GeneratedRow[] parsed = OBJECT_MAPPER.readValue(extractJsonArray(reply), GeneratedRow[].class);
            return parsed == null ? List.of() : List.of(parsed);
        } catch (RuntimeException e) {
            log.warn("{} could not parse LLM reply as a JSON array: {}", logTag, e.getMessage());
            return List.of();
        }
    }

    /** Strips prose/fences around the JSON array the model should return. */
    static String extractJsonArray(String reply) {
        int start = reply.indexOf('[');
        int end = reply.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return reply.substring(start, end + 1);
        }
        return reply.trim();
    }

    /** The model's candidate row; every field is optional and validated downstream. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeneratedRow(
            String name,
            List<String> turns,
            String expectedOutcome,
            String expectedNodeId,
            JsonNode expectedSlots,
            String rubric) {
    }

    // ─────────────────────────── prompts ───────────────────────────

    private static final String GENERATE_SYSTEM_PROMPT = """
            You write evaluation test cases for a conversational AI agent. Given the \
            agent's SYSTEM PROMPT, its declared SLOTS, and its chat-flow GOALS, invent \
            realistic user conversations that exercise the agent, and state what the \
            agent is expected to do. Output ONLY a JSON array — no prose, no markdown \
            fences.

            Each array element is an object:
              {"name": "<short case name>", "turns": ["<user msg 1>", "<user msg 2>"], \
            "expectedOutcome": "<OUTCOME>", "expectedNodeId": "<node id or omit>", \
            "expectedSlots": {"<slot>": "<expected value>"}, \
            "rubric": "<one natural-language assertion about the agent's reply>"}

            expectedOutcome must be one of: ANY, CAPTURED, ABANDONED, HANDOFF.

            HARD RULES (conservative grounding):
            - "turns" holds ONLY the user's messages, in order — never the agent's replies.
            - Only reference slot names that appear in SLOTS; never invent a slot.
            - Only reference a node id that appears in GOALS; otherwise omit expectedNodeId.
            - Prefer expectedOutcome=CAPTURED for happy-path goal-completion cases, \
              ABANDONED / HANDOFF for drop-off / escalation cases, ANY when unsure.
            - Cover a spread: happy path, missing/ambiguous input, off-topic, and edge \
              phrasings. Keep each case short (1-4 turns) and in the agent's language.
            - The rubric is a single checkable statement; omit it if you cannot ground one.""";

    private static final String AUGMENT_SYSTEM_PROMPT = """
            You paraphrase a user's side of a conversation to widen test coverage. \
            Given an ordered list of the user's TURNS, produce alternative phrasings \
            that a different user might use to say the SAME things in the SAME order — \
            same intent, same information, same number of turns. Output ONLY a JSON \
            array — no prose, no markdown fences.

            Each array element is one paraphrased variant:
              {"turns": ["<paraphrased user msg 1>", "<paraphrased user msg 2>"]}

            HARD RULES:
            - Preserve meaning and any concrete values (names, numbers, dates, IDs) \
              exactly — only vary the wording/register.
            - Keep the same number of turns and the same language as the input.
            - Do not add, drop, merge, or reorder turns; do not answer as the agent.
            - Make the variants genuinely different from the input and from each other.""";

    private String generateUserPrompt(String agentContext, int count) {
        return "AGENT CONTEXT:\n" + agentContext + "\n\nGenerate " + count
                + " distinct evaluation cases as a JSON array.";
    }

    private String augmentUserPrompt(List<String> turns, int variants) {
        StringBuilder sb = new StringBuilder("USER TURNS (in order):\n");
        for (int i = 0; i < turns.size(); i++) {
            sb.append(i + 1).append(". ").append(turns.get(i)).append('\n');
        }
        sb.append("\nProduce ").append(variants)
                .append(" paraphrased variants as a JSON array of {\"turns\":[...]} objects.");
        return sb.toString();
    }

    /** Renders the agent's grounding artifacts (prompt + slots + flow goals). */
    private String agentContext(TurAIAgent agent) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(agent.getTitle())) {
            sb.append("Title: ").append(agent.getTitle()).append('\n');
        }
        if (StringUtils.hasText(agent.getDescription())) {
            sb.append("Description: ").append(agent.getDescription()).append('\n');
        }
        if (StringUtils.hasText(agent.getSystemPrompt())) {
            sb.append("\nSYSTEM PROMPT:\n").append(agent.getSystemPrompt().trim()).append('\n');
        }
        appendSlots(sb, agent.getId());
        appendFlowGoals(sb, agent.getId());
        return sb.length() == 0 ? "(no additional context)" : sb.toString();
    }

    private void appendSlots(StringBuilder sb, String agentId) {
        List<TurAIAgentSlot> slots = slotRepository.findByTurAIAgent_IdOrderByNameAsc(agentId);
        if (slots.isEmpty()) {
            return;
        }
        sb.append("\nSLOTS (name | type | description):\n");
        for (TurAIAgentSlot slot : slots) {
            sb.append("- ").append(slot.getName())
                    .append(" | ").append(slot.getType() == null ? "STRING" : slot.getType().name())
                    .append(" | ").append(slot.getDescription() == null ? "" : slot.getDescription())
                    .append('\n');
        }
    }

    private void appendFlowGoals(StringBuilder sb, String agentId) {
        List<TurChatFlow> flows = chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(agentId);
        boolean header = false;
        int shown = 0;
        for (TurChatFlow flow : flows) {
            Optional<ChatFlowGraph> graph = chatFlowEngineService.parseGraph(flow);
            if (graph.isEmpty()) {
                continue;
            }
            for (ChatFlowNode node : graph.get().nodes()) {
                if (shown >= MAX_FLOW_NODES) {
                    break;
                }
                String goal = firstNonBlank(node.aiInstruction(), node.label());
                if (!StringUtils.hasText(goal)) {
                    continue;
                }
                if (!header) {
                    sb.append("\nGOALS (flow nodes — id | type | goal):\n");
                    header = true;
                }
                sb.append("- ").append(node.id()).append(" | ").append(node.type())
                        .append(" | ").append(goal.trim().replace('\n', ' ')).append('\n');
                shown++;
            }
        }
    }

    // ─────────────────────────── draft / canonical mapping ───────────────────────────

    /** Builds a draft (unsaved) row DTO. {@code id} is null so the UI knows it is new. */
    private TurEvalDatasetRowDto draft(int order, String name, List<String> turns,
            String expectedOutcome, String expectedNodeId, JsonNode expectedSlots, String rubric,
            String referenceAnswer, String baseTag, ObjectNode metadata) {
        ArrayNode turnsNode = OBJECT_MAPPER.createArrayNode();
        turns.forEach(turnsNode::add);
        String slotsJson = expectedSlots != null && expectedSlots.isObject()
                ? expectedSlots.toString() : null;
        return new TurEvalDatasetRowDto(null, name, turnsNode.toString(), slotsJson,
                normalizeOutcome(expectedOutcome), blankToNull(expectedNodeId), blankToNull(rubric),
                blankToNull(referenceAnswer), baseTag, metadata.toString(), order);
    }

    /** DTO (from the review form) -> the canonical node the importer's buildRow reads. */
    private ObjectNode toCanonical(TurEvalDatasetRowDto dto) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        if (dto.name() != null) {
            node.put("name", dto.name());
        }
        node.set("turns", parseTurnsArray(dto.seedTurnsJson()));
        JsonNode slots = parseObject(dto.expectedSlotsJson());
        if (slots != null && slots.isObject()) {
            node.set("expectedSlots", slots);
        }
        node.put("expectedOutcome", normalizeOutcome(dto.expectedOutcome()));
        if (dto.expectedNodeId() != null) {
            node.put("expectedNodeId", dto.expectedNodeId());
        }
        if (dto.rubric() != null) {
            node.put("rubric", dto.rubric());
        }
        if (dto.referenceAnswer() != null) {
            node.put("referenceAnswer", dto.referenceAnswer());
        }
        if (dto.tags() != null) {
            node.put("tags", dto.tags());
        }
        JsonNode metadata = parseObject(dto.metadataJson());
        if (metadata != null && metadata.isObject()) {
            node.set("metadata", metadata);
        }
        return node;
    }

    // ─────────────────────────── helpers ───────────────────────────

    private ObjectNode provenance(String source, String agentId, String sourceRowId) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("source", source);
        if (agentId != null) {
            node.put("agentId", agentId);
        }
        if (sourceRowId != null) {
            node.put("sourceRowId", sourceRowId);
        }
        return node;
    }

    private static List<String> cleanTurns(List<String> turns) {
        if (turns == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String turn : turns) {
            if (turn != null && !turn.isBlank()) {
                out.add(turn.trim());
            }
        }
        return out;
    }

    private List<String> parseTurns(String seedTurnsJson) {
        ArrayNode array = parseTurnsArray(seedTurnsJson);
        List<String> turns = new ArrayList<>();
        for (JsonNode node : array) {
            if (!node.isNull()) {
                turns.add(node.asString());
            }
        }
        return turns;
    }

    private ArrayNode parseTurnsArray(String seedTurnsJson) {
        if (!StringUtils.hasText(seedTurnsJson)) {
            return OBJECT_MAPPER.createArrayNode();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(seedTurnsJson);
            return node.isArray() ? (ArrayNode) node : OBJECT_MAPPER.createArrayNode();
        } catch (RuntimeException e) {
            return OBJECT_MAPPER.createArrayNode();
        }
    }

    private JsonNode parseObject(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            return node.isObject() ? node : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String normalizeOutcome(String value) {
        if (!StringUtils.hasText(value)) {
            return TurAgentEvalExpectedOutcome.ANY.name();
        }
        try {
            return TurAgentEvalExpectedOutcome.valueOf(value.trim().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            return TurAgentEvalExpectedOutcome.ANY.name();
        }
    }

    private static String outcomeName(TurAgentEvalExpectedOutcome outcome) {
        return outcome == null ? TurAgentEvalExpectedOutcome.ANY.name() : outcome.name();
    }

    private static String variantName(String sourceName) {
        return sourceName == null || sourceName.isBlank() ? "variant" : sourceName + " (variant)";
    }

    private static String firstNonBlank(String a, String b) {
        return StringUtils.hasText(a) ? a : b;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String requireId(String id, String label) {
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " is required");
        }
        return id;
    }

    // ─────────────────────────── LLM resolution (mirrors the eval runner) ───────────────────────────

    private TurLLMInstance resolveLlm(TurAIAgent agent) {
        if (agent != null && agent.getLlmInstances() != null) {
            Optional<TurLLMInstance> agentLlm = agent.getLlmInstances().stream()
                    .filter(l -> l.getEnabled() == 1).findFirst();
            if (agentLlm.isPresent()) {
                return agentLlm.get();
            }
        }
        String defaultLlmId = globalSettingsService.getDefaultLlmId();
        if (defaultLlmId == null || defaultLlmId.isBlank()) {
            return null;
        }
        return llmInstanceRepository.findById(defaultLlmId)
                .filter(l -> l.getEnabled() == 1).orElse(null);
    }

    private ChatModel createChatModel(TurLLMInstance llmInstance) {
        String apiKey = secretCryptoService.decrypt(llmInstance.getApiKeyEncrypted());
        return llmModelFactory.createChatModel(llmInstance, apiKey);
    }
}
