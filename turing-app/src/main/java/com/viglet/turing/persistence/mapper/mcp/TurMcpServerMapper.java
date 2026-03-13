package com.viglet.turing.persistence.mapper.mcp;

import com.viglet.turing.persistence.dto.mcp.TurMcpServerDto;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurMcpServerMapper {
    TurMcpServerDto toDto(TurMcpServer entity);

    TurMcpServer toEntity(TurMcpServerDto dto);

    List<TurMcpServerDto> toDtoList(List<TurMcpServer> entities);

    Set<TurMcpServerDto> toDtoSet(Set<TurMcpServer> entities);
}
