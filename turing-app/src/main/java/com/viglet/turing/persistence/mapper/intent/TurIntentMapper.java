package com.viglet.turing.persistence.mapper.intent;

import java.util.List;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

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

    /**
     * Copy editable fields from {@code source} into {@code target}. The
     * primary key and the parent agent are intentionally ignored so a
     * naive PUT (where the frontend doesn't echo back {@code turAIAgent})
     * doesn't null out the FK and orphan the intent.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "turAIAgent", ignore = true)
    void updateEntity(TurIntent source, @MappingTarget TurIntent target);
}
