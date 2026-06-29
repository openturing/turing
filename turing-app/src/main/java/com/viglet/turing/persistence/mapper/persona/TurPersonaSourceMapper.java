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

import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import com.viglet.turing.persistence.dto.persona.TurPersonaSourceDto;
import com.viglet.turing.persistence.model.persona.TurPersonaSource;

/**
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurPersonaSourceMapper {
    TurPersonaSourceDto toDto(TurPersonaSource entity);

    TurPersonaSource toEntity(TurPersonaSourceDto dto);

    List<TurPersonaSourceDto> toDtoList(List<TurPersonaSource> entities);
}
