package com.viglet.turing.persistence.mapper.sn.field;

import java.util.List;
import java.util.Set;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtFacetDto;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurSNSiteFieldExtFacetMapper {
    TurSNSiteFieldExtFacetDto toDto(TurSNSiteFieldExtFacet entity);

    @Mapping(target = "turSNSiteFieldExt", ignore = true)
    TurSNSiteFieldExtFacet toEntity(TurSNSiteFieldExtFacetDto dto);

    List<TurSNSiteFieldExtFacetDto> toDtoList(List<TurSNSiteFieldExtFacet> entities);

    Set<TurSNSiteFieldExtFacetDto> toDtoSet(Set<TurSNSiteFieldExtFacet> entities);
}