/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.service.chatanalytics;

/**
 * Terminal classification of a chat session — drives the success/abandon
 * funnel chart and is the headline tag of every analytics row.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public enum TurChatSessionOutcome {
    /** Session is still open — recorded by the start event. */
    IN_PROGRESS,
    /** Flow reached an end node, or agent gave a final answer with no flow. */
    COMPLETED,
    /** User left mid-flow (no terminal node, conversationState manually reset, etc.). */
    ABANDONED,
    /** Server-side error (LLM failure, tool exception) terminated the session. */
    ERROR,
    /** Session escalated to a human agent / alternative channel. */
    HANDOFF
}
