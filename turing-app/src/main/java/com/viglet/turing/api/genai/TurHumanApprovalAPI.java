/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.genai;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.flow.TurHumanApprovalService;
import com.viglet.turing.persistence.dto.agent.TurHumanApprovalDto;
import com.viglet.turing.persistence.model.agent.TurChatHumanApproval;
import com.viglet.turing.persistence.model.agent.TurHumanApprovalStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * T119 / §IX.5.a — public, token-gated endpoints an operator hits from a
 * notification to resolve a {@code humanApproval} node, plus an
 * authenticated admin list of everything still pending.
 *
 * <ul>
 *   <li>{@code GET  /api/genai/approval/{token}} — fetch the approval request
 *       (prompt + status) for the approval page. Public (the token is the
 *       capability).</li>
 *   <li>{@code POST /api/genai/approval/{token}} — record the operator's
 *       decision and resume the parked flow. Public.</li>
 *   <li>{@code GET  /api/genai/approvals} — list all PENDING approvals.
 *       Authenticated (admin / agent view).</li>
 * </ul>
 *
 * <p>The two token endpoints are added to the security {@code permitAll} list
 * ({@code /api/genai/approval/*}); the plural admin list deliberately is not.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@RestController
@Tag(name = "Human Approval", description = "Human-in-the-loop approval (T119)")
public class TurHumanApprovalAPI {

    private final TurHumanApprovalService approvalService;
    private final TurChatFlowEngineService chatFlowEngineService;

    public TurHumanApprovalAPI(TurHumanApprovalService approvalService,
            TurChatFlowEngineService chatFlowEngineService) {
        this.approvalService = approvalService;
        this.chatFlowEngineService = chatFlowEngineService;
    }

    @Operation(summary = "Fetch a pending approval by its resume token")
    @GetMapping("/api/genai/approval/{token}")
    public ResponseEntity<TurHumanApprovalDto> get(@PathVariable String token) {
        return approvalService.findByToken(token)
                .map(a -> ResponseEntity.ok(TurHumanApprovalDto.of(a)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Record an operator decision and resume the parked flow")
    @PostMapping("/api/genai/approval/{token}")
    public ResponseEntity<DecideResponse> decide(@PathVariable String token,
            @RequestBody DecideRequest body) {
        String decision = body == null ? null : body.decision();
        if (decision == null || decision.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new DecideResponse(false, null, 0, "decision is required"));
        }
        Optional<TurChatHumanApproval> opt = approvalService.findByToken(token);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new DecideResponse(false, null, 0, "unknown approval token"));
        }
        TurChatHumanApproval approval = opt.get();
        if (approval.getStatus() != TurHumanApprovalStatus.PENDING) {
            // Already resolved (operator double-clicked, or the timeout swept it
            // first). Idempotent: report the terminal status, resume nothing.
            return ResponseEntity.ok(new DecideResponse(false,
                    approval.getStatus().name(), 0, "approval already resolved"));
        }
        approvalService.recordDecision(token, decision.trim());
        TurChatFlowEngineService.ResumeResult result = chatFlowEngineService.resumeSuspendedFlow(
                approval.getConversationId(),
                Map.of(approval.getApprovalSlot(), decision.trim()),
                "approval=" + token);
        // T652 / §XXXVII.14 — the token IS the capability (anyone holding it can
        // decide the approval), so do NOT log its value. Correlate on the
        // conversation id instead.
        log.info("[HumanApproval] decision '{}' recorded for conversation='{}' — resumed {} state(s)",
                decision.trim(), approval.getConversationId(), result.resumed());
        return ResponseEntity.ok(new DecideResponse(true, "DECIDED", result.resumed(), null));
    }

    @Operation(summary = "List all pending human approvals")
    @Secured({"ROLE_ADMIN", "AI_AGENT_VIEW"})
    @GetMapping("/api/genai/approvals")
    public List<TurHumanApprovalDto> listPending() {
        return approvalService.listPending();
    }

    public record DecideRequest(String decision) {
    }

    public record DecideResponse(boolean ok, String status, int resumed, String error) {
    }
}
