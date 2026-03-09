package com.viglet.turing.persistence.mapper.sn.source;

import com.viglet.turing.persistence.dto.sn.source.TurSNSourceTypeDto;
import com.viglet.turing.persistence.model.sn.source.TurSNSourceType;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurSNSourceTypeMapper {
    TurSNSourceTypeDto toDto(TurSNSourceType entity);

    TurSNSourceType toEntity(TurSNSourceTypeDto dto);

    List<TurSNSourceTypeDto> toDtoList(List<TurSNSourceType> entities);

    Set<TurSNSourceTypeDto> toDtoSet(Set<TurSNSourceType> entities);
}