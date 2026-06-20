/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.genai.flow.ChatFlowNode.HumanApprovalConfig;
import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.persistence.dto.agent.TurHumanApprovalDto;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.model.agent.TurChatHumanApproval;
import com.viglet.turing.persistence.model.agent.TurHumanApprovalStatus;
import com.viglet.turing.persistence.repository.agent.TurChatHumanApprovalRepository;
import com.viglet.turing.properties.TurConfigProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * T119 / §IX.5.a — business logic for the {@code humanApproval} node, kept free
 * of any dependency on {@link TurChatFlowEngineService} so the engine can call
 * back into it (via {@link TurHumanApprovalNodeExecutor}) without a circular
 * bean graph. Resuming the parked flow is the caller's job (the approval
 * controller and the timeout sweep both invoke
 * {@link TurChatFlowEngineService#resumeSuspendedFlow} after a decision is
 * recorded here).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurHumanApprovalService {

    // --- S1192: extracted duplicated literals ---
    private static final String AUTO_APPROVE = "auto_approve";


    /** Slot the decision lands in when the node names none and has no outputVariable. */
    public static final String DEFAULT_APPROVAL_SLOT = "operator_decision";
    /** Decision written when an {@code auto_reject} timeout fires (default behavior). */
    public static final String DECISION_REJECT = "reject";
    /** Decision written when an {@code auto_approve} timeout fires. */
    public static final String DECISION_APPROVE = "approve";

    private final TurChatHumanApprovalRepository repository;
    private final TurHumanApprovalNotifier notifier;
    private final TurConfigProperties configProperties;

    public TurHumanApprovalService(TurChatHumanApprovalRepository repository,
            TurHumanApprovalNotifier notifier,
            TurConfigProperties configProperties) {
        this.repository = repository;
        this.notifier = notifier;
        this.configProperties = configProperties;
    }

    /**
     * The slot a node's decision is written into: the explicit
     * {@code approvalSlot}, falling back to the node's {@code outputVariable},
     * then to {@link #DEFAULT_APPROVAL_SLOT}.
     */
    public static String effectiveApprovalSlot(ChatFlowNode node) {
        HumanApprovalConfig config = node == null ? null : node.humanApproval();
        if (config != null && config.approvalSlot() != null && !config.approvalSlot().isBlank()) {
            return config.approvalSlot().trim();
        }
        if (node != null && node.outputVariable() != null && !node.outputVariable().isBlank()) {
            return node.outputVariable().trim();
        }
        return DEFAULT_APPROVAL_SLOT;
    }

    /**
     * Idempotently raises the approval for {@code node} on {@code state}: when
     * no {@code PENDING} record exists for this conversation+node, one is
     * persisted and a notification fired; a re-entry (the conversation parked
     * here and the user sent another turn) is a no-op so the notification fires
     * exactly once.
     */
    @Transactional
    public void ensurePending(TurChatFlowState state, ChatFlowNode node) {
        if (state == null || node == null) {
            return;
        }
        HumanApprovalConfig config = node.humanApproval();
        if (config == null) {
            log.warn("[HumanApproval] node '{}' has no humanApproval config — parking without notification",
                    node.id());
            return;
        }
        String conversationId = state.getConversationId();
        Optional<TurChatHumanApproval> existing = repository
                .findFirstByConversationIdAndNodeIdAndStatus(
                        conversationId, node.id(), TurHumanApprovalStatus.PENDING);
        if (existing.isPresent()) {
            return;
        }

        Map<String, String> slots = ChatFlowOps.readVariables(state);
        String prompt = renderPrompt(config.template(), slots);
        String token = UUID.randomUUID().toString().replace("-", "");
        int timeoutSeconds = resolveTimeoutSeconds(config);

        TurChatHumanApproval approval = new TurChatHumanApproval();
        approval.setResumeToken(token);
        approval.setConversationId(conversationId);
        approval.setNodeId(node.id());
        approval.setFlowId(state.getFlow() == null ? null : state.getFlow().getId());
        approval.setApprovalSlot(effectiveApprovalSlot(node));
        approval.setChannel(config.channel());
        approval.setTarget(config.target());
        approval.setPromptText(prompt);
        approval.setStatus(TurHumanApprovalStatus.PENDING);
        approval.setTimeoutBehavior(normalizeTimeoutBehavior(config.timeoutBehavior()));
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        approval.setCreatedAt(now);
        approval.setExpiresAt(timeoutSeconds > 0 ? now.plusSeconds(timeoutSeconds) : null);
        repository.save(approval);

        log.info("[HumanApproval] raised approval token='{}' conv='{}' node='{}' channel='{}'",
                token, conversationId, node.id(), config.channel());
        notifier.notify(config.channel(), config.target(), prompt, buildResumeUrl(token),
                conversationId, slots);
    }

    /**
     * Records an operator decision for the approval identified by {@code token}.
     * Idempotent: a token that is unknown returns empty; one that is already
     * resolved is returned unchanged (the caller must not re-resume it).
     */
    @Transactional
    public Optional<TurChatHumanApproval> recordDecision(String token, String decision) {
        Optional<TurChatHumanApproval> opt = repository.findByResumeToken(token);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        TurChatHumanApproval approval = opt.get();
        if (approval.getStatus() != TurHumanApprovalStatus.PENDING) {
            return opt;
        }
        approval.setStatus(TurHumanApprovalStatus.DECIDED);
        approval.setDecision(decision);
        approval.setDecidedAt(LocalDateTime.now(ZoneId.systemDefault()));
        return Optional.of(repository.save(approval));
    }

    /** Pending approvals whose deadline elapsed — fed to the timeout sweep. */
    @Transactional(readOnly = true)
    public List<TurChatHumanApproval> findExpired() {
        return repository.findByStatusAndExpiresAtBefore(
                TurHumanApprovalStatus.PENDING, LocalDateTime.now(ZoneId.systemDefault()));
    }

    /**
     * Marks an expired approval {@code TIMED_OUT}, deriving the decision from
     * its {@code timeoutBehavior}. Returns the decision value the caller writes
     * into the approval slot before resuming the flow.
     */
    @Transactional
    public String markTimedOut(TurChatHumanApproval approval) {
        String decision = AUTO_APPROVE.equals(approval.getTimeoutBehavior())
                ? DECISION_APPROVE : DECISION_REJECT;
        approval.setStatus(TurHumanApprovalStatus.TIMED_OUT);
        approval.setDecision(decision);
        approval.setDecidedAt(LocalDateTime.now(ZoneId.systemDefault()));
        repository.save(approval);
        return decision;
    }

    /**
     * Marks the PENDING record for a conversation+node {@code CANCELLED} —
     * called when an admin force-advances the conversation past the node
     * without a real decision. No-op when nothing is pending.
     */
    @Transactional
    public void cancelPending(String conversationId, String nodeId) {
        repository.findFirstByConversationIdAndNodeIdAndStatus(
                conversationId, nodeId, TurHumanApprovalStatus.PENDING)
                .ifPresent(approval -> {
                    approval.setStatus(TurHumanApprovalStatus.CANCELLED);
                    approval.setDecidedAt(LocalDateTime.now(ZoneId.systemDefault()));
                    repository.save(approval);
                });
    }

    @Transactional(readOnly = true)
    public Optional<TurChatHumanApproval> findByToken(String token) {
        return token == null || token.isBlank() ? Optional.empty() : repository.findByResumeToken(token);
    }

    @Transactional(readOnly = true)
    public List<TurHumanApprovalDto> listPending() {
        return repository.findByStatusOrderByCreatedAtDesc(TurHumanApprovalStatus.PENDING)
                .stream().map(TurHumanApprovalDto::of).toList();
    }

    private String renderPrompt(String template, Map<String, String> slots) {
        if (template == null || template.isBlank()) {
            return "A conversation is awaiting your approval.";
        }
        return ChatFlowOps.interpolateVariables(template, slots);
    }

    private int resolveTimeoutSeconds(HumanApprovalConfig config) {
        Integer nodeValue = config.timeoutSeconds();
        if (nodeValue != null && nodeValue > 0) {
            return nodeValue;
        }
        if (nodeValue != null && nodeValue == 0) {
            return 0;
        }
        // Node left it unset (null) → fall back to the instance default.
        return Math.max(0, configProperties.getGenai().getHumanApproval().getDefaultTimeoutSeconds());
    }

    private static String normalizeTimeoutBehavior(String behavior) {
        String trimmed = behavior == null ? null : behavior.trim();
        return AUTO_APPROVE.equalsIgnoreCase(trimmed) ? AUTO_APPROVE : "auto_reject";
    }

    private String buildResumeUrl(String token) {
        String baseUrl = configProperties.getGenai().getHumanApproval().getBaseUrl();
        String path = "/api/genai/approval/" + token;
        if (baseUrl == null || baseUrl.isBlank()) {
            return path;
        }
        String trimmed = baseUrl.trim();
        return (trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed) + path;
    }
}
