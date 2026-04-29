/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.mapper.agent;

import java.util.List;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import com.viglet.turing.persistence.dto.agent.TurChatFlowDto;
import com.viglet.turing.persistence.model.agent.TurChatFlow;

/**
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurChatFlowMapper {
    TurChatFlowDto toDto(TurChatFlow entity);

    TurChatFlow toEntity(TurChatFlowDto dto);

    List<TurChatFlowDto> toDtoList(List<TurChatFlow> entities);
}
