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

/**
 * Outcome summary of a JSONL import into a vector store collection. Used by
 * the import endpoint to render a "what happened" panel similar to the SN
 * exchange flow.
 *
 * @param total       number of records read from the input file (lines that
 *                    could be parsed as JSON)
 * @param imported    records successfully upserted into the collection — sum of
 *                    {@code preserved + reEmbedded}, kept separately so the UI
 *                    does not have to add up
 * @param preserved   records whose embedding was kept verbatim from the JSONL
 * @param reEmbedded  records whose embedding was missing/empty and was
 *                    re-computed from {@code content} via the configured
 *                    embedding model
 * @param failed      records the provider could not upsert (logged warnings,
 *                    typically due to malformed embedding length, transport
 *                    errors, or invalid metadata)
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public record TurStoreImportResult(int total, int imported, int preserved, int reEmbedded, int failed) {
}
