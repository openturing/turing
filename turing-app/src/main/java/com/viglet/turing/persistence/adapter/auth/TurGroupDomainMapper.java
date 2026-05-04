/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.persistence.adapter.auth;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.viglet.turing.domain.auth.TurGroupDomain;
import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurRole;
import com.viglet.turing.persistence.model.auth.TurUser;

/**
 * MapStruct mapper for the JPA {@link TurGroup} entity to the
 * {@link TurGroupDomain} aggregate.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Mapper(componentModel = "spring")
public interface TurGroupDomainMapper {

    @Mapping(target = "roleIds", source = "turRoles")
    @Mapping(target = "userIds", source = "turUsers")
    TurGroupDomain toDomain(TurGroup entity);

    List<TurGroupDomain> toDomainList(List<TurGroup> entities);

    default Set<String> rolesToIds(Collection<TurRole> set) {
        return set == null ? Set.of()
                : Collections.unmodifiableSet(
                        set.stream().map(TurRole::getId).collect(Collectors.toSet()));
    }

    default Set<String> usersToIds(Collection<TurUser> set) {
        return set == null ? Set.of()
                : Collections.unmodifiableSet(
                        set.stream().map(TurUser::getUsername).collect(Collectors.toSet()));
    }
}
