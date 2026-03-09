package com.viglet.turing.persistence.mapper.sn.metric;

import com.viglet.turing.persistence.dto.sn.metric.TurSNSiteMetricAccessDto;
import com.viglet.turing.persistence.model.sn.metric.TurSNSiteMetricAccess;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurSNSiteMetricAccessMapper {
    TurSNSiteMetricAccessDto toDto(TurSNSiteMetricAccess entity);

    TurSNSiteMetricAccess toEntity(TurSNSiteMetricAccessDto dto);

    List<TurSNSiteMetricAccessDto> toDtoList(List<TurSNSiteMetricAccess> entities);

    Set<TurSNSiteMetricAccessDto> toDtoSet(Set<TurSNSiteMetricAccess> entities);
}