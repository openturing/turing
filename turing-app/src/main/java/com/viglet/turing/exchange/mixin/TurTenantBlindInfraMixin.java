/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.exchange.mixin;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Keeps the owning {@code tenantId} of a BYO-infra instance out of every export
 * bundle (T373 / §XIV.8.7).
 * <p>
 * A tenant id only means something in the install that minted it. Carried inside
 * a portable bundle it is worse than useless: {@code VigletInfraTenantScope}
 * stamps an imported instance only when {@code getTenantId() == null}, so a
 * non-null value survives the import verbatim and the instance lands owned by a
 * tenant that does not exist on the target — invisible to everyone, because
 * visibility is {@code owner == null || owner.equals(current)}. Dropping the
 * field at serialization time makes every bundle land GLOBAL on a startup/seed
 * import (no tenant bound) and correctly owned on a tenant-bound import (the
 * scope stamps the importer).
 * <p>
 * This is a <strong>serialization</strong> mixin on purpose. Both export
 * producers put <em>live JPA entities</em> into the {@code TurExchange}
 * envelope, so clearing the field with a setter would let Hibernate's dirty
 * checking flush the null back to the database and un-own the source install's
 * own infra.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public abstract class TurTenantBlindInfraMixin {

    @JsonIgnore
    public abstract String getTenantId();
}
