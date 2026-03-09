package com.viglet.turing.persistence.mapper.auth;

import com.viglet.turing.persistence.dto.auth.TurUserDto;
import com.viglet.turing.persistence.model.auth.TurUser;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TurUserMapper {
    TurUserDto toDto(TurUser entity);

    TurUser toEntity(TurUserDto dto);

    List<TurUserDto> toDtoList(List<TurUser> entities);

    Set<TurUserDto> toDtoSet(Set<TurUser> entities);
}