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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.genai.selftuning.TurSelfTuningMiner;
import com.viglet.turing.persistence.dto.agent.TurChatSessionMessageDto;
import com.viglet.turing.persistence.model.agent.TurAgentEvalExpectedOutcome;
import com.viglet.turing.persistence.model.agent.TurChatFlowSubmission;
import com.viglet.turing.persistence.model.agent.TurEvalDataset;
import com.viglet.turing.persistence.repository.agent.TurChatFlowSubmissionRepository;
import com.viglet.turing.service.chatanalytics.TurChatAnalyticsStore;
import com.viglet.turing.service.chatanalytics.TurChatSessionFilter;
import com.viglet.turing.service.chatmemory.TurChatMemoryService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * T596 / §XXXIII.11 — the second half of "provide a dataset to be tested":
 * build an eval {@link TurEvalDataset} from the platform's own history instead
 * of an uploaded file. Two sources, both composing shipped subsystems:
 *
 * <ul>
 *   <li><b>transcripts</b> — recent conversations for an agent (T61 chat-memory
 *       transcript + T66 submission archive) become candidate golden rows: the
 *       user turns seed the case, the last assistant reply becomes the golden
 *       {@code referenceAnswer}, and the terminal submission supplies the
 *       expected outcome / node / captured slots;</li>
 *   <li><b>failures</b> — the conversations the T447 self-tuning miner flags as
 *       failing (abandoned / errored / handed-off / negative-sentiment / tool
 *       errors) become negative-example rows (no golden answer — the observed
 *       bad outcome is stashed in metadata for a human to curate; expectedOutcome
 *       stays {@code ANY} so the row asserts nothing until reviewed).</li>
 * </ul>
 *
 * <p>Both delegate persistence + canonical row mapping to
 * {@link TurEvalDatasetImportService#importCanonicalRows}, so a mined dataset is
 * byte-identical in shape to an uploaded one and round-trips through the same
 * export. Fail-open: when the chat-memory / analytics stores are disabled (the
 * NoOp default) there is nothing to mine and the caller gets a {@code 400 "No
 * rows"} — no partial or fabricated dataset.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurEvalDatasetSourceImportService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Max turns pulled per conversation transcript (clamped by the store). */
    private static final int TRANSCRIPT_LIMIT = 200;

    /** Analytics session look-back window for the transcript source. */
    private static final int TRANSCRIPT_WINDOW_DAYS = 30;

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String ABANDONED_MARKER = "__abandoned__";

    private final TurEvalDatasetImportService importService;
    private final TurChatMemoryService chatMemoryService;
    private final TurChatAnalyticsStore analyticsStore;
    private final TurSelfTuningMiner failureMiner;
    private final TurChatFlowSubmissionRepository submissionRepository;

    public TurEvalDatasetSourceImportService(TurEvalDatasetImportService importService,
            TurChatMemoryService chatMemoryService, TurChatAnalyticsStore analyticsStore,
            TurSelfTuningMiner failureMiner, TurChatFlowSubmissionRepository submissionRepository) {
        this.importService = importService;
        this.chatMemoryService = chatMemoryService;
        this.analyticsStore = analyticsStore;
        this.failureMiner = failureMiner;
        this.submissionRepository = submissionRepository;
    }

    /**
     * Builds a dataset from up to {@code limit} of the agent's most recent
     * conversations (last {@value #TRANSCRIPT_WINDOW_DAYS} days). Each becomes a
     * candidate golden row tagged {@code transcript}.
     */
    public TurEvalDataset importFromTranscripts(String agentId, String name, int limit) {
        requireAgent(agentId);
        List<Map<String, Object>> sessions = recentSessions(agentId, clampLimit(limit));
        List<ObjectNode> rows = buildRows(sessions, false);
        return importService.importCanonicalRows(datasetName(name, "transcripts", agentId), rows);
    }

    /**
     * Builds a dataset from the agent's mined failing conversations (T447),
     * capped at {@code limit}. Rows are tagged {@code mined-failure}.
     */
    public TurEvalDataset importFromFailures(String agentId, String name, int limit) {
        requireAgent(agentId);
        List<Map<String, Object>> sessions = failureMiner.findFailingSessions(agentId);
        int cap = clampLimit(limit);
        if (sessions.size() > cap) {
            sessions = sessions.subList(0, cap);
        }
        List<ObjectNode> rows = buildRows(sessions, true);
        return importService.importCanonicalRows(datasetName(name, "failures", agentId), rows);
    }

    // ─────────────────────────── row building ───────────────────────────

    private List<ObjectNode> buildRows(List<Map<String, Object>> sessions, boolean failure) {
        List<ObjectNode> rows = new ArrayList<>();
        for (Map<String, Object> session : sessions) {
            String conversationId = str(session.get("conversationId"));
            if (conversationId == null || conversationId.isBlank()) {
                continue;
            }
            ObjectNode row = buildRow(conversationId, session, failure);
            if (row != null) {
                rows.add(row);
            }
        }
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No rows to import — chat-memory/analytics stores may be disabled or empty");
        }
        return rows;
    }

    /** One canonical eval row from a conversation, or {@code null} to skip it. */
    private ObjectNode buildRow(String conversationId, Map<String, Object> session, boolean failure) {
        List<TurChatSessionMessageDto> messages =
                chatMemoryService.listMessages(conversationId, TRANSCRIPT_LIMIT).messages();

        ArrayNode turns = OBJECT_MAPPER.createArrayNode();
        String lastAssistant = null;
        for (TurChatSessionMessageDto message : messages) {
            String content = message.content();
            if (content == null || content.isBlank()) {
                continue;
            }
            if (ROLE_USER.equalsIgnoreCase(message.role())) {
                turns.add(content);
            } else if (ROLE_ASSISTANT.equalsIgnoreCase(message.role())) {
                lastAssistant = content;
            }
        }
        if (turns.isEmpty()) {
            // Nothing to replay — skip conversations with no captured user turn.
            return null;
        }

        ObjectNode row = OBJECT_MAPPER.createObjectNode();
        row.put("name", conversationId);
        row.set("turns", turns);

        TurChatFlowSubmission submission = latestSubmission(conversationId);
        // A mined failure is a negative example: no golden answer, assert nothing
        // (expectedOutcome stays ANY) until a human curates it.
        if (!failure) {
            if (lastAssistant != null) {
                row.put("referenceAnswer", lastAssistant);
            }
            row.put("expectedOutcome", expectedOutcome(submission, session).name());
            if (submission != null && !ABANDONED_MARKER.equals(submission.getEndNodeId())) {
                row.put("expectedNodeId", submission.getEndNodeId());
            }
        }
        if (submission != null && submission.getVariablesJson() != null
                && !submission.getVariablesJson().isBlank()) {
            try {
                row.set("expectedSlots", OBJECT_MAPPER.readTree(submission.getVariablesJson()));
            } catch (RuntimeException e) {
                log.debug("[EvalDataset] skipping unparseable slots for conv={}: {}",
                        conversationId, e.getMessage());
            }
        }

        ArrayNode tags = OBJECT_MAPPER.createArrayNode();
        tags.add(failure ? "mined-failure" : "transcript");
        row.set("tags", tags);

        ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
        metadata.put("source", failure ? "failures" : "transcripts");
        metadata.put("conversationId", conversationId);
        putIfPresent(metadata, "observedOutcome", session.get("outcome"));
        putIfPresent(metadata, "sentiment", session.get("sentiment"));
        row.set("metadata", metadata);

        return row;
    }

    private TurChatFlowSubmission latestSubmission(String conversationId) {
        List<TurChatFlowSubmission> submissions =
                submissionRepository.findByConversationIdOrderByCompletedAtDesc(conversationId);
        return submissions.isEmpty() ? null : submissions.getFirst();
    }

    /**
     * Prefers the terminal submission's node ({@code __abandoned__} → ABANDONED,
     * else CAPTURED); falls back to the analytics session outcome; defaults ANY.
     */
    private static TurAgentEvalExpectedOutcome expectedOutcome(TurChatFlowSubmission submission,
            Map<String, Object> session) {
        if (submission != null) {
            return ABANDONED_MARKER.equals(submission.getEndNodeId())
                    ? TurAgentEvalExpectedOutcome.ABANDONED
                    : TurAgentEvalExpectedOutcome.CAPTURED;
        }
        return switch (str(session.get("outcome"))) {
            case "COMPLETED" -> TurAgentEvalExpectedOutcome.CAPTURED;
            case "ABANDONED" -> TurAgentEvalExpectedOutcome.ABANDONED;
            case "HANDOFF" -> TurAgentEvalExpectedOutcome.HANDOFF;
            case null, default -> TurAgentEvalExpectedOutcome.ANY;
        };
    }

    // ─────────────────────────── helpers ───────────────────────────

    private List<Map<String, Object>> recentSessions(String agentId, int limit) {
        Instant now = Instant.now();
        Instant from = now.minus(Duration.ofDays(TRANSCRIPT_WINDOW_DAYS));
        try {
            return analyticsStore.findRecentSessions(from, now,
                    new TurChatSessionFilter(agentId, null, null, null, null, null), limit);
        } catch (RuntimeException e) {
            log.warn("[EvalDataset] transcript source read failed for agent={}: {}", agentId,
                    e.getMessage());
            return List.of();
        }
    }

    private static void requireAgent(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "agentId is required");
        }
    }

    private static int clampLimit(int limit) {
        return Math.clamp(limit, 1, 500);
    }

    private static String datasetName(String name, String source, String agentId) {
        if (name != null && !name.isBlank()) {
            return name;
        }
        return "%s-%s".formatted(source, agentId);
    }

    private static void putIfPresent(ObjectNode node, String key, Object value) {
        String text = str(value);
        if (text != null && !text.isBlank()) {
            node.put(key, text);
        }
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
