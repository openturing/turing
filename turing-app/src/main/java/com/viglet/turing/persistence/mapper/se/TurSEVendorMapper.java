package com.viglet.turing.persistence.mapper.se;

import com.viglet.turing.persistence.dto.se.TurSEVendorDto;
import com.viglet.turing.persistence.model.se.TurSEVendor;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurSEVendorMapper {
    TurSEVendorDto toDto(TurSEVendor entity);

    TurSEVendor toEntity(TurSEVendorDto dto);

    List<TurSEVendorDto> toDtoList(List<TurSEVendor> entities);

    Set<TurSEVendorDto> toDtoSet(Set<TurSEVendor> entities);
}