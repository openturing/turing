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
package com.viglet.turing.persistence.adapter.integration;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.viglet.turing.domain.integration.TurIntegrationInstanceDomain;
import com.viglet.turing.persistence.model.integration.TurIntegrationInstance;

/**
 * MapStruct mapper for the JPA {@link TurIntegrationInstance} entity to
 * the {@link TurIntegrationInstanceDomain} aggregate. The dev token's
 * secret value is not mapped — only the token ID is projected.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Mapper(componentModel = "spring")
public interface TurIntegrationInstanceDomainMapper {

    @Mapping(target = "apiTokenId", source = "apiToken.id")
    TurIntegrationInstanceDomain toDomain(TurIntegrationInstance entity);

    List<TurIntegrationInstanceDomain> toDomainList(List<TurIntegrationInstance> entities);
}
