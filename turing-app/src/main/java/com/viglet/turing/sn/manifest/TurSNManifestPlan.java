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

package com.viglet.turing.sn.manifest;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.viglet.core.manifest.VigletManifestDiff;

/**
 * What {@code POST /api/sn/manifest/plan} answers (T1084): the neutral
 * {@link VigletManifestDiff}, flattened into the same JSON object it always
 * was, plus the custom facets provisioning would create or change.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNManifestPlan(@JsonUnwrapped VigletManifestDiff diff,
        List<String> customFacetsToChange) {
}
