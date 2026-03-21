package com.viglet.turing.persistence.mapper.store;

import com.viglet.turing.persistence.dto.store.TurStoreVendorDto;
import com.viglet.turing.persistence.model.store.TurStoreVendor;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurStoreVendorMapper {
    TurStoreVendorDto toDto(TurStoreVendor entity);

    TurStoreVendor toEntity(TurStoreVendorDto dto);

    List<TurStoreVendorDto> toDtoList(List<TurStoreVendor> entities);

    Set<TurStoreVendorDto> toDtoSet(Set<TurStoreVendor> entities);

    @Mapping(target = "id", ignore = true)
    void updateEntity(TurStoreVendor source, @MappingTarget TurStoreVendor target);
}