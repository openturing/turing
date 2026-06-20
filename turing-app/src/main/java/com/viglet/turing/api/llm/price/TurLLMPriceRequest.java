/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.llm.price;

/**
 * Request body for upserting a per-model price row. Mirrors the writable fields
 * of {@code TurLLMPrice} (the {@code id} is server-generated and
 * {@code updatedAt} is stamped on save) so the persistent entity is never bound
 * directly from the HTTP request. The JSON field names match the entity's, so
 * the admin client contract is unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.5
 */
public record TurLLMPriceRequest(
        String vendorId,
        String modelName,
        double inputPricePerMillion,
        double outputPricePerMillion,
        String currency) {
}
