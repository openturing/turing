package com.viglet.turing.persistence.mapper.auth;

import com.viglet.turing.persistence.dto.auth.TurGroupDto;
import com.viglet.turing.persistence.model.auth.TurGroup;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurGroupMapper {
    TurGroupDto toDto(TurGroup entity);

    TurGroup toEntity(TurGroupDto dto);

    List<TurGroupDto> toDtoList(List<TurGroup> entities);

    Set<TurGroupDto> toDtoSet(Set<TurGroup> entities);
}