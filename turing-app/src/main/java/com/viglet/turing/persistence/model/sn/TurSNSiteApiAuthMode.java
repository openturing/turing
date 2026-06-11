/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.sn;

/**
 * T233 / §VII.6.h — controls whether the visitor-facing Semantic Navigation
 * API of a site (search / autocomplete / query / click / chat / spell-check)
 * is open to anyone or requires a Turing-registered API key (a Dev Token sent
 * in the {@code Key} header or {@code apiKey} query parameter).
 *
 * <p>Defaults to {@link #PUBLIC} so existing sites keep their open,
 * unauthenticated behaviour.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum TurSNSiteApiAuthMode {

    /** Open API — no credential required (legacy behaviour). */
    PUBLIC,

    /**
     * The visitor-facing endpoints require a valid Turing-registered API key
     * (Dev Token). Requests without one are rejected with HTTP 401.
     */
    API_KEY
}
