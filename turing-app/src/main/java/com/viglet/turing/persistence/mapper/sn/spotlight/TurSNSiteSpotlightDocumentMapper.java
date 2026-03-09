package com.viglet.turing.persistence.mapper.sn.spotlight;

import com.viglet.turing.persistence.dto.sn.spotlight.TurSNSiteSpotlightDocumentDto;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurSNSiteSpotlightDocumentMapper {
    TurSNSiteSpotlightDocumentDto toDto(TurSNSiteSpotlightDocument entity);

    TurSNSiteSpotlightDocument toEntity(TurSNSiteSpotlightDocumentDto dto);

    List<TurSNSiteSpotlightDocumentDto> toDtoList(List<TurSNSiteSpotlightDocument> entities);

    Set<TurSNSiteSpotlightDocumentDto> toDtoSet(Set<TurSNSiteSpotlightDocument> entities);
}