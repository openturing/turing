/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.mapper.persona;

import java.util.List;
import java.util.Set;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import com.viglet.turing.persistence.dto.persona.TurPersonaDto;
import com.viglet.turing.persistence.model.persona.TurPersona;

/**
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurPersonaMapper {
    TurPersonaDto toDto(TurPersona entity);

    TurPersona toEntity(TurPersonaDto dto);

    List<TurPersonaDto> toDtoList(List<TurPersona> entities);

    Set<TurPersonaDto> toDtoSet(Set<TurPersona> entities);
}
