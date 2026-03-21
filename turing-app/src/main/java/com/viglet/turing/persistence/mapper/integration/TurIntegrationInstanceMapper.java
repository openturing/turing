package com.viglet.turing.persistence.mapper.integration;

import com.viglet.turing.persistence.dto.integration.TurIntegrationInstanceDto;
import com.viglet.turing.persistence.model.integration.TurIntegrationInstance;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurIntegrationInstanceMapper {
    TurIntegrationInstanceDto toDto(TurIntegrationInstance entity);

    TurIntegrationInstance toEntity(TurIntegrationInstanceDto dto);

    List<TurIntegrationInstanceDto> toDtoList(List<TurIntegrationInstance> entities);

    Set<TurIntegrationInstanceDto> toDtoSet(Set<TurIntegrationInstance> entities);

    @Mapping(target = "id", ignore = true)
    void updateEntity(TurIntegrationInstance source, @MappingTarget TurIntegrationInstance target);
}