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
    private final TurRoutineGroovyExecutor groovyExecutor;
    private final TurChatFlowEngineService engine;
    private final com.viglet.turing.tenant.TurJmsTenantPropagation turJmsTenantPropagation;

    public TurRoutineQueue(TurRoutineRepository routineRepository,
            TurNativeToolService nativeToolService,
            TurRoutineGroovyExecutor groovyExecutor,
            @Lazy TurChatFlowEngineService engine,
            com.viglet.turing.tenant.TurJmsTenantPropagation turJmsTenantPropagation) {
        this.routineRepository = routineRepository;
        this.nativeToolService = nativeToolService;
        this.groovyExecutor = groovyExecutor;
        this.engine = engine;
        this.turJmsTenantPropagation = turJmsTenantPropagation;
    }

    @JmsListener(destination = TurSNConstants.ROUTINE_QUEUE,
            id = TurSNConstants.ROUTINE_QUEUE_LISTENER,
            concurrency = "${turing.jms.routine.concurrency:1-2}")
    public void receive(TurScheduleAgentMessage message,
            @org.springframework.messaging.handler.annotation.Header(
                    name = com.viglet.turing.tenant.TurJmsTenantPropagation.HEADER,
                    required = false) String tenantId) {
        // T272 / §XIV.4.6 — restore the originating tenant on this consumer thread.
        turJmsTenantPropagation.runForHeader(tenantId, () -> process(message));
    }

    private void process(TurScheduleAgentMessage message) {
        if (message == null || message.getRoutineId() == null) {
            log.warn("[RoutineQueue] dropped null/empty message");
            return;
        }
        Optional<TurRoutine> routineOpt = routineRepository.findById(message.getRoutineId());
        if (routineOpt.isEmpty()) {
            log.warn("[RoutineQueue] routine '{}' not found (conv={} node={})",
                    message.getRoutineId(), message.getConversationId(), message.getNodeId());
            return;
        }
        TurRoutine routine = routineOpt.get();
        if (!routine.isEnabled()) {
            log.warn("[RoutineQueue] routine '{}' is disabled (conv={} node={})",
                    routine.getName(), message.getConversationId(), message.getNodeId());
            return;
        }
        String result = switch (routine.getKind()) {
            case NATIVE -> runNative(routine, message);
            case GROOVY -> runGroovy(routine, message);
        };
        if (result == null) {
            return;
        }
        log.info("[RoutineQueue] routine '{}' completed on conv={} node={} ({} chars{})",
                routine.getName(), message.getConversationId(), message.getNodeId(),
                result.length(),
                message.getOutputVariable() == null ? " — no outputVariable"
                        : " → slot '" + message.getOutputVariable() + "'");
        if (message.getOutputVariable() != null && !message.getOutputVariable().isBlank()) {
            engine.writeSlot(message.getConversationId(), message.getOutputVariable(), result);
        }
    }

    /** Returns the tool result, or {@code null} when the routine could not run. */
    private String runNative(TurRoutine routine, TurScheduleAgentMessage message) {
        String toolName = routine.getNativeToolName();
        if (toolName == null || toolName.isBlank()) {
            log.warn("[RoutineQueue] NATIVE routine '{}' has no nativeToolName", routine.getName());
            return null;
        }
        ToolCallback[] callbacks = nativeToolService.getToolCallbacks(Set.of(toolName));
        if (callbacks.length == 0) {
            log.warn("[RoutineQueue] native tool '{}' not found for routine '{}'",
                    toolName, routine.getName());
            return null;
        }
        String input = message.getInputJson() == null ? "{}" : message.getInputJson();
        try {
            String out = callbacks[0].call(input);
            return out == null ? "" : out;
        } catch (Exception e) {
            log.warn("[RoutineQueue] NATIVE routine '{}' tool '{}' failed: {}",
                    routine.getName(), toolName, e.getMessage(), e);
            return null;
        }
    }

    /** Returns the script result, or {@code null} when execution failed. */
    private String runGroovy(TurRoutine routine, TurScheduleAgentMessage message) {
        try {
            return groovyExecutor.execute(routine, message.getConversationId(),
                    message.getInputJson() == null ? "{}" : message.getInputJson());
        } catch (RuntimeException e) {
            log.warn("[RoutineQueue] GROOVY routine '{}' failed: {}",
                    routine.getName(), e.getMessage(), e);
            return null;
        }
    }
}
