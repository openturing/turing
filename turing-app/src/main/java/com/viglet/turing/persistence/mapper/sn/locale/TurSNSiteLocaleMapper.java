package com.viglet.turing.persistence.mapper.sn.locale;

import com.viglet.turing.persistence.dto.sn.locale.TurSNSiteLocaleDto;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurSNSiteLocaleMapper {
    TurSNSiteLocaleDto toDto(TurSNSiteLocale entity);

    TurSNSiteLocale toEntity(TurSNSiteLocaleDto dto);

    List<TurSNSiteLocaleDto> toDtoList(List<TurSNSiteLocale> entities);

    Set<TurSNSiteLocaleDto> toDtoSet(Set<TurSNSiteLocale> entities);
}