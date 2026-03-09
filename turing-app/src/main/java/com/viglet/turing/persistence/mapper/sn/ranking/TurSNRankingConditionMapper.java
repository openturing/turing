package com.viglet.turing.persistence.mapper.sn.ranking;

import com.viglet.turing.persistence.dto.sn.ranking.TurSNRankingConditionDto;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingCondition;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurSNRankingConditionMapper {
    TurSNRankingConditionDto toDto(TurSNRankingCondition entity);

    TurSNRankingCondition toEntity(TurSNRankingConditionDto dto);

    List<TurSNRankingConditionDto> toDtoList(List<TurSNRankingCondition> entities);

    Set<TurSNRankingConditionDto> toDtoSet(Set<TurSNRankingCondition> entities);
}