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

import java.io.Serial;
import java.io.Serializable;

/**
 * JMS payload sent from a {@code scheduleAgent} node executor to the
 * {@code TurRoutineQueue} worker. Carries enough context for the worker
 * to run the named routine, write the result back through the slot bus,
 * and unblock the parked chat-flow state.
 *
 * <p>Kept as a small {@link Serializable} POJO so the default Artemis
 * object converter handles it without a custom message-converter chain.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public class TurScheduleAgentMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Routine to execute (FK to {@code tur_routine}). */
    private String routineId;

    /** Conversation that owns the parked flow state. */
    private String conversationId;

    /** Chat-flow node id (for log correlation only). */
    private String nodeId;

    /** Slot name to write the routine result into. */
    private String outputVariable;

    /** Tool input JSON (already interpolated, ready to pass to the callback). */
    private String inputJson;

    public TurScheduleAgentMessage() {
    }

    public TurScheduleAgentMessage(String routineId, String conversationId, String nodeId,
            String outputVariable, String inputJson) {
        this.routineId = routineId;
        this.conversationId = conversationId;
        this.nodeId = nodeId;
        this.outputVariable = outputVariable;
        this.inputJson = inputJson;
    }

    public String getRoutineId() {
        return routineId;
    }

    public void setRoutineId(String routineId) {
        this.routineId = routineId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getOutputVariable() {
        return outputVariable;
    }

    public void setOutputVariable(String outputVariable) {
        this.outputVariable = outputVariable;
    }

    public String getInputJson() {
        return inputJson;
    }

    public void setInputJson(String inputJson) {
        this.inputJson = inputJson;
    }
}
