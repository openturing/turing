package com.viglet.turing.persistence.mapper.embedding;

import com.viglet.turing.persistence.dto.embedding.TurEmbeddingModelDto;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * MapStruct mapper for Embedding Model.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurEmbeddingModelMapper {
    TurEmbeddingModelDto toDto(TurEmbeddingModel entity);

    TurEmbeddingModel toEntity(TurEmbeddingModelDto dto);

    List<TurEmbeddingModelDto> toDtoList(List<TurEmbeddingModel> entities);

    @Mapping(target = "id", ignore = true)
    void updateEntity(TurEmbeddingModel source, @MappingTarget TurEmbeddingModel target);

    @Mapping(target = "id", ignore = true)
    void updateEntityFromDto(TurEmbeddingModelDto source, @MappingTarget TurEmbeddingModel target);
}
