package com.viglet.turing.persistence.mapper.llm;

import com.viglet.turing.persistence.dto.llm.TurLLMVendorDto;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurLLMVendorMapper {
    TurLLMVendorDto toDto(TurLLMVendor entity);

    TurLLMVendor toEntity(TurLLMVendorDto dto);

    List<TurLLMVendorDto> toDtoList(List<TurLLMVendor> entities);

    Set<TurLLMVendorDto> toDtoSet(Set<TurLLMVendor> entities);

    @Mapping(target = "id", ignore = true)
    void updateEntity(TurLLMVendor source, @MappingTarget TurLLMVendor target);
}