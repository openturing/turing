/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.dto.agent;

import java.util.List;

/**
 * T236 / §VII.13.g — outcome of the form-label "tidy" pass.
 *
 * <p>The service does NOT persist anything — the tidied fields are returned so
 * the author can review them in the convert-to-form dialog before applying the
 * conversion. {@code fields} mirrors the request order so the client can re-pair
 * each tidied label with its original field by index.
 *
 * @param success true when the LLM produced usable labels.
 * @param error   human-readable error when {@code success} is false; {@code null} otherwise.
 * @param fields  the tidied fields (same order/size as the request) when
 *                {@code success} is true; {@code null} otherwise.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurFormLabelTidyResponse(
        boolean success,
        String error,
        List<TurFormLabelTidyField> fields) {
}
