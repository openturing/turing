package com.viglet.turing.persistence.mapper.se;

import com.viglet.turing.persistence.dto.se.TurSEInstanceDto;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurSEInstanceMapper {
    TurSEInstanceDto toDto(TurSEInstance entity);

    TurSEInstance toEntity(TurSEInstanceDto dto);

    List<TurSEInstanceDto> toDtoList(List<TurSEInstance> entities);

    Set<TurSEInstanceDto> toDtoSet(Set<TurSEInstance> entities);

    // T372 — never reassign ownership through an edit; tenantId is owned by
    // the create/import path (TurInfraTenantScope), not the request body.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    void updateEntity(TurSEInstance source, @MappingTarget TurSEInstance target);
}