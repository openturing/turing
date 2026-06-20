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
 * The configured entity references that identify a chat session at start time:
 * the conversation plus the agent / persona / LLM / embedding / store instances
 * it runs against, and the user who opened it. Bundled into one cohesive record
 * so the {@code recordSessionStart} entry point stays below the parameter
 * threshold instead of threading seven loose strings.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurChatSessionRefs(
        String conversationId,
        String agentId,
        String personaId,
        String llmInstanceId,
        String embeddingModelId,
        String storeInstanceId,
        String userId) {
}
