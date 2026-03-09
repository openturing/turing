package com.viglet.turing.persistence.mapper.sn.spotlight;

import com.viglet.turing.persistence.dto.sn.spotlight.TurSNSiteSpotlightDto;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurSNSiteSpotlightMapper {
    TurSNSiteSpotlightDto toDto(TurSNSiteSpotlight entity);

    TurSNSiteSpotlight toEntity(TurSNSiteSpotlightDto dto);

    List<TurSNSiteSpotlightDto> toDtoList(List<TurSNSiteSpotlight> entities);

    Set<TurSNSiteSpotlightDto> toDtoSet(Set<TurSNSiteSpotlight> entities);
}