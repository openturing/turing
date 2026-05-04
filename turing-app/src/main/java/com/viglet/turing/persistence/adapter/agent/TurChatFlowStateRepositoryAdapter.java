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
package com.viglet.turing.persistence.adapter.agent;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.viglet.turing.domain.agent.TurChatFlowStateDomain;
import com.viglet.turing.domain.agent.TurChatFlowStateRepositoryPort;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;

/**
 * Infrastructure adapter implementing
 * {@link TurChatFlowStateRepositoryPort}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurChatFlowStateRepositoryAdapter implements TurChatFlowStateRepositoryPort {

    private final TurChatFlowStateRepository turChatFlowStateRepository;
    private final TurChatFlowStateDomainMapper mapper;

    public TurChatFlowStateRepositoryAdapter(TurChatFlowStateRepository turChatFlowStateRepository,
            TurChatFlowStateDomainMapper mapper) {
        this.turChatFlowStateRepository = turChatFlowStateRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<TurChatFlowStateDomain> findById(String id) {
        return turChatFlowStateRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<TurChatFlowStateDomain> findByConversationIdAndFlowId(String conversationId,
            String flowId) {
        return turChatFlowStateRepository
                .findByConversationIdAndFlow_Id(conversationId, flowId)
                .map(mapper::toDomain);
    }

    @Override
    public List<TurChatFlowStateDomain> findByConversationIdAndAgentId(String conversationId,
            String agentId) {
        return mapper.toDomainList(turChatFlowStateRepository
                .findByConversationIdAndFlow_TurAIAgent_Id(conversationId, agentId));
    }

    @Override
    public List<TurChatFlowStateDomain> findByFlowIdOrderByUpdatedAtDesc(String flowId) {
        return mapper.toDomainList(
                turChatFlowStateRepository.findByFlow_IdOrderByUpdatedAtDesc(flowId));
    }
}
