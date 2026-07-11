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
package com.viglet.turing.persistence.model.kb;

/**
 * T668 / §XL (Block AQ) — provenance of a {@link TurKnowledgeBase}, so the admin
 * UI can distinguish a shipped seed library from customer-authored or imported
 * content.
 *
 * <ul>
 *   <li>{@link #SYSTEM_SEED} — shipped as an importable seed bundle (T674).</li>
 *   <li>{@link #AI_GENERATED} — produced by LLM-assisted generation (T675).</li>
 *   <li>{@link #XML_IMPORT} — imported from an external thesaurus authority file (T673).</li>
 *   <li>{@link #USER} — authored in the console/bento CRUD (default).</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurKnowledgeBaseSource {
    SYSTEM_SEED,
    AI_GENERATED,
    XML_IMPORT,
    USER
}
