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

package com.viglet.turing.genai;

import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;

/**
 * Resolved per-turn flow runtime — group of values that always travel
 * together through the chat-turn pipeline (executor → tool resolver →
 * prompt assembler → streaming dispatcher).
 *
 * <p>{@code freshlyTriggered} mirrors
 * {@link com.viglet.turing.genai.flow.TurChatFlowEngineService.FlowSelection#freshlyTriggered}:
 * true only on the very turn that activated the flow (user msg = trigger,
 * not data). The executor uses this to skip the early-advance pass so the
 * trigger text doesn't get force-captured as the first slot.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
record TurAgentChatFlowContext(TurChatFlow flow, ChatFlowGraph graph, TurChatFlowState state,
        boolean freshlyTriggered) {
}
