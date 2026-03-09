package com.viglet.turing.persistence.mapper.store;

import com.viglet.turing.persistence.dto.store.TurStoreInstanceDto;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurStoreInstanceMapper {
    TurStoreInstanceDto toDto(TurStoreInstance entity);

    TurStoreInstance toEntity(TurStoreInstanceDto dto);

    List<TurStoreInstanceDto> toDtoList(List<TurStoreInstance> entities);

    Set<TurStoreInstanceDto> toDtoSet(Set<TurStoreInstance> entities);
}