/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.system;

import java.util.Map;
import java.util.Optional;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * T829 / §LXI.5 (Block BN) — the seam through which {@link TurLlmSummaryService}
 * asks a provider for a <strong>schema-bound</strong> completion.
 *
 * <p>It exists so the summary service (which owns the cache, the instance
 * resolution and the token accounting) can obtain a provider-enforced JSON reply
 * without depending on the vendor-specific native clients directly. The
 * implementation lives in {@code genai.structuredoutputs} and routes to OpenAI's
 * {@code json_schema} or Anthropic's forced-tool input; anything else yields
 * {@link Optional#empty()} so the caller falls back to a plain chat call with the
 * converter's format instruction.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurStructuredCompletionPort {

    /**
     * One schema-bound completion.
     *
     * @param instance   the LLM instance already resolved by the caller
     * @param schemaName a stable name for the schema (providers surface it in logs
     *                   and, on Anthropic, as the forced tool's name)
     * @param schema     the JSON Schema of the expected object
     * @return the raw JSON reply, or empty when this provider has no structured
     *         surface (or the call failed — the port never throws)
     */
    Optional<String> complete(TurLLMInstance instance, String systemPrompt, String userInput,
            String schemaName, Map<String, Object> schema);
}
