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
package com.viglet.turing.sn.kb;

import java.util.List;

/**
 * T671 / §XL (Block AQ) — a canonical term resolved from a document surface form,
 * carrying the pre-computed root→term ancestor {@code path} (labels, inclusive of
 * the term itself). The path is computed once at dictionary-compile time (walking
 * {@code parentTermId}) so the T672 index-time enricher only reads it — expansion
 * writes exactly this list to the {@code microthesaurus_terms} field.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurRecognizedTerm(String termId, String label, List<String> path) {
}
