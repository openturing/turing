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

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.viglet.turing.domain.agent.TurAIAgentDomain;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.customtool.TurCustomTool;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;

/**
 * MapStruct mapper for the JPA {@link TurAIAgent} entity to the
 * {@link TurAIAgentDomain} aggregate. ManyToMany relationships are projected
 * to immutable sets of IDs so the domain record cannot be used to mutate the
 * agent's configured memberships.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Mapper(componentModel = "spring")
public interface TurAIAgentDomainMapper {

    @Mapping(target = "llmInstanceIds", source = "llmInstances")
    @Mapping(target = "mcpServerIds", source = "mcpServers")
    @Mapping(target = "customToolIds", source = "customTools")
    @Mapping(target = "embeddingModelInstanceId", source = "turEmbeddingModelInstance.id")
    @Mapping(target = "storeInstanceId", source = "turStoreInstance.id")
    TurAIAgentDomain toDomain(TurAIAgent entity);

    List<TurAIAgentDomain> toDomainList(List<TurAIAgent> entities);

    default Set<String> llmInstancesToIds(Set<TurLLMInstance> set) {
        return set == null ? Set.of()
                : Collections.unmodifiableSet(
                        set.stream().map(TurLLMInstance::getId).collect(Collectors.toSet()));
    }

    default Set<String> mcpServersToIds(Set<TurMcpServer> set) {
        return set == null ? Set.of()
                : Collections.unmodifiableSet(
                        set.stream().map(TurMcpServer::getId).collect(Collectors.toSet()));
    }

    default Set<String> customToolsToIds(Set<TurCustomTool> set) {
        return set == null ? Set.of()
                : Collections.unmodifiableSet(
                        set.stream().map(TurCustomTool::getId).collect(Collectors.toSet()));
    }
}
