package com.viglet.turing.persistence.mapper.sn.merge;

import com.viglet.turing.persistence.dto.sn.merge.TurSNSiteMergeProvidersDto;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProviders;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurSNSiteMergeProvidersMapper {
    TurSNSiteMergeProvidersDto toDto(TurSNSiteMergeProviders entity);

    TurSNSiteMergeProviders toEntity(TurSNSiteMergeProvidersDto dto);

    List<TurSNSiteMergeProvidersDto> toDtoList(List<TurSNSiteMergeProviders> entities);

    Set<TurSNSiteMergeProvidersDto> toDtoSet(Set<TurSNSiteMergeProviders> entities);
}