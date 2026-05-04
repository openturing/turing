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
package com.viglet.turing.persistence.adapter.dev;

import java.util.List;

import org.mapstruct.Mapper;

import com.viglet.turing.domain.dev.TurDevTokenDomain;
import com.viglet.turing.persistence.model.dev.token.TurDevToken;

/**
 * MapStruct mapper for the JPA {@link TurDevToken} entity to the
 * {@link TurDevTokenDomain} aggregate. The secret token value is
 * deliberately not mapped — it stays on the JPA entity for the
 * authentication path.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Mapper(componentModel = "spring")
public interface TurDevTokenDomainMapper {

    TurDevTokenDomain toDomain(TurDevToken entity);

    List<TurDevTokenDomain> toDomainList(List<TurDevToken> entities);
}
