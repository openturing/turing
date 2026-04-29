/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.mapper.customtool;

import java.util.List;
import java.util.Set;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.viglet.turing.persistence.dto.customtool.TurCustomToolDto;
import com.viglet.turing.persistence.model.customtool.TurCustomTool;

/**
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurCustomToolMapper {
    TurCustomToolDto toDto(TurCustomTool entity);

    TurCustomTool toEntity(TurCustomToolDto dto);

    List<TurCustomToolDto> toDtoList(List<TurCustomTool> entities);

    Set<TurCustomToolDto> toDtoSet(Set<TurCustomTool> entities);

    @Mapping(target = "id", ignore = true)
    void updateEntity(TurCustomTool source, @MappingTarget TurCustomTool target);
}
