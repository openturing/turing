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
package com.viglet.turing.persistence.adapter.intent;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.viglet.turing.domain.intent.TurIntentActionDomain;
import com.viglet.turing.domain.intent.TurIntentDomain;
import com.viglet.turing.persistence.model.intent.TurIntent;
import com.viglet.turing.persistence.model.intent.TurIntentAction;

/**
 * MapStruct mapper for the JPA {@link TurIntent} entity to the
 * {@link TurIntentDomain} aggregate. Actions are projected as full
 * {@link TurIntentActionDomain} records since consumers always need the
 * action payload (label + prompt) to render the chat UI.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Mapper(componentModel = "spring")
public interface TurIntentDomainMapper {

    @Mapping(target = "agentId", source = "turAIAgent.id")
    @Mapping(target = "actions", source = "actions")
    TurIntentDomain toDomain(TurIntent entity);

    List<TurIntentDomain> toDomainList(List<TurIntent> entities);

    TurIntentActionDomain toActionDomain(TurIntentAction entity);

    default Set<TurIntentActionDomain> actionsToDomain(Set<TurIntentAction> set) {
        return set == null ? Set.of()
                : Collections.unmodifiableSet(
                        set.stream().map(this::toActionDomain).collect(Collectors.toSet()));
    }
}
