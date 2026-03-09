package com.viglet.turing.persistence.mapper.auth;

import com.viglet.turing.persistence.dto.auth.TurRoleDto;
import com.viglet.turing.persistence.model.auth.TurRole;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurRoleMapper {
    TurRoleDto toDto(TurRole entity);

    TurRole toEntity(TurRoleDto dto);

    List<TurRoleDto> toDtoList(List<TurRole> entities);

    Set<TurRoleDto> toDtoSet(Set<TurRole> entities);
}