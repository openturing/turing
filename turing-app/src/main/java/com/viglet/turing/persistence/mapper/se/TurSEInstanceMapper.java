package com.viglet.turing.persistence.mapper.se;

import com.viglet.turing.persistence.dto.se.TurSEInstanceDto;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurSEInstanceMapper {
    TurSEInstanceDto toDto(TurSEInstance entity);

    TurSEInstance toEntity(TurSEInstanceDto dto);

    List<TurSEInstanceDto> toDtoList(List<TurSEInstance> entities);

    Set<TurSEInstanceDto> toDtoSet(Set<TurSEInstance> entities);
}