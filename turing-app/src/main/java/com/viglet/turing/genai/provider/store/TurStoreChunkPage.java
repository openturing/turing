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
package com.viglet.turing.genai.provider.store;

import java.util.List;
import java.util.Map;

/**
 * A page of chunks returned by
 * {@link TurGenAiStoreProvider#listChunks(com.viglet.turing.persistence.model.store.TurStoreInstance,
 * String, String, int, int, Map)} — used by the ANN admin UI to browse all
 * indexed content without similarity scoring.
 *
 * @param chunks chunks in this page (empty when {@code page} is past the end).
 * @param total  total chunks across all pages, used to render pagination
 *               controls. {@code -1} when the backend cannot cheaply provide
 *               a total (the caller falls back to "next page available?"
 *               heuristics).
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public record TurStoreChunkPage(List<Chunk> chunks, long total) {

    public record Chunk(String id, String content, Map<String, Object> metadata) {
    }
}
