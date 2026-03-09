package com.viglet.turing.persistence.mapper.system;

import com.viglet.turing.persistence.dto.system.TurLocaleDto;
import com.viglet.turing.persistence.model.system.TurLocale;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurLocaleMapper {
    TurLocaleDto toDto(TurLocale entity);

    TurLocale toEntity(TurLocaleDto dto);

    List<TurLocaleDto> toDtoList(List<TurLocale> entities);

    Set<TurLocaleDto> toDtoSet(Set<TurLocale> entities);
}