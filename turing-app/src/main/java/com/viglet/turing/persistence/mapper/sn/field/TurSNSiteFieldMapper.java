package com.viglet.turing.persistence.mapper.sn.field;

import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldDto;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurSNSiteFieldMapper {
    TurSNSiteFieldDto toDto(TurSNSiteField entity);

    TurSNSiteField toEntity(TurSNSiteFieldDto dto);

    List<TurSNSiteFieldDto> toDtoList(List<TurSNSiteField> entities);

    Set<TurSNSiteFieldDto> toDtoSet(Set<TurSNSiteField> entities);
}