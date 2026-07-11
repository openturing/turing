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
package com.viglet.turing.kb;

import com.viglet.turing.persistence.dto.kb.TurThesaurusDraft;
import com.viglet.turing.persistence.dto.kb.TurThesaurusGenerationRequest;

/**
 * T675 / §XL (Block AQ) — the seam that turns a domain+language prompt into a
 * draft microthesaurus hierarchy. The default implementation
 * ({@code TurLlmThesaurusHierarchyGenerator}) drives the default LLM through the
 * T426 strict-schema structured-output path; alternative implementations (a
 * canned generator in tests, a template-based one for air-gapped installs) plug
 * in without touching {@link TurThesaurusGenerationService}. This is the same
 * "pluggable LLM deriver behind a seam so CI never needs a real model" shape as
 * the T387 manifest deriver.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurThesaurusHierarchyGenerator {

    /**
     * Drafts a microthesaurus tree for the request. The result is a
     * <em>proposal</em> — never persisted here.
     *
     * @throws IllegalStateException when no capable generator is available (e.g.
     *         the default LLM is not a provider that supports structured output).
     */
    TurThesaurusDraft generate(TurThesaurusGenerationRequest request);
}
