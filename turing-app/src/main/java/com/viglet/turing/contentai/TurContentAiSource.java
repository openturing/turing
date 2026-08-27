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
package com.viglet.turing.contentai;

import java.util.List;
import java.util.Set;

/**
 * T1045 / §XX.22 — one content source as {@code GET /content-sources} describes
 * it, reduced to what a Turing configuration can be checked against.
 *
 * <p>The full response carries a per-space description, timestamps and an ETag.
 * None of that answers a question an operator has, so none of it is kept: what
 * this holds is the source's identity, its type, the space names a query may
 * select, and which fields the index will actually filter or full-text match on.
 * Those are exactly the four things {@code turing.content-ai.*} can name wrongly.
 *
 * @param name          the source name, which is what a search request addresses
 * @param type          {@code ACQUISITION}, {@code AEM_AUTHOR}, {@code AEM_PUBLISH}
 *                      or {@code CUSTOM}
 * @param vectorSpaces  vector space names declared by this source's IndexConfig
 * @param lexicalSpaces lexical space names declared by this source's IndexConfig
 * @param filterFields  fields the index will filter on, i.e. the fields a Turing
 *                      facet can be built from
 * @param fulltextFields fields the index will full-text match on
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurContentAiSource(
        String name,
        String type,
        List<String> vectorSpaces,
        List<String> lexicalSpaces,
        Set<String> filterFields,
        Set<String> fulltextFields) {
}
