package com.viglet.turing.persistence.mapper.sn.field;

import com.viglet.turing.persistence.dto.sn.field.TurSNSiteCustomFacetItemDto;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetItem;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurSNSiteCustomFacetItemMapper {
    TurSNSiteCustomFacetItemDto toDto(TurSNSiteCustomFacetItem entity);

    TurSNSiteCustomFacetItem toEntity(TurSNSiteCustomFacetItemDto dto);

    List<TurSNSiteCustomFacetItemDto> toDtoList(List<TurSNSiteCustomFacetItem> entities);

    Set<TurSNSiteCustomFacetItemDto> toDtoSet(Set<TurSNSiteCustomFacetItem> entities);
}