/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow.routine;

import java.util.Optional;
import java.util.Set;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Lazy;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.tool.TurNativeToolService;
import com.viglet.turing.persistence.model.agent.TurRoutine;
import com.viglet.turing.persistence.model.agent.TurRoutineKind;
import com.viglet.turing.persistence.repository.agent.TurRoutineRepository;
import com.viglet.turing.sn.TurSNConstants;

import lombok.extern.slf4j.Slf4j;

/**
 * T48 — JMS worker that runs a {@link TurRoutine} dispatched by a
 * {@code scheduleAgent} chat-flow node. The worker resolves the routine
 * (NATIVE → Spring AI {@code @Tool} callback), invokes it with the
 * pre-interpolated input JSON, and writes the result back through
 * {@link TurChatFlowEngineService#writeSlot(String, String, String)} so
 * the slot-bus subscribers (including the auto-resume listener) see the
 * completion immediately.
 *
 * <p>The engine is injected {@link Lazy} to break the boot cycle —
 * {@code TurChatFlowEngineService} depends on {@code TurScheduleAgentNodeExecutor}
 * which lives in the same package as this worker; without the lazy
 * indirection Spring rejects the graph as circular.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class TurRoutineQueue {

    private final TurRoutineRepository routineRepository;
    private final TurNativeToolService nativeToolService;
    private final TurChatFlowEngineService engine;

    public TurRoutineQueue(TurRoutineRepository routineRepository,
            TurNativeToolService nativeToolService,
            @Lazy TurChatFlowEngineService engine) {
        this.routineRepository = routineRepository;
        this.nativeToolService = nativeToolService;
        this.engine = engine;
    }

    @JmsListener(destination = TurSNConstants.ROUTINE_QUEUE,
            id = TurSNConstants.ROUTINE_QUEUE_LISTENER,
            concurrency = "${turing.jms.routine.concurrency:1-2}")
    public void receive(TurScheduleAgentMessage message) {
        if (message == null || message.getRoutineId() == null) {
            log.warn("[RoutineQueue] dropped null/empty message");
            return;
        }
        String routineId = message.getRoutineId();
        String conversationId = message.getConversationId();
        String nodeId = message.getNodeId();
        String outputVariable = message.getOutputVariable();

        Optional<TurRoutine> routineOpt = routineRepository.findById(routineId);
        if (routineOpt.isEmpty()) {
            log.warn("[RoutineQueue] routine '{}' not found (conv={} node={})",
                    routineId, conversationId, nodeId);
            return;
        }
        TurRoutine routine = routineOpt.get();
        if (!routine.isEnabled()) {
            log.warn("[RoutineQueue] routine '{}' is disabled (conv={} node={})",
                    routineId, conversationId, nodeId);
            return;
        }
        if (routine.getKind() != TurRoutineKind.NATIVE) {
            log.warn("[RoutineQueue] routine kind '{}' not yet supported (T48 MVP is NATIVE-only)",
                    routine.getKind());
            return;
        }
        String toolName = routine.getNativeToolName();
        if (toolName == null || toolName.isBlank()) {
            log.warn("[RoutineQueue] routine '{}' has no nativeToolName", routineId);
            return;
        }

        ToolCallback[] callbacks = nativeToolService.getToolCallbacks(Set.of(toolName));
        if (callbacks.length == 0) {
            log.warn("[RoutineQueue] native tool '{}' not found for routine '{}'",
                    toolName, routineId);
            return;
        }

        String input = message.getInputJson() == null ? "{}" : message.getInputJson();
        String result;
        try {
            result = callbacks[0].call(input);
        } catch (Exception e) {
            log.warn("[RoutineQueue] routine '{}' tool '{}' failed: {}",
                    routineId, toolName, e.getMessage(), e);
            return;
        }

        log.info("[RoutineQueue] routine '{}' completed on conv={} node={} ({} chars{})",
                routineId, conversationId, nodeId,
                result == null ? 0 : result.length(),
                outputVariable == null ? " — no outputVariable" : " → slot '" + outputVariable + "'");

        if (outputVariable != null && !outputVariable.isBlank()) {
            engine.writeSlot(conversationId, outputVariable, result == null ? "" : result);
        }
    }
}
