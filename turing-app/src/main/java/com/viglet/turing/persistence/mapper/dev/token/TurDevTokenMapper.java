package com.viglet.turing.persistence.mapper.dev.token;

import com.viglet.turing.persistence.dto.dev.token.TurDevTokenDto;
import com.viglet.turing.persistence.model.dev.token.TurDevToken;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurDevTokenMapper {
    TurDevTokenDto toDto(TurDevToken entity);

    TurDevToken toEntity(TurDevTokenDto dto);

    List<TurDevTokenDto> toDtoList(List<TurDevToken> entities);

    Set<TurDevTokenDto> toDtoSet(Set<TurDevToken> entities);
}