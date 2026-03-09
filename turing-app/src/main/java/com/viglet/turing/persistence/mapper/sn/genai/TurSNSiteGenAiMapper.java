package com.viglet.turing.persistence.mapper.sn.genai;

import com.viglet.turing.persistence.dto.sn.genai.TurSNSiteGenAiDto;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurSNSiteGenAiMapper {
    TurSNSiteGenAiDto toDto(TurSNSiteGenAi entity);

    TurSNSiteGenAi toEntity(TurSNSiteGenAiDto dto);

    List<TurSNSiteGenAiDto> toDtoList(List<TurSNSiteGenAi> entities);

    Set<TurSNSiteGenAiDto> toDtoSet(Set<TurSNSiteGenAi> entities);
}