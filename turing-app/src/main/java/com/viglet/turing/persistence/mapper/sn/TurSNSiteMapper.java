package com.viglet.turing.persistence.mapper.sn;

import com.viglet.turing.persistence.dto.sn.TurSNSiteDto;
import com.viglet.turing.persistence.dto.sn.TurSNSiteListDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurSNSiteMapper {
    TurSNSiteDto toDto(TurSNSite entity);

    TurSNSite toEntity(TurSNSiteDto dto);

    List<TurSNSiteDto> toDtoList(List<TurSNSite> entities);

    Set<TurSNSiteDto> toDtoSet(Set<TurSNSite> entities);

    /**
     * Maps the entity to the lightweight listing DTO, touching only the
     * {@code turSNSiteLocales} association. Used by the listing endpoint to
     * avoid the N+1 pattern caused by copying every lazy collection.
     * <p>
     * {@code genAiEnabled} reflects whether the site has a configured and
     * enabled {@link com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi},
     * gating the ANN Search button on the admin listing.
     */
    @Mapping(target = "genAiEnabled",
            expression = "java(entity.getTurSNSiteGenAi() != null"
                    + " && entity.getTurSNSiteGenAi().getTurAIAgent() != null"
                    + " && entity.getTurSNSiteGenAi().getTurAIAgent().getEnabled() == 1"
                    + " && entity.getTurSNSiteGenAi().getTurAIAgent().isRagEnabled())")
    TurSNSiteListDto toListDto(TurSNSite entity);

    List<TurSNSiteListDto> toListDtoList(List<TurSNSite> entities);

    TurSNSiteListDto.TurSNSiteLocaleSummary toLocaleSummary(TurSNSiteLocale locale);
}