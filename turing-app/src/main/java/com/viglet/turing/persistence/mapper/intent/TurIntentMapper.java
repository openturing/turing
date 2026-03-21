package com.viglet.turing.persistence.mapper.intent;

import java.util.List;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import com.viglet.turing.persistence.dto.intent.TurIntentDto;
import com.viglet.turing.persistence.model.intent.TurIntent;

/**
 * @author Alexandre Oliveira
 * @since 2026.1.17
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurIntentMapper {
    TurIntentDto toDto(TurIntent entity);
    TurIntent toEntity(TurIntentDto dto);
    List<TurIntentDto> toDtoList(List<TurIntent> entities);
}
