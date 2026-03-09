package com.viglet.turing.persistence.mapper.sn.ranking;

import com.viglet.turing.persistence.dto.sn.ranking.TurSNRankingExpressionDto;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurSNRankingExpressionMapper {
    TurSNRankingExpressionDto toDto(TurSNRankingExpression entity);

    TurSNRankingExpression toEntity(TurSNRankingExpressionDto dto);

    List<TurSNRankingExpressionDto> toDtoList(List<TurSNRankingExpression> entities);

    Set<TurSNRankingExpressionDto> toDtoSet(Set<TurSNRankingExpression> entities);
}