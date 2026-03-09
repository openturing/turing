package com.viglet.turing.persistence.mapper.sn;

import com.viglet.turing.persistence.dto.sn.TurSNSiteDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurSNSiteMapper {
    TurSNSiteDto toDto(TurSNSite entity);

    TurSNSite toEntity(TurSNSiteDto dto);

    List<TurSNSiteDto> toDtoList(List<TurSNSite> entities);

    Set<TurSNSiteDto> toDtoSet(Set<TurSNSite> entities);
}