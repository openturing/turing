package com.viglet.turing.persistence.mapper.sn.field;

import com.viglet.turing.persistence.dto.sn.field.TurSNSiteCustomFacetDto;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurSNSiteCustomFacetMapper {
    TurSNSiteCustomFacetDto toDto(TurSNSiteCustomFacet entity);

    TurSNSiteCustomFacet toEntity(TurSNSiteCustomFacetDto dto);

    List<TurSNSiteCustomFacetDto> toDtoList(List<TurSNSiteCustomFacet> entities);

    Set<TurSNSiteCustomFacetDto> toDtoSet(Set<TurSNSiteCustomFacet> entities);
}